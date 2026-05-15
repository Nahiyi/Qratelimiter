package cn.clazs.qratelimiter.stress;

import cn.clazs.qratelimiter.core.RateLimiterOptions;
import cn.clazs.qratelimiter.core.RateLimiterTemplate;
import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Core-only stress verification")
class CoreOnlyStressVerificationTest {

    private static final int FREQUENCY = 8;
    private static final int CAPACITY = 10;
    private static final int ATTEMPTS = 80;
    private static final long INTERVAL = 60000L;

    @Test
    @DisplayName("sliding window log respects configured frequency under concurrency")
    void slidingWindowLogRespectsConfiguredFrequencyUnderConcurrency() throws Exception {
        assertConcurrentUpperBound(RateLimitAlgorithm.SLIDING_WINDOW_LOG, FREQUENCY);
    }

    @Test
    @DisplayName("sliding window counter respects configured frequency under concurrency")
    void slidingWindowCounterRespectsConfiguredFrequencyUnderConcurrency() throws Exception {
        assertConcurrentUpperBound(RateLimitAlgorithm.SLIDING_WINDOW_COUNTER, FREQUENCY);
    }

    @Test
    @DisplayName("token bucket respects configured capacity under concurrency")
    void tokenBucketRespectsConfiguredCapacityUnderConcurrency() throws Exception {
        assertConcurrentUpperBound(RateLimitAlgorithm.TOKEN_BUCKET, CAPACITY);
    }

    @Test
    @DisplayName("leaky bucket respects configured capacity under concurrency")
    void leakyBucketRespectsConfiguredCapacityUnderConcurrency() throws Exception {
        assertConcurrentUpperBound(RateLimitAlgorithm.LEAKY_BUCKET, CAPACITY);
    }

    @Test
    @DisplayName("different keys keep independent quotas under concurrency")
    void differentKeysKeepIndependentQuotasUnderConcurrency() throws Exception {
        RateLimiterTemplate template = RateLimiterTemplate.local(optionsFor(RateLimitAlgorithm.SLIDING_WINDOW_LOG));
        String firstKey = "stress:key:a:" + UUID.randomUUID();
        String secondKey = "stress:key:b:" + UUID.randomUUID();

        int firstAllowed = StressTestSupport.runConcurrentAttempts(
                () -> template.tryAcquire(firstKey), ATTEMPTS);
        int secondAllowed = StressTestSupport.runConcurrentAttempts(
                () -> template.tryAcquire(secondKey), ATTEMPTS);

        assertEquals(FREQUENCY, firstAllowed);
        assertEquals(FREQUENCY, secondAllowed);
    }

    private void assertConcurrentUpperBound(RateLimitAlgorithm algorithm, int expectedMaxAllowed) throws Exception {
        RateLimiterTemplate template = RateLimiterTemplate.local(optionsFor(algorithm));
        String key = "stress:core:" + algorithm.getCode() + ":" + UUID.randomUUID();

        int allowed = StressTestSupport.runConcurrentAttempts(() -> template.tryAcquire(key), ATTEMPTS);

        assertEquals(expectedMaxAllowed, allowed, algorithm + " allowed unexpected concurrent requests");
    }

    private RateLimiterOptions optionsFor(RateLimitAlgorithm algorithm) {
        return RateLimiterOptions.builder()
                .algorithm(algorithm)
                .storage(RateLimitStorage.LOCAL)
                .freq(FREQUENCY)
                .interval(INTERVAL)
                .capacity(CAPACITY)
                .cacheExpireAfterAccessMinutes(1L)
                .cacheMaximumSize(1024L)
                .build();
    }
}
