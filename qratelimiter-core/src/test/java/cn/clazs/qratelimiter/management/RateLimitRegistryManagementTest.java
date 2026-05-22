package cn.clazs.qratelimiter.management;

import cn.clazs.qratelimiter.core.LimiterExecutor;
import cn.clazs.qratelimiter.core.RateLimiter;
import cn.clazs.qratelimiter.core.RateLimiterConfig;
import cn.clazs.qratelimiter.core.RateLimiterOptions;
import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;
import cn.clazs.qratelimiter.factory.LimiterExecutorFactory;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Rate limiter management model")
class RateLimitRegistryManagementTest {

    @Test
    @DisplayName("snapshot reports registry and per-limiter request statistics")
    void snapshotReportsRegistryAndPerLimiterRequestStatistics() {
        RateLimitRegistry registry = newRegistry(2, 60_000L, 3);
        RateLimiter userLimiter = registry.getLimiter("user:1001");

        assertTrue(userLimiter.allowRequest("user:1001", 2, 60_000L, 3));
        assertTrue(userLimiter.allowRequest("user:1001", 2, 60_000L, 3));
        assertFalse(userLimiter.allowRequest("user:1001", 2, 60_000L, 3));

        RateLimitSnapshot snapshot = registry.snapshot();

        assertEquals(1L, snapshot.getCurrentCacheSize());
        assertEquals(1L, snapshot.getTotalCreatedLimiters());
        assertEquals(2L, snapshot.getAllowedRequests());
        assertEquals(1L, snapshot.getRejectedRequests());
        assertEquals(1, snapshot.getLimiters().size());

        RateLimitSnapshot.LimiterSnapshot limiterSnapshot = snapshot.getLimiters().get(0);
        assertEquals("user:1001", limiterSnapshot.getKey());
        assertEquals("sliding-window-log", limiterSnapshot.getAlgorithm());
        assertEquals("local", limiterSnapshot.getStorage());
        assertEquals(2L, limiterSnapshot.getAllowedRequests());
        assertEquals(1L, limiterSnapshot.getRejectedRequests());
        assertEquals(2, limiterSnapshot.getCurrentCount());
    }

    @Test
    @DisplayName("removeLimiter resets executor state as well as registry cache")
    void removeLimiterResetsExecutorStateAsWellAsRegistryCache() {
        RateLimitRegistry registry = newRegistry(1, 60_000L, 2);
        RateLimiter limiter = registry.getLimiter("reset:user");

        assertTrue(limiter.allowRequest("reset:user", 1, 60_000L, 2));
        assertFalse(limiter.allowRequest("reset:user", 1, 60_000L, 2));

        registry.removeLimiter("reset:user");

        RateLimiter newLimiter = registry.getLimiter("reset:user");
        assertNotNull(newLimiter);
        assertTrue(newLimiter.allowRequest("reset:user", 1, 60_000L, 2));
    }

    @Test
    @DisplayName("clearAll resets all executor state")
    void clearAllResetsAllExecutorState() {
        RateLimitRegistry registry = newRegistry(1, 60_000L, 2);
        RateLimiter first = registry.getLimiter("reset:first");
        RateLimiter second = registry.getLimiter("reset:second");

        assertTrue(first.allowRequest("reset:first", 1, 60_000L, 2));
        assertFalse(first.allowRequest("reset:first", 1, 60_000L, 2));
        assertTrue(second.allowRequest("reset:second", 1, 60_000L, 2));
        assertFalse(second.allowRequest("reset:second", 1, 60_000L, 2));

        registry.clearAll();

        assertTrue(registry.getLimiter("reset:first").allowRequest("reset:first", 1, 60_000L, 2));
        assertTrue(registry.getLimiter("reset:second").allowRequest("reset:second", 1, 60_000L, 2));
    }

