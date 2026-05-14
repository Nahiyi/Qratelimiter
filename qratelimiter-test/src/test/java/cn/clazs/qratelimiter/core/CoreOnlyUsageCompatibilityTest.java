package cn.clazs.qratelimiter.core;

import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;
import cn.clazs.qratelimiter.factory.LimiterExecutorFactory;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Core-only usage compatibility")
class CoreOnlyUsageCompatibilityTest {

    @Test
    @DisplayName("plain Java template usage works without Spring")
    void plainJavaTemplateUsageWorksWithoutSpring() {
        RateLimiterTemplate template = RateLimiterTemplate.local(optionsFor(RateLimitAlgorithm.SLIDING_WINDOW_LOG));
        String key = uniqueKey("template");

        assertTrue(template.tryAcquire(key));
        assertTrue(template.tryAcquire(key));
        assertFalse(template.tryAcquire(key));
    }

    @Test
    @DisplayName("all local algorithms reject after a short burst")
    void allLocalAlgorithmsRejectAfterShortBurst() {
        for (RateLimitAlgorithm algorithm : RateLimitAlgorithm.values()) {
            RateLimiterTemplate template = RateLimiterTemplate.local(optionsFor(algorithm));
            String key = uniqueKey(algorithm.getCode());

            assertEventuallyRejects(template, key, algorithm);
        }
    }

    @Test
    @DisplayName("registry and executor factory can be wired manually")
    void registryAndExecutorFactoryCanBeWiredManually() {
        RateLimiterOptions options = optionsFor(RateLimitAlgorithm.TOKEN_BUCKET);
        LimiterExecutorFactory factory = new LimiterExecutorFactory(options);
        RateLimitRegistry registry = new RateLimitRegistry(options, factory);
        String key = uniqueKey("manual");

        RateLimiter first = registry.getLimiter(key);
        RateLimiter second = registry.getLimiter(key);
        RateLimiter other = registry.getLimiter(uniqueKey("manual-other"));

        assertSame(first, second);
        assertNotSame(first, other);
        assertEquals(RateLimitAlgorithm.TOKEN_BUCKET, first.getAlgorithm());
        assertEquals(RateLimitStorage.LOCAL, first.getStorage());
        assertEquals(2, registry.getTotalCreatedLimiters());
    }

    @Test
    @DisplayName("explicit method-level limits work through template")
    void explicitLimitsWorkThroughTemplate() {
        RateLimiterTemplate template = RateLimiterTemplate.localDefaults();
        String key = uniqueKey("explicit");

        assertTrue(template.tryAcquire(key, 1, 60000L, 1));
        assertFalse(template.tryAcquire(key, 1, 60000L, 1));
    }

    @Test
    @DisplayName("invalid core inputs fail fast")
    void invalidCoreInputsFailFast() {
        RateLimiterTemplate template = RateLimiterTemplate.localDefaults();

        assertThrows(IllegalArgumentException.class, () -> template.tryAcquire(null));
        assertThrows(IllegalArgumentException.class, () -> template.tryAcquire(""));
        assertThrows(IllegalArgumentException.class, () -> template.tryAcquire("   "));
        assertThrows(IllegalArgumentException.class, () -> template.tryAcquire("bad", 0, 1000L, 1));
        assertThrows(IllegalArgumentException.class, () -> template.tryAcquire("bad", 1, 0L, 1));

        RateLimiterOptions invalidOptions = optionsFor(RateLimitAlgorithm.SLIDING_WINDOW_LOG);
        invalidOptions.setCapacity(0);

        assertThrows(IllegalArgumentException.class, () -> RateLimiterTemplate.local(invalidOptions));
        assertThrows(IllegalArgumentException.class, () -> new LimiterExecutorFactory(invalidOptions));
    }

    private RateLimiterOptions optionsFor(RateLimitAlgorithm algorithm) {
        return RateLimiterOptions.builder()
                .algorithm(algorithm)
                .storage(RateLimitStorage.LOCAL)
                .freq(2)
                .interval(60000L)
                .capacity(3)
                .cacheExpireAfterAccessMinutes(1L)
                .cacheMaximumSize(128L)
                .build();
    }

    private void assertEventuallyRejects(RateLimiterTemplate template, String key, RateLimitAlgorithm algorithm) {
        boolean rejected = false;
        for (int i = 0; i < 6 && !rejected; i++) {
            rejected = !template.tryAcquire(key);
        }
        assertTrue(rejected, algorithm + " should reject within a bounded short burst");
    }

    private String uniqueKey(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
