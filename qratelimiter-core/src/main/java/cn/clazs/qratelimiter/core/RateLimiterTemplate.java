package cn.clazs.qratelimiter.core;

import cn.clazs.qratelimiter.enums.RateLimitStorage;
import cn.clazs.qratelimiter.factory.LimiterExecutorFactory;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;

/**
 * Programmatic core API for Java applications.
 */
public class RateLimiterTemplate {

    private final RateLimitRegistry registry;

    public RateLimiterTemplate(RateLimitRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry cannot be null");
        }
        this.registry = registry;
    }

    public static RateLimiterTemplate localDefaults() {
        return local(RateLimiterOptions.defaults());
    }

    public static RateLimiterTemplate local(RateLimiterOptions options) {
        RateLimiterOptions safeOptions = RateLimiterOptions.copyOf(options);
        safeOptions.setStorage(RateLimitStorage.LOCAL);
        safeOptions.validate();
        LimiterExecutorFactory factory = new LimiterExecutorFactory(safeOptions);
        return new RateLimiterTemplate(new RateLimitRegistry(safeOptions, factory));
    }

    public boolean tryAcquire(String key) {
        RateLimiter limiter = registry.getLimiter(key);
        RateLimiterConfig config = limiter.getConfig();
        return limiter.allowRequest(key, config.getFreq(), config.getInterval(), config.getCapacity());
    }

    public boolean tryAcquire(String key, int freq, long interval, int capacity) {
        RateLimiter limiter = registry.getLimiter(key, freq, interval, capacity);
        int finalCapacity = capacity <= 0 ? freq + (freq >> 1) : capacity;
        return limiter.allowRequest(key, freq, interval, finalCapacity);
    }

    public RateLimiter getLimiter(String key) {
        return registry.getLimiter(key);
    }

    public RateLimitRegistry getRegistry() {
        return registry;
    }
}
