package cn.clazs.qratelimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserLimiter 综合测试类
 * 测试限流器的核心功能、边界条件、参数验证和并发安全性
 */
@DisplayName("UserLimiter 限流器测试")
class UserLimiterTest {

    private UserLimiter limiter;
    private long baseTime;

    @BeforeEach
    void setUp() {
        // 初始化限流器：容量5，频率3次，时间窗口1000ms
        limiter = new UserLimiter(5, 3, 1000L);
        baseTime = System.currentTimeMillis();
    }

    // ==================== 核心功能测试 ====================

    @Test
    @DisplayName("基本功能：未超过频率限制应该允许访问")
    void testBasicAllowRequest() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.allowRequest(baseTime + i * 100),
                    "第" + (i + 1) + "次请求应该被允许");
        }
        assertEquals(3, limiter.getSize());
    }

    @Test
    @DisplayName("限流功能：超过频率限制应该拒绝访问")
    void testRateLimiting() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.allowRequest(baseTime + i * 100));
        }

        assertFalse(limiter.allowRequest(baseTime + 300), "第4次请求应该被限流");
        assertEquals(3, limiter.getSize());
    }

    @Test
    @DisplayName("时间窗口：过期记录应该不影响限流判断")
    void testTimeWindowExpiry() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.allowRequest(baseTime + i * 100));
        }

        long newTime = baseTime + 1500;
        assertTrue(limiter.allowRequest(newTime), "时间窗口过期后应该允许");

        for (int i = 1; i < 3; i++) {
            assertTrue(limiter.allowRequest(newTime + i * 100));
        }

        assertFalse(limiter.allowRequest(newTime + 300));
    }

    // ==================== 参数验证测试 ====================

    @Test
    @DisplayName("参数验证：capacity 必须 >= freq")
    void testCapacityMustBeGreaterOrEqualFreq() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new UserLimiter(2, 5, 1000L)
        );

        assertTrue(ex.getMessage().contains("Capacity must be >= frequency"));
    }

    @Test
    @DisplayName("参数验证：拒绝非法参数")
    void testRejectInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new UserLimiter(0, 5, 1000L));
        assertThrows(IllegalArgumentException.class, () -> new UserLimiter(-1, 5, 1000L));
        assertThrows(IllegalArgumentException.class, () -> new UserLimiter(5, 0, 1000L));
        assertThrows(IllegalArgumentException.class, () -> new UserLimiter(5, -1, 1000L));
        assertThrows(IllegalArgumentException.class, () -> new UserLimiter(5, 3, 0L));
    }

    @Test
    @DisplayName("参数验证：允许合法配置")
    void testAcceptValidParameters() {
        assertDoesNotThrow(() -> new UserLimiter(1, 1, 1L));
        assertDoesNotThrow(() -> new UserLimiter(5, 5, 1000L));
        assertDoesNotThrow(() -> new UserLimiter(10, 5, 1000L));
        assertDoesNotThrow(() -> new UserLimiter(100, 50, 60000L));
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("边界条件：空数组状态")
    void testEmptyArray() {
        assertEquals(0, limiter.getSize());
        assertEquals(0, limiter.getOldestTimestamp());
        assertEquals(0, limiter.getLatestTimestamp());
        assertTrue(limiter.isEmpty());
    }

    @Test
    @DisplayName("边界条件：极限频率")
    void testBoundaryFrequency() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.allowRequest(baseTime + i));
        }

        assertFalse(limiter.allowRequest(baseTime + 3));
    }

    @Test
    @DisplayName("时间戳顺序：记录应该按时间顺序存储")
    void testTimestampOrdering() {
        long[] timestamps = {1000L, 1200L, 1400L};
        for (long ts : timestamps) {
            limiter.allowRequest(ts);
        }

        assertEquals(1000L, limiter.getOldestTimestamp());
        assertEquals(1400L, limiter.getLatestTimestamp());
    }

    @Test
    @DisplayName("并发场景：连续快速请求")
    void testBurstTraffic() {
        int allowedCount = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.allowRequest(baseTime + i * 10)) {
                allowedCount++;
            }
        }

        assertEquals(3, allowedCount);
        assertEquals(3, limiter.getSize());
    }

    // ==================== 便捷构造器测试 ====================

    @Test
    @DisplayName("便捷构造器：capacity = freq")
    void testConvenientConstructor() {
        UserLimiter limiter = new UserLimiter(5, 1000L);
        assertEquals(5, limiter.getCapacity());
        assertEquals(5, limiter.getFreq());
        assertEquals(1000L, limiter.getInterval());
    }

    // ==================== 并发安全测试 ====================

    @Test
    @DisplayName("并发安全：同一用户并发请求应该被顺序执行")
    void testConcurrentRequests() throws InterruptedException {
        UserLimiter limiter = new UserLimiter(5, 3, 1000L);
        long currentTime = System.currentTimeMillis();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger(0);

        // 模拟10个并发请求
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    if (limiter.allowRequest(currentTime)) {
                        allowedCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "所有线程应该在5秒内完成");
        assertEquals(3, allowedCount.get(), "只应该允许3个请求");
        executor.shutdown();
    }

    @Test
    @DisplayName("并发安全：高频并发压力测试")
    void testHighConcurrency() throws InterruptedException {
        UserLimiter limiter = new UserLimiter(100, 50, 60000L);
        long currentTime = System.currentTimeMillis();

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    if (limiter.allowRequest(currentTime)) {
                        allowedCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(50, allowedCount.get());
        executor.shutdown();
    }

    // ==================== 监控功能测试 ====================

    @Test
    @DisplayName("监控：锁状态查询")
    void testLockMonitoring() {
        // 初始状态：未加锁
        assertFalse(limiter.isLocked(), "初始状态应该未加锁");
        assertEquals(0, limiter.getQueueLength(), "初始等待队列应该为空");

        // 正常请求后
        limiter.allowRequest(baseTime);
        assertFalse(limiter.isLocked(), "请求完成后应该释放锁");
    }

    @Test
    @DisplayName("性能：锁不应该影响性能")
    void testLockPerformance() {
        long startTime = System.currentTimeMillis();

        // 连续请求1000次
        for (int i = 0; i < 1000; i++) {
            limiter.allowRequest(baseTime + i);
        }

        long duration = System.currentTimeMillis() - startTime;

        // 1000次请求应该在合理时间内完成（即使有锁）
        assertTrue(duration < 1000, "1000次请求应该在1秒内完成，实际耗时：" + duration + "ms");
    }
}
