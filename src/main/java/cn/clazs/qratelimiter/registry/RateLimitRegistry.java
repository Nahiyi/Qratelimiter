package cn.clazs.qratelimiter.registry;

import cn.clazs.qratelimiter.core.RateLimiter;
import cn.clazs.qratelimiter.core.RateLimiterFactory;
import cn.clazs.qratelimiter.properties.RateLimiterProperties;
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
 *     <li>管理 Map<UserId, RateLimiter></li>
 *     <li>自动清理不活跃用户（防止内存泄漏）</li>
 *     <li>线程安全的 Limiter 创建（委托给 Factory）</li>
 * </ul>
 *
 * @author clazs
 * @since 1.0
 */
@Slf4j
public class RateLimitRegistry {

    /**
     * Caffeine 缓存：存储 UserId ---> RateLimiter 的映射
     */
    private final Cache<String, RateLimiter> limiterCache;

    /**
     * 全局默认配置
     */
    @Getter
    private final RateLimiterProperties properties;

    /**
     * 限流器工厂
     */
    private final RateLimiterFactory rateLimiterFactory;

    /**
     * 统计信息：创建的限流器总数
     */
    private final AtomicLong totalCreatedLimiters = new AtomicLong(0);

    /**
     * 根据配置创建注册中心
     *
     * @param properties 限流器配置
     * @param rateLimiterFactory 限流器工厂
     */
    public RateLimitRegistry(RateLimiterProperties properties, RateLimiterFactory rateLimiterFactory) {
        if (properties == null) {
            throw new IllegalArgumentException("非法配置 -> null");
        }
        if (rateLimiterFactory == null) {
            throw new IllegalArgumentException("限流器工厂不能为空");
        }

        // 验证配置
        properties.validate();
        this.properties = properties;
        this.rateLimiterFactory = rateLimiterFactory;

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
     * 使用yml默认配置获取指定用户的限流器（保障线程安全）
     * 如果缓存中存在，直接返回；如果不存在，自动创建并放入缓存
     */
    public RateLimiter getLimiter(String userId) {
        return getLimiter(userId, properties.getFreq(), properties.getInterval(), properties.getCapacity());
    }

    /**
     * 使用自定义配置获取或创建限流器
     * 支持方法级别的精细化限流配置，允许覆盖全局配置
     * 容量自动计算：若 capacity <= 0，系统会自动计算为 freq + (freq >> 1)
     *
     * @param key    限流Key（可以是 userId，也可以是复合Key）
     * @param freq   频率限制（时间窗口内最大请求数）
     * @param interval 时间窗口长度（毫秒）
     * @param capacity 数组容量（如果 <= 0，则自动计算）
     */
    public RateLimiter getLimiter(String key, int freq, long interval, int capacity) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("限流Key不能为空");
        }

        // 参数校验：freq 和 interval 必须大于 0
        if (freq <= 0 || interval <= 0) {
            throw new IllegalArgumentException("限流参数必须大于0: freq=" + freq + ", interval=" + interval);
        }

        // 若用户未指定 capacity（<= 0），则自动计算
        final int finalCapacity = capacity <= 0 ? freq + (freq >> 1) : capacity;

        if (capacity <= 0) {
            log.debug("自动计算容量：key={}, freq={}, autoCapacity={}", key, freq, finalCapacity);
        }

        // 最终校验：capacity 必须 >= freq
        if (finalCapacity < freq) {
            throw new IllegalArgumentException("容量不能小于频率: capacity=" + finalCapacity + ", freq=" + freq);
        }

        // Caffeine 的原子操作：如果存在就返回，不存在就创建
        RateLimiter limiter = limiterCache.get(key, cacheKey -> {
            log.debug("创建新的限流器（自定义配置）：key={}, freq={}, interval={}ms, capacity={}, mode={}",
                    key, freq, interval, finalCapacity, properties.getStorageType());

            // 统计信息
            totalCreatedLimiters.incrementAndGet();

            // 使用工厂创建实例
            return rateLimiterFactory.create(key, freq, interval, finalCapacity);
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
     */
    public long getCurrentCacheSize() {
        return limiterCache.estimatedSize();
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
     * 缓存统计信息
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
