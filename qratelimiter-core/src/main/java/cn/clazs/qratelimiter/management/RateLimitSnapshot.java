package cn.clazs.qratelimiter.management;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable management snapshot of the registry.
 */
public class RateLimitSnapshot {

    private final long currentCacheSize;
    private final long totalCreatedLimiters;
    private final long allowedRequests;
    private final long rejectedRequests;
    private final List<LimiterSnapshot> limiters;

    public RateLimitSnapshot(long currentCacheSize,
                             long totalCreatedLimiters,
                             long allowedRequests,
                             long rejectedRequests,
                             List<LimiterSnapshot> limiters) {
        this.currentCacheSize = currentCacheSize;
        this.totalCreatedLimiters = totalCreatedLimiters;
        this.allowedRequests = allowedRequests;
        this.rejectedRequests = rejectedRequests;
        this.limiters = Collections.unmodifiableList(new ArrayList<>(limiters));
    }

    public long getCurrentCacheSize() {
        return currentCacheSize;
    }

    public long getTotalCreatedLimiters() {
        return totalCreatedLimiters;
    }

    public long getAllowedRequests() {
        return allowedRequests;
    }

    public long getRejectedRequests() {
        return rejectedRequests;
    }

    public List<LimiterSnapshot> getLimiters() {
        return limiters;
    }

    public static class LimiterSnapshot {
        private final String key;
        private final String algorithm;
        private final String storage;
        private final int freq;
        private final long interval;
        private final int capacity;
        private final int currentCount;
        private final long allowedRequests;
        private final long rejectedRequests;

        public LimiterSnapshot(String key,
                               String algorithm,
                               String storage,
                               int freq,
                               long interval,
                               int capacity,
                               int currentCount,
                               long allowedRequests,
                               long rejectedRequests) {
            this.key = key;
            this.algorithm = algorithm;
            this.storage = storage;
            this.freq = freq;
            this.interval = interval;
            this.capacity = capacity;
            this.currentCount = currentCount;
            this.allowedRequests = allowedRequests;
            this.rejectedRequests = rejectedRequests;
        }

        public String getKey() {
            return key;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public String getStorage() {
            return storage;
        }

        public int getFreq() {
            return freq;
        }

        public long getInterval() {
            return interval;
        }

        public int getCapacity() {
            return capacity;
        }

        public int getCurrentCount() {
            return currentCount;
        }

        public long getAllowedRequests() {
            return allowedRequests;
        }

        public long getRejectedRequests() {
            return rejectedRequests;
        }
    }
}
