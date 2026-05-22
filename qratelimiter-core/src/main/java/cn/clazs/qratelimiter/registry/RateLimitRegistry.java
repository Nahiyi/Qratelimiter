package cn.clazs.qratelimiter.registry;

import cn.clazs.qratelimiter.core.DefaultRateLimiter;
import cn.clazs.qratelimiter.core.LimiterExecutor;
import cn.clazs.qratelimiter.core.RateLimiter;
import cn.clazs.qratelimiter.core.RateLimiterConfig;
import cn.clazs.qratelimiter.core.RateLimiterOptions;
import cn.clazs.qratelimiter.factory.LimiterExecutorFactory;
import cn.clazs.qratelimiter.management.RateLimitRefreshStrategy;
import cn.clazs.qratelimiter.management.RateLimitSnapshot;
import cn.clazs.qratelimiter.management.RateLimitStats;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring-independent limiter registry.
 *
 * @author clazs
 * @since 1.0.0
 */
public class RateLimitRegistry {

    private final Cache<String, RateLimiter> limiterCache;
    private volatile RateLimiterOptions options;
    private final LimiterExecutorFactory executorFactory;
    private final AtomicLong totalCreatedLimiters = new AtomicLong(0);
    private final RateLimitStats aggregateStats = new RateLimitStats();

    public RateLimitRegistry(RateLimiterOptions options, LimiterExecutorFactory executorFactory) {
        if (options == null) {
            throw new IllegalArgumentException("options cannot be null");
        }
        if (executorFactory == null) {
            throw new IllegalArgumentException("executorFactory cannot be null");
        }
        RateLimiterOptions safeOptions = RateLimiterOptions.copyOf(options);
        safeOptions.validate();
        this.options = safeOptions;
        this.executorFactory = executorFactory;
        this.limiterCache = Caffeine.newBuilder()
                .expireAfterAccess(safeOptions.getCacheExpireAfterAccessMinutes(), TimeUnit.MINUTES)
                .maximumSize(safeOptions.getCacheMaximumSize())
                .recordStats()
                .build();
    }

    public RateLimiterOptions getOptions() {
        return RateLimiterOptions.copyOf(options);
    }

    public void refreshOptions(RateLimiterOptions newOptions, RateLimitRefreshStrategy strategy) {
        if (newOptions == null) {
            throw new IllegalArgumentException("newOptions cannot be null");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("strategy cannot be null");
        }

        RateLimiterOptions safeOptions = RateLimiterOptions.copyOf(newOptions);
        safeOptions.validate();
        this.options = safeOptions;
        if (strategy == RateLimitRefreshStrategy.CLEAR_CACHE_AND_APPLY) {
            clearAll();
        }
    }

    public RateLimiter getLimiter(String key) {
        validateKey(key);
        return limiterCache.get(key, this::createDefaultLimiter);
    }

    public RateLimiter getLimiter(String key, int freq, long interval, int capacity) {
        validateKey(key);
        if (freq <= 0 || interval <= 0) {
            throw new IllegalArgumentException("rate limit arguments must be positive");
        }

        final int finalCapacity = capacity <= 0 ? freq + (freq >> 1) : capacity;
        RateLimiterConfig.validate(options.getAlgorithm(), freq, interval, finalCapacity);

        return limiterCache.get(key, cacheKey -> {
            RateLimiterConfig config = RateLimiterConfig.builder()
                    .algorithm(options.getAlgorithm())
                    .storage(options.getStorage())
                    .freq(freq)
                    .interval(interval)
                    .capacity(finalCapacity)
                    .build();
            totalCreatedLimiters.incrementAndGet();
            return createLimiter(cacheKey, config);
        });
    }

    public boolean hasLimiter(String key) {
        return key != null && !key.trim().isEmpty() && limiterCache.getIfPresent(key) != null;
    }

    public void removeLimiter(String key) {
        if (key != null && !key.trim().isEmpty()) {
            RateLimiter limiter = limiterCache.getIfPresent(key);
            resetIfSupported(limiter);
            limiterCache.invalidate(key);
        }
    }

