package cn.clazs.qratelimiter.factory;

import cn.clazs.qratelimiter.core.LimiterExecutor;
import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;
import cn.clazs.qratelimiter.executor.local.LocalLeakyBucketExecutor;
import cn.clazs.qratelimiter.executor.local.LocalSlidingWindowCounterExecutor;
import cn.clazs.qratelimiter.executor.local.LocalSlidingWindowLogExecutor;
import cn.clazs.qratelimiter.executor.local.LocalTokenBucketExecutor;
import cn.clazs.qratelimiter.properties.RateLimiterProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流执行器工厂
 *
 * <p>根据算法类型和存储类型创建对应的执行器实例
 * <p>支持执行器缓存，避免重复创建
 *
 * @author clazs
 * @since 1.0.0
 */
@Slf4j
public class LimiterExecutorFactory {

    /**
     * 执行器缓存：key = "algorithm:storage"，value = LimiterExecutor
     */
    private final Map<String, LimiterExecutor> executorCache = new ConcurrentHashMap<>();

    /**
     * Redis 模板（可选，仅在 storage=REDIS 时使用）
     */
    private Object redisTemplate;

    /**
     * Redis 键前缀配置
     */
    private final String redisKeyPrefix;

    /**
     * 默认构造函数
     */
    public LimiterExecutorFactory() {
        this("qratelimiter:");
    }

    /**
     * 构造函数（通过配置初始化）
     *
     * @param properties 配置属性
     */
    public LimiterExecutorFactory(RateLimiterProperties properties) {
        this(properties != null && properties.getRedis() != null
                ? properties.getRedis().getKeyPrefix()
                : "qratelimiter:");
    }

    /**
     * 私有构造函数（设置key前缀）
     */
    private LimiterExecutorFactory(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }

    /**
     * 设置 Redis 模板（由 Spring 自动调用）
     *
     * @param redisTemplate Redis 模板
     */
    public void setRedisTemplate(Object redisTemplate) {
        this.redisTemplate = redisTemplate;
        log.debug("Redis 模板已注入: {}", redisTemplate != null ? redisTemplate.getClass().getName() : "null");
    }

    /**
     * 获取执行器实例（带缓存）
     *
     * @param algorithm 算法类型
     * @param storage 存储类型
     * @return 执行器实例
     */
    public LimiterExecutor getExecutor(RateLimitAlgorithm algorithm, RateLimitStorage storage) {
        Objects.requireNonNull(algorithm, "algorithm cannot be null");
        Objects.requireNonNull(storage, "storage cannot be null");

        String cacheKey = buildCacheKey(algorithm, storage);

        return executorCache.computeIfAbsent(cacheKey, k -> {
            log.info("创建限流执行器: algorithm={}, storage={}", algorithm, storage);
            return doCreateExecutor(algorithm, storage);
        });
    }

    /**
     * 实际创建执行器
     *
     * @param algorithm 算法类型
     * @param storage 存储类型
     * @return 执行器实例
     */
    private LimiterExecutor doCreateExecutor(RateLimitAlgorithm algorithm, RateLimitStorage storage) {
        switch (storage) {
            case LOCAL:
                return createLocalExecutor(algorithm);

            case REDIS:
                return createRedisExecutor(algorithm);

            default:
                throw new UnsupportedOperationException("Unsupported storage: " + storage);
        }
    }

    /**
     * 创建本地执行器
     *
     * @param algorithm 算法类型
     * @return 本地执行器实例
     */
    private LimiterExecutor createLocalExecutor(RateLimitAlgorithm algorithm) {
        switch (algorithm) {
            case SLIDING_WINDOW_LOG:
                return new LocalSlidingWindowLogExecutor();

            case SLIDING_WINDOW_COUNTER:
                return new LocalSlidingWindowCounterExecutor();

            case TOKEN_BUCKET:
                return new LocalTokenBucketExecutor();

            case LEAKY_BUCKET:
                return new LocalLeakyBucketExecutor();

            default:
                throw new UnsupportedOperationException("Unsupported algorithm: " + algorithm);
        }
    }

    /**
     * 创建 Redis 执行器
     *
     * @param algorithm 算法类型
     * @return Redis 执行器实例
     */
    private LimiterExecutor createRedisExecutor(RateLimitAlgorithm algorithm) {
        if (redisTemplate == null) {
            throw new IllegalStateException(
                    "Redis storage is not available. Please add spring-boot-starter-data-redis dependency."
            );
        }

        // 使用反射创建 Redis 执行器，避免硬依赖
        try {
            Class<?> redisTemplateClass = Class.forName("org.springframework.data.redis.core.StringRedisTemplate");
            Class<?> slidingWindowLogExecutorClass = Class.forName("cn.clazs.qratelimiter.executor.redis.RedisSlidingWindowLogExecutor");

            switch (algorithm) {
                case SLIDING_WINDOW_LOG:
                    return (LimiterExecutor) slidingWindowLogExecutorClass
                            .getConstructor(redisTemplateClass, String.class)
                            .newInstance(redisTemplate, redisKeyPrefix);

                case SLIDING_WINDOW_COUNTER:
                    return (LimiterExecutor) Class.forName("cn.clazs.qratelimiter.executor.redis.RedisSlidingWindowCounterExecutor")
                            .getDeclaredConstructor().newInstance();

                case TOKEN_BUCKET:
                    return (LimiterExecutor) Class.forName("cn.clazs.qratelimiter.executor.redis.RedisTokenBucketExecutor")
                            .getDeclaredConstructor().newInstance();

                case LEAKY_BUCKET:
                    return (LimiterExecutor) Class.forName("cn.clazs.qratelimiter.executor.redis.RedisLeakyBucketExecutor")
                            .getDeclaredConstructor().newInstance();

                default:
                    throw new UnsupportedOperationException("Unsupported algorithm: " + algorithm);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Redis executor", e);
        }
    }

    /**
     * 构建缓存键
     *
     * @param algorithm 算法类型
     * @param storage 存储类型
     * @return 缓存键
     */
    private String buildCacheKey(RateLimitAlgorithm algorithm, RateLimitStorage storage) {
        return algorithm.getCode() + ":" + storage.getCode();
    }

    /**
     * 清空执行器缓存
     * <p>用于动态配置刷新场景
     */
    public void clearCache() {
        log.info("清空执行器缓存");
        executorCache.clear();
    }

    /**
     * 获取缓存的执行器数量
     *
     * @return 缓存大小
     */
    public int getCacheSize() {
        return executorCache.size();
    }
}
