package cn.clazs.qratelimiter.core;

import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RateLimiterTemplate core API")
class RateLimiterTemplateTest {

    @Test
    @DisplayName("localDefaults creates a Spring-independent local rate limiter template")
    void localDefaultsCreatesSpringIndependentTemplate() {
        RateLimiterTemplate template = RateLimiterTemplate.localDefaults();

        assertNotNull(template);
        assertTrue(template.tryAcquire("api:search", 2, 1000L, 3));
        assertTrue(template.tryAcquire("api:search", 2, 1000L, 3));
        assertFalse(template.tryAcquire("api:search", 2, 1000L, 3));
    }

    @Test
    @DisplayName("custom options are applied to limiters created through the template")
    void customOptionsAreAppliedToCreatedLimiters() {
        RateLimiterOptions options = RateLimiterOptions.builder()
                .algorithm(RateLimitAlgorithm.TOKEN_BUCKET)
                .storage(RateLimitStorage.LOCAL)
                .freq(1)
                .interval(60_000L)
                .capacity(2)
                .cacheExpireAfterAccessMinutes(2L)
                .cacheMaximumSize(16L)
                .build();

        RateLimiterTemplate template = RateLimiterTemplate.local(options);
        RateLimiter limiter = template.getLimiter("user:42");

        assertEquals(RateLimitAlgorithm.TOKEN_BUCKET, limiter.getAlgorithm());
        assertEquals(RateLimitStorage.LOCAL, limiter.getStorage());
        assertEquals(1, limiter.getConfig().getFreq());
        assertEquals(60_000L, limiter.getConfig().getInterval());
        assertEquals(2, limiter.getConfig().getCapacity());
    }

    @Test
    @DisplayName("local template does not mutate the caller's options")
    void localTemplateDoesNotMutateCallerOptions() {
        RateLimiterOptions options = RateLimiterOptions.builder()
                .algorithm(RateLimitAlgorithm.SLIDING_WINDOW_LOG)
                .storage(RateLimitStorage.REDIS)
                .freq(2)
                .interval(1000L)
                .capacity(3)
                .build();

        RateLimiterTemplate.local(options);

        assertEquals(RateLimitStorage.REDIS, options.getStorage());
    }

    @Test
    @DisplayName("same key reuses the same cached limiter instance")
    void sameKeyReusesCachedLimiter() {
        RateLimiterTemplate template = RateLimiterTemplate.localDefaults();

        assertSame(template.getLimiter("user:1001"), template.getLimiter("user:1001"));
    }

    @Test
    @DisplayName("all local algorithms can reject after a short burst")
    void allLocalAlgorithmsRejectAfterShortBurst() {
        for (RateLimitAlgorithm algorithm : RateLimitAlgorithm.values()) {
            RateLimiterOptions options = RateLimiterOptions.builder()
                    .algorithm(algorithm)
                    .storage(RateLimitStorage.LOCAL)
                    .freq(2)
                    .interval(60000L)
                    .capacity(algorithm == RateLimitAlgorithm.SLIDING_WINDOW_COUNTER ? 10 : 2)
                    .build();
            RateLimiterTemplate template = RateLimiterTemplate.local(options);
            String key = "algorithm:" + algorithm.getCode();

            assertTrue(template.tryAcquire(key), algorithm + " should allow first request");
            assertTrue(template.tryAcquire(key), algorithm + " should allow second request");
            assertFalse(template.tryAcquire(key), algorithm + " should reject third request");
        }
    }

    @Test
    @DisplayName("invalid keys are rejected before creating limiters")
    void invalidKeysAreRejected() {
        RateLimiterTemplate template = RateLimiterTemplate.localDefaults();

        assertThrows(IllegalArgumentException.class, () -> template.tryAcquire(null));
        assertThrows(IllegalArgumentException.class, () -> template.tryAcquire(""));
        assertThrows(IllegalArgumentException.class, () -> template.tryAcquire("   "));
    }
}
