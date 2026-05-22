package cn.clazs.qratelimiter.core;

import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;
import cn.clazs.qratelimiter.management.RateLimitStats;

/**
 * 默认限流器实现。
 *
 * @author clazs
 * @since 1.0.0
 */
public class DefaultRateLimiter implements RateLimiter {

    private final LimiterExecutor executor;
    private final RateLimiterConfig config;
    private final String key;
    private final RateLimitStats stats = new RateLimitStats();
    private final RateLimitStats aggregateStats;

    public DefaultRateLimiter(LimiterExecutor executor, String key, RateLimiterConfig config) {
        this(executor, key, config, null);
    }

    public DefaultRateLimiter(LimiterExecutor executor,
                              String key,
                              RateLimiterConfig config,
                              RateLimitStats aggregateStats) {
        if (executor == null) {
            throw new IllegalArgumentException("executor cannot be null");
        }
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key cannot be blank");
        }
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        this.executor = executor;
        this.key = key;
        this.config = config;
        this.aggregateStats = aggregateStats;
    }

    @Override
    public boolean allowRequest(String key, int freq, long interval, int capacity) {
        boolean allowed = executor.tryAcquire(key, freq, interval, capacity);
        stats.record(allowed);
        if (aggregateStats != null) {
            aggregateStats.record(allowed);
        }
        return allowed;
    }

    @Override
    public RateLimitAlgorithm getAlgorithm() {
        return config.getAlgorithm();
    }

    @Override
    public RateLimitStorage getStorage() {
        return config.getStorage();
    }

    @Override
    public RateLimiterConfig getConfig() {
        return config;
    }

    public String getKey() {
        return key;
    }

    public RateLimitStats getStats() {
        return stats;
    }

    public int getCurrentCount() {
        try {
            return executor.getCurrentCount(key);
        } catch (UnsupportedOperationException e) {
            return -1;
        }
    }

    public void resetState() {
        executor.reset(key);
    }
}