    @Test
    @DisplayName("clear operations tolerate custom executors without reset support")
    void clearOperationsTolerateCustomExecutorsWithoutResetSupport() {
        LimiterExecutorFactory factory = new LimiterExecutorFactory();
        factory.registerProvider(RateLimitAlgorithm.SLIDING_WINDOW_LOG, RateLimitStorage.LOCAL,
                NoResetExecutor::new);
        RateLimiterOptions options = RateLimiterOptions.builder()
                .freq(1)
                .interval(60_000L)
                .capacity(2)
                .cacheExpireAfterAccessMinutes(10L)
                .cacheMaximumSize(100L)
                .build();
        RateLimitRegistry registry = new RateLimitRegistry(options, factory);

        registry.getLimiter("custom:no-reset");

        assertDoesNotThrow(() -> registry.removeLimiter("custom:no-reset"));
        registry.getLimiter("custom:no-reset-again");
        assertDoesNotThrow(registry::clearAll);
    }

    @Test
    @DisplayName("snapshot keeps aggregate request statistics after cache is cleared")
    void snapshotKeepsAggregateRequestStatisticsAfterCacheIsCleared() {
        RateLimitRegistry registry = newRegistry(1, 60_000L, 2);
        RateLimiter limiter = registry.getLimiter("aggregate:user");

        assertTrue(limiter.allowRequest("aggregate:user", 1, 60_000L, 2));
        assertFalse(limiter.allowRequest("aggregate:user", 1, 60_000L, 2));

        registry.clearAll();

        RateLimitSnapshot snapshot = registry.snapshot();
        assertEquals(0L, snapshot.getCurrentCacheSize());
        assertEquals(1L, snapshot.getAllowedRequests());
        assertEquals(1L, snapshot.getRejectedRequests());
        assertEquals(0, snapshot.getLimiters().size());
    }

    @Test
    @DisplayName("refresh can apply new defaults only to new limiters")
    void refreshCanApplyNewDefaultsOnlyToNewLimiters() {
        RateLimitRegistry registry = newRegistry(1, 60_000L, 2);
        RateLimiter existing = registry.getLimiter("refresh:existing");

        RateLimiterOptions newOptions = RateLimiterOptions.builder()
                .freq(3)
                .interval(60_000L)
                .capacity(4)
                .cacheExpireAfterAccessMinutes(10L)
                .cacheMaximumSize(100L)
                .build();

        registry.refreshOptions(newOptions, RateLimitRefreshStrategy.APPLY_TO_NEW_LIMITERS_ONLY);

        assertEquals(1, existing.getConfig().getFreq());
        assertEquals(3, registry.getLimiter("refresh:new").getConfig().getFreq());
    }

    @Test
    @DisplayName("refresh can clear cache and apply new defaults immediately")
    void refreshCanClearCacheAndApplyNewDefaultsImmediately() {
        RateLimitRegistry registry = newRegistry(1, 60_000L, 2);
        registry.getLimiter("refresh:clear");

        RateLimiterOptions newOptions = RateLimiterOptions.builder()
                .freq(3)
                .interval(60_000L)
                .capacity(4)
                .cacheExpireAfterAccessMinutes(10L)
                .cacheMaximumSize(100L)
                .build();

        registry.refreshOptions(newOptions, RateLimitRefreshStrategy.CLEAR_CACHE_AND_APPLY);

        assertEquals(0L, registry.getCurrentCacheSize());
        assertEquals(3, registry.getLimiter("refresh:clear").getConfig().getFreq());
    }

    private RateLimitRegistry newRegistry(int freq, long interval, int capacity) {
        RateLimiterOptions options = RateLimiterOptions.builder()
                .freq(freq)
                .interval(interval)
                .capacity(capacity)
                .cacheExpireAfterAccessMinutes(10L)
                .cacheMaximumSize(100L)
                .build();
        return new RateLimitRegistry(options, new LimiterExecutorFactory(options));
    }

    private static class NoResetExecutor implements LimiterExecutor {
        @Override
        public boolean tryAcquire(String key, int freq, long interval, int capacity) {
            RateLimiterConfig.validate(RateLimitAlgorithm.SLIDING_WINDOW_LOG, freq, interval, capacity);
            return true;
        }
    }
}
