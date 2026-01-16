package cn.clazs.qratelimiter.registry;

import cn.clazs.qratelimiter.properties.RateLimiterProperties;
import cn.clazs.qratelimiter.value.UserLimiter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流器注册中心
 * 使用 Caffeine 缓存管理所有用户的限流器实例，自动清理不活跃用户
 *
 * <p>核心功能：
 * <ul>
 *     <li>管理 Map<UserId, UserLimiter></li>
 *     <li>自动清理不活跃用户（防止内存泄漏）</li>
 *     <li>线程安全的 Limiter 创建</li>
 *     <li>支持从配置文件读取参数</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * // 创建注册中心
 * RateLimiterProperties properties = new RateLimiterProperties();
 * properties.setFreq(100);
 * properties.setInterval(60000L);
 * properties.setCapacity(150);
 * RateLimitRegistry registry = new RateLimitRegistry(properties);
 *
 * // 获取用户的限流器
 * UserLimiter limiter = registry.getLimiter("user123");
 * if (limiter.allowRequest()) {
 *     // 处理请求
 * }
 * </pre>
 *
 * @author clazs
 * @since 1.0
 */
@Slf4j
public class RateLimitRegistry {

    /**
     * Caffeine 缓存：存储 UserId ---> UserLimiter 的映射
     */
    private final Cache<String, UserLimiter> limiterCache;

    /**
     * 全局默认配置
     */
    @Getter
    private final RateLimiterProperties properties;

    /**
     * 统计信息：创建的限流器总数
     */
    private final AtomicLong totalCreatedLimiters = new AtomicLong(0);

    /**
     * 根据配置创建注册中心
     *
     * @param properties 限流器配置
     */
    public RateLimitRegistry(RateLimiterProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("非法配置 -> null");
        }

        // 验证配置
        properties.validate();
        this.properties = properties;

        log.info("初始化 RateLimitRegistry，配置：{}", properties.getSummary());

        // 配置 Caffeine 缓存
        this.limiterCache = Caffeine.newBuilder()
                // 写入后指定时间没有访问，自动删除（防止内存泄漏）
                .expireAfterAccess(properties.getCacheExpireAfterAccessMinutes(), TimeUnit.MINUTES)
                // 最大缓存用户数（防止恶意攻击刷爆内存）
                .maximumSize(properties.getCacheMaximumSize())
                // 启用统计信息
                .recordStats()
                // 移除监听器：记录日志
                .removalListener((key, value, cause) -> {
                    log.debug("限流器被移除：userId={}, 原因={}", key, cause);
                })
                .build();

