package cn.clazs.qratelimiter.core;

import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;

import java.util.Objects;

/**
 * Global options for a {@link cn.clazs.qratelimiter.registry.RateLimitRegistry}.
 */
public class RateLimiterOptions {

    private int freq = 100;
    private long interval = 60000L;
    private int capacity = 150;
    private long cacheExpireAfterAccessMinutes = 1440L;
    private long cacheMaximumSize = 10000L;
    private RateLimitAlgorithm algorithm = RateLimitAlgorithm.SLIDING_WINDOW_LOG;
    private RateLimitStorage storage = RateLimitStorage.LOCAL;

    public static RateLimiterOptions defaults() {
        return new RateLimiterOptions();
    }

    public static RateLimiterOptions copyOf(RateLimiterOptions source) {
        RateLimiterOptions copy = new RateLimiterOptions();
        if (source == null) {
            return copy;
        }
        copy.freq = source.freq;
        copy.interval = source.interval;
        copy.capacity = source.capacity;
        copy.cacheExpireAfterAccessMinutes = source.cacheExpireAfterAccessMinutes;
        copy.cacheMaximumSize = source.cacheMaximumSize;
        copy.algorithm = source.algorithm;
        copy.storage = source.storage;
        return copy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getFreq() {
        return freq;
    }

    public void setFreq(int freq) {
        this.freq = freq;
    }

    public long getInterval() {
        return interval;
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public long getCacheExpireAfterAccessMinutes() {
        return cacheExpireAfterAccessMinutes;
    }

    public void setCacheExpireAfterAccessMinutes(long cacheExpireAfterAccessMinutes) {
        this.cacheExpireAfterAccessMinutes = cacheExpireAfterAccessMinutes;
    }

    public long getCacheMaximumSize() {
        return cacheMaximumSize;
    }

    public void setCacheMaximumSize(long cacheMaximumSize) {
        this.cacheMaximumSize = cacheMaximumSize;
    }

    public RateLimitAlgorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(RateLimitAlgorithm algorithm) {
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm cannot be null");
    }

    public RateLimitStorage getStorage() {
        return storage;
    }

    public void setStorage(RateLimitStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
    }

    public void validate() {
        RateLimiterConfig.validate(algorithm, freq, interval, capacity);
        if (cacheExpireAfterAccessMinutes <= 0) {
            throw new IllegalArgumentException("cacheExpireAfterAccessMinutes must be > 0");
        }
        if (cacheMaximumSize <= 0) {
            throw new IllegalArgumentException("cacheMaximumSize must be > 0");
        }
    }

    public String getSummary() {
        return String.format(
                "RateLimiterOptions{freq=%d, interval=%dms, capacity=%d, "
                        + "algorithm=%s, storage=%s, cacheExpireAfterAccessMinutes=%d, cacheMaximumSize=%d}",
                freq, interval, capacity, algorithm, storage,
                cacheExpireAfterAccessMinutes, cacheMaximumSize
        );
    }

    public static class Builder {
        private final RateLimiterOptions options = new RateLimiterOptions();

        public Builder freq(int freq) {
            options.freq = freq;
            return this;
        }

        public Builder interval(long interval) {
            options.interval = interval;
            return this;
        }

        public Builder capacity(int capacity) {
            options.capacity = capacity;
            return this;
        }

        public Builder cacheExpireAfterAccessMinutes(long minutes) {
            options.cacheExpireAfterAccessMinutes = minutes;
            return this;
        }

        public Builder cacheMaximumSize(long maximumSize) {
            options.cacheMaximumSize = maximumSize;
            return this;
        }

        public Builder algorithm(RateLimitAlgorithm algorithm) {
            options.setAlgorithm(algorithm);
            return this;
        }

        public Builder storage(RateLimitStorage storage) {
            options.setStorage(storage);
            return this;
        }

        public RateLimiterOptions build() {
            options.validate();
            return options;
        }
    }
}
