package cn.clazs.qratelimiter.stress;

import cn.clazs.qratelimiter.core.RateLimiterOptions;
import cn.clazs.qratelimiter.core.RateLimiterTemplate;
import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Optional local stress verification")
class LocalStressVerificationTest {

    @Test
    @DisplayName("sliding window log never allows more than configured frequency under concurrency")
    void slidingWindowLogNeverAllowsMoreThanConfiguredFrequencyUnderConcurrency() throws Exception {
        int frequency = 8;
        int attempts = 64;
        RateLimiterTemplate template = RateLimiterTemplate.local(optionsFor(RateLimitAlgorithm.SLIDING_WINDOW_LOG,
                frequency, frequency));
        String key = "stress:log:" + UUID.randomUUID();

        int allowed = runConcurrentAttempts(template, key, attempts);

        assertEquals(frequency, allowed);
    }

    @Test
    @DisplayName("token bucket never allows more than configured capacity under concurrency")
    void tokenBucketNeverAllowsMoreThanConfiguredCapacityUnderConcurrency() throws Exception {
        int capacity = 10;
        int attempts = 80;
        RateLimiterTemplate template = RateLimiterTemplate.local(optionsFor(RateLimitAlgorithm.TOKEN_BUCKET,
                1, capacity));
        String key = "stress:token:" + UUID.randomUUID();

        int allowed = runConcurrentAttempts(template, key, attempts);

        assertEquals(capacity, allowed);
    }

    @Test
    @DisplayName("different keys keep independent quotas under concurrency")
    void differentKeysKeepIndependentQuotasUnderConcurrency() throws Exception {
        int frequency = 4;
        RateLimiterTemplate template = RateLimiterTemplate.local(optionsFor(RateLimitAlgorithm.SLIDING_WINDOW_LOG,
                frequency, frequency));

        int firstAllowed = runConcurrentAttempts(template, "stress:key:a:" + UUID.randomUUID(), 32);
        int secondAllowed = runConcurrentAttempts(template, "stress:key:b:" + UUID.randomUUID(), 32);

        assertEquals(frequency, firstAllowed);
        assertEquals(frequency, secondAllowed);
    }

    private RateLimiterOptions optionsFor(RateLimitAlgorithm algorithm, int frequency, int capacity) {
        return RateLimiterOptions.builder()
                .algorithm(algorithm)
                .storage(RateLimitStorage.LOCAL)
                .freq(frequency)
                .interval(60000L)
                .capacity(capacity)
                .cacheExpireAfterAccessMinutes(1L)
                .cacheMaximumSize(1024L)
                .build();
    }

    private int runConcurrentAttempts(RateLimiterTemplate template, String key, int attempts) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();

        try {
            for (int i = 0; i < attempts; i++) {
                futures.add(executorService.submit(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        ready.countDown();
                        assertTrue(start.await(2, TimeUnit.SECONDS));
                        return template.tryAcquire(key);
                    }
                }));
            }

            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();

            int allowed = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(2, TimeUnit.SECONDS)) {
                    allowed++;
                }
            }
            return allowed;
        } finally {
            executorService.shutdownNow();
            assertTrue(executorService.awaitTermination(2, TimeUnit.SECONDS));
        }
    }
}