        log.info("RateLimitRegistry 初始化完成");
    }

    /**
     * 获取指定用户的限流器（保障线程安全）
     * 如果缓存中存在，直接返回；如果不存在，自动创建并放入缓存
     * 使用Caffeine的原子操作保证线程安全
     */
    public UserLimiter getLimiter(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        // Caffeine 的原子操作：如果存在就返回，不存在就创建
        UserLimiter limiter = limiterCache.get(userId, key -> {
            log.debug("创建新的限流器：userId={}", key);

            // 创建新的限流器：从properties取默认值
            UserLimiter newLimiter = new UserLimiter(
                    properties.getCapacity(),
                    properties.getFreq(),
                    properties.getInterval()
            );

            // 统计信息
            totalCreatedLimiters.incrementAndGet();

            return newLimiter;
        });

        return limiter;
    }

    /**
     * 获取或创建限流器（使用自定义配置）
     * 支持方法级别的精细化限流配置，允许覆盖全局配置
     *
     * @param key    限流Key（可以是 userId，也可以是复合Key）
     * @param freq   频率限制（时间窗口内最大请求数）
     * @param interval 时间窗口长度（毫秒）
     * @param capacity 数组容量（必须 >= freq）
     */
    public UserLimiter getLimiter(String key, int freq, long interval, int capacity) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("限流Key不能为空");
        }

        // 参数校验
        if (freq <= 0 || interval <= 0 || capacity <= 0) {
            throw new IllegalArgumentException("限流参数必须大于0: freq=" + freq + ", interval=" + interval + ", capacity=" + capacity);
        }

        if (capacity < freq) {
            throw new IllegalArgumentException("容量不能小于频率: capacity=" + capacity + ", freq=" + freq);
        }

        // Caffeine 的原子操作：如果存在就返回，不存在就创建
        UserLimiter limiter = limiterCache.get(key, cacheKey -> {
            log.debug("创建新的限流器（自定义配置）：key={}, freq={}, interval={}ms, capacity={}",
                    key, freq, interval, capacity);

            // 创建新的限流器（使用自定义配置）
            UserLimiter newLimiter = new UserLimiter(capacity, freq, interval);

            // 统计信息
            totalCreatedLimiters.incrementAndGet();

            return newLimiter;
        });

        return limiter;
    }

    /**
     * 判断指定用户是否已有限流器（不创建新实例）
     *
     * @param userId 用户ID
     * @return 如果存在返回 true，否则返回 false
     */
    public boolean hasLimiter(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        return limiterCache.getIfPresent(userId) != null;
    }

    /**
     * 手动移除指定用户的限流器
     * ps.通常不需要手动调用，Caffeine会自动清理不活跃用户
     *
     * @param userId 用户ID
     */
    public void removeLimiter(String userId) {
        if (userId != null && !userId.trim().isEmpty()) {
            limiterCache.invalidate(userId);
            log.debug("手动移除限流器：userId={}", userId);
        }
    }

    /**
     * 清空所有限流器（慎用）
     */
    public void clearAll() {
        long size = limiterCache.estimatedSize();
        limiterCache.invalidateAll();
        log.info("清空所有限流器，清空前数量：{}", size);
    }

    /**
     * 获取当前缓存的限流器数量（实时估算值）
     *
     * @return 当前缓存大小
     */
    public long getCurrentCacheSize() {
        return limiterCache.estimatedSize();
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息摘要
     */
    public String getStats() {
        return String.format(
                "RateLimitRegistryStats{totalCreated=%d, currentCacheSize=%d, maxSize=%d}",
                totalCreatedLimiters.get(),
                getCurrentCacheSize(),
                properties.getCacheMaximumSize()
        );
    }

    /**
     * 获取已创建的限流器总数
     */
    public long getTotalCreatedLimiters() {
        return totalCreatedLimiters.get();
    }

    /**
     * 获取缓存命中率等高级统计信息（CaffeineAPI）
     */
    public CacheStats getAdvancedStats() {
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = limiterCache.stats();
        return new CacheStats(
                stats.requestCount(),
                stats.hitCount(),
                stats.hitRate(),
                stats.missCount(),
                stats.missRate(),
                stats.totalLoadTime(),
                stats.evictionCount()
        );
    }

    /**
     * 缓存统计信息（相当于一个DTO）
     */
    @Getter
    public static class CacheStats {
        private final long requestCount;      // 总请求数
        private final long hitCount;          // 命中次数
        private final double hitRate;         // 命中率
        private final long missCount;         // 未命中次数
        private final double missRate;        // 未命中率
        private final long totalLoadTime;     // 总加载时间（纳秒）
        private final long evictionCount;     // 驱逐次数

        public CacheStats(long requestCount, long hitCount, double hitRate,
                          long missCount, double missRate, long totalLoadTime, long evictionCount) {
            this.requestCount = requestCount;
            this.hitCount = hitCount;
            this.hitRate = hitRate;
            this.missCount = missCount;
            this.missRate = missRate;
            this.totalLoadTime = totalLoadTime;
            this.evictionCount = evictionCount;
        }

        @Override
        public String toString() {
            return String.format(
                    "CacheStats{requestCount=%d, hitCount=%d, hitRate=%.2f%%, " +
                            "missCount=%d, missRate=%.2f%%, totalLoadTime=%dns, evictionCount=%d}",
                    requestCount, hitCount, hitRate * 100,
                    missCount, missRate * 100, totalLoadTime, evictionCount
            );
        }
    }
}