    public void clearAll() {
        for (Map.Entry<String, RateLimiter> entry : limiterCache.asMap().entrySet()) {
            resetIfSupported(entry.getValue());
        }
        limiterCache.invalidateAll();
    }

    public long getCurrentCacheSize() {
        return limiterCache.estimatedSize();
    }

    public long getTotalCreatedLimiters() {
        return totalCreatedLimiters.get();
    }

    public String getStats() {
        return String.format(
                "RateLimitRegistryStats{totalCreated=%d, currentCacheSize=%d, maxSize=%d}",
                totalCreatedLimiters.get(),
                getCurrentCacheSize(),
                options.getCacheMaximumSize()
        );
    }

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

    public RateLimitSnapshot snapshot() {
        List<RateLimitSnapshot.LimiterSnapshot> limiterSnapshots = new ArrayList<>();
        for (Map.Entry<String, RateLimiter> entry : limiterCache.asMap().entrySet()) {
            RateLimiter limiter = entry.getValue();
            if (!(limiter instanceof DefaultRateLimiter)) {
                continue;
            }

            DefaultRateLimiter defaultLimiter = (DefaultRateLimiter) limiter;
            long limiterAllowed = defaultLimiter.getStats().getAllowedRequests();
            long limiterRejected = defaultLimiter.getStats().getRejectedRequests();

            RateLimiterConfig config = defaultLimiter.getConfig();
            limiterSnapshots.add(new RateLimitSnapshot.LimiterSnapshot(
                    defaultLimiter.getKey(),
                    config.getAlgorithm().getCode(),
                    config.getStorage().getCode(),
                    config.getFreq(),
                    config.getInterval(),
                    config.getCapacity(),
                    defaultLimiter.getCurrentCount(),
                    limiterAllowed,
                    limiterRejected
            ));
        }

        limiterSnapshots.sort(Comparator.comparing(RateLimitSnapshot.LimiterSnapshot::getKey));
        return new RateLimitSnapshot(
                getCurrentCacheSize(),
                getTotalCreatedLimiters(),
                aggregateStats.getAllowedRequests(),
                aggregateStats.getRejectedRequests(),
                limiterSnapshots
        );
    }

    private RateLimiter createDefaultLimiter(String key) {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(options.getAlgorithm())
                .storage(options.getStorage())
                .freq(options.getFreq())
                .interval(options.getInterval())
                .capacity(options.getCapacity())
                .build();
        totalCreatedLimiters.incrementAndGet();
        return createLimiter(key, config);
    }

    private RateLimiter createLimiter(String key, RateLimiterConfig config) {
        LimiterExecutor executor = executorFactory.getExecutor(config.getAlgorithm(), config.getStorage());
        return new DefaultRateLimiter(executor, key, config, aggregateStats);
    }

    private void resetIfSupported(RateLimiter limiter) {
        if (limiter instanceof DefaultRateLimiter) {
            try {
                ((DefaultRateLimiter) limiter).resetState();
            } catch (UnsupportedOperationException ignored) {
                // Custom executors may not support runtime reset; cache invalidation is still safe.
            }
        }
    }

    private void validateKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("limit key cannot be blank");
        }
    }

    public static class CacheStats {
        private final long requestCount;
        private final long hitCount;
        private final double hitRate;
        private final long missCount;
        private final double missRate;
        private final long totalLoadTime;
        private final long evictionCount;

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

        public long getRequestCount() {
            return requestCount;
        }

        public long getHitCount() {
            return hitCount;
        }

        public double getHitRate() {
            return hitRate;
        }

        public long getMissCount() {
            return missCount;
        }

        public double getMissRate() {
            return missRate;
        }

        public long getTotalLoadTime() {
            return totalLoadTime;
        }

        public long getEvictionCount() {
            return evictionCount;
        }

        @Override
        public String toString() {
            return String.format(
                    "CacheStats{requestCount=%d, hitCount=%d, hitRate=%.2f%%, "
                            + "missCount=%d, missRate=%.2f%%, totalLoadTime=%dns, evictionCount=%d}",
                    requestCount, hitCount, hitRate * 100,
                    missCount, missRate * 100, totalLoadTime, evictionCount
            );
        }
    }
}
