package cn.clazs.qratelimiter.core;

import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;

import java.util.Objects;

/**
 * 单个限流器实例的运行配置。
 *
 * @author clazs
 * @since 1.0.0
 */
public class RateLimiterConfig {

    private RateLimitAlgorithm algorithm = RateLimitAlgorithm.SLIDING_WINDOW_LOG;
    private RateLimitStorage storage = RateLimitStorage.LOCAL;
    private int freq = 100;
    private long interval = 60000L;
    private int capacity = 150;

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final RateLimiterConfig config = new RateLimiterConfig();

        public Builder algorithm(RateLimitAlgorithm algorithm) {
            config.setAlgorithm(algorithm);
            return this;
        }

        public Builder storage(RateLimitStorage storage) {
            config.setStorage(storage);
            return this;
        }

        public Builder freq(int freq) {
            config.freq = freq;
            return this;
        }

        public Builder interval(long interval) {
            config.interval = interval;
            return this;
        }

        public Builder capacity(int capacity) {
            config.capacity = capacity;
            return this;
        }

        public RateLimiterConfig build() {
            validate(config.algorithm, config.freq, config.interval, config.capacity);
            return config;
        }
    }

    public static void validate(RateLimitAlgorithm algorithm, int freq, long interval, int capacity) {
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm cannot be null");
        }
        if (freq <= 0) {
            throw new IllegalArgumentException("freq must be > 0");
        }
        if (interval <= 0) {
            throw new IllegalArgumentException("interval must be > 0");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        if (algorithm.requiresCapacityAtLeastFreq() && capacity < freq) {
            throw new IllegalArgumentException("capacity must be >= freq");
        }
    }
}
