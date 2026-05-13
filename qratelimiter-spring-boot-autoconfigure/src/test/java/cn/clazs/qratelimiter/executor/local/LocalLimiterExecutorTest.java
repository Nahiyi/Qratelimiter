package cn.clazs.qratelimiter.executor.local;

import cn.clazs.qratelimiter.core.LimiterExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Local 限流执行器测试")
class LocalLimiterExecutorTest {

    @Test
    @DisplayName("滑动窗口日志：窗口内达到阈值后拒绝，重置后恢复")
    void slidingWindowLogRejectsAfterThresholdAndAllowsAfterReset() {
        LimiterExecutor executor = new LocalSlidingWindowLogExecutor();
        String key = "local:log";

        assertTrue(executor.tryAcquire(key, 2, 1000L, 3));
        assertTrue(executor.tryAcquire(key, 2, 1000L, 3));
        assertFalse(executor.tryAcquire(key, 2, 1000L, 3));
        assertEquals(2, executor.getCurrentCount(key));

        executor.reset(key);

        assertEquals(0, executor.getCurrentCount(key));
        assertTrue(executor.tryAcquire(key, 2, 1000L, 3));
    }

    @Test
    @DisplayName("滑动窗口计数器：窗口内达到阈值后拒绝")
    void slidingWindowCounterRejectsAfterThreshold() {
        LimiterExecutor executor = new LocalSlidingWindowCounterExecutor();
        String key = "local:counter";

        assertTrue(executor.tryAcquire(key, 2, 1000L, 10));
        assertTrue(executor.tryAcquire(key, 2, 1000L, 10));
        assertFalse(executor.tryAcquire(key, 2, 1000L, 10));
        assertTrue(executor.getCurrentCount(key) >= 2);
    }

    @Test
    @DisplayName("令牌桶：耗尽令牌后拒绝，等待补充后恢复")
    void tokenBucketRefillsAfterInterval() throws InterruptedException {
        LimiterExecutor executor = new LocalTokenBucketExecutor();
        String key = "local:token";

        assertTrue(executor.tryAcquire(key, 10, 100L, 2));
        assertTrue(executor.tryAcquire(key, 10, 100L, 2));
        assertFalse(executor.tryAcquire(key, 10, 100L, 2));

        Thread.sleep(120L);

        assertTrue(executor.tryAcquire(key, 10, 100L, 2));
    }

    @Test
    @DisplayName("漏桶：满桶后拒绝，等待泄放后恢复")
    void leakyBucketLeaksAfterInterval() throws InterruptedException {
        LimiterExecutor executor = new LocalLeakyBucketExecutor();
        String key = "local:leaky";

        assertTrue(executor.tryAcquire(key, 10, 100L, 2));
        assertTrue(executor.tryAcquire(key, 10, 100L, 2));
        assertFalse(executor.tryAcquire(key, 10, 100L, 2));

        Thread.sleep(120L);

        assertTrue(executor.tryAcquire(key, 10, 100L, 2));
    }
}
