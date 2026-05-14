package cn.clazs.qratelimiter.core;

import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;

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

    public DefaultRateLimiter(LimiterExecutor executor, String key, RateLimiterConfig config) {
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
    }

    @Override
    public boolean allowRequest(String key, int freq, long interval, int capacity) {
        return executor.tryAcquire(key, freq, interval, capacity);
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
}
