package cn.clazs.qratelimiter.registry;

import cn.clazs.qratelimiter.core.RateLimiter;
import cn.clazs.qratelimiter.factory.LimiterExecutorFactory;
import cn.clazs.qratelimiter.properties.RateLimiterProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RateLimitRegistry 测试类
 */
@DisplayName("RateLimitRegistry 注册中心测试")
class RateLimitRegistryTest {

    private RateLimiterProperties properties;
    private LimiterExecutorFactory executorFactory;
    private RateLimitRegistry registry;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setFreq(10);
        properties.setInterval(1000L);
        properties.setCapacity(15);
        properties.setCacheExpireAfterAccessMinutes(1L);  // 1分钟过期
        properties.setCacheMaximumSize(100L);

        executorFactory = new LimiterExecutorFactory();
        registry = new RateLimitRegistry(properties, executorFactory);
    }

    // ==================== 基本功能测试 ====================

    @Test
    @DisplayName("基本功能：获取用户的限流器")
    void testGetLimiter() {
        RateLimiter limiter = registry.getLimiter("user123");

        assertNotNull(limiter, "限流器不应该为 null");
        assertEquals(15, limiter.getConfig().getCapacity(), "容量应该从配置读取");
        assertEquals(10, limiter.getConfig().getFreq(), "频率应该从配置读取");
        assertEquals(1000L, limiter.getConfig().getInterval(), "时间窗口应该从配置读取");
    }

    @Test
    @DisplayName("基本功能：多次获取同一用户返回相同实例")
    void testSameInstanceForSameUser() {
        RateLimiter limiter1 = registry.getLimiter("user123");
        RateLimiter limiter2 = registry.getLimiter("user123");

        assertSame(limiter1, limiter2, "应该返回同一个实例");
    }

    @Test
    @DisplayName("基本功能：不同用户返回不同实例")
    void testDifferentInstanceForDifferentUsers() {
        RateLimiter limiter1 = registry.getLimiter("user123");
        RateLimiter limiter2 = registry.getLimiter("user456");

        assertNotSame(limiter1, limiter2, "应该返回不同实例");
    }

    // ==================== 限流功能测试 ====================

    @Test
    @DisplayName("限流功能：限流器正常工作")
    void testRateLimitingWorks() {
        RateLimiter limiter = registry.getLimiter("user123");

        // 前10次应该允许
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.allowRequest("user123", 10, 1000L, 15), "第" + (i + 1) + "次请求应该被允许");
        }

        // 第11次应该被拒绝
        assertFalse(limiter.allowRequest("user123", 10, 1000L, 15), "第11次请求应该被拒绝");
    }

    @Test
    @DisplayName("限流功能：不同用户独立限流")
    void testIndependentRateLimiting() {
        RateLimiter limiter1 = registry.getLimiter("user123");
        RateLimiter limiter2 = registry.getLimiter("user456");

        // user123 耗完配额
        for (int i = 0; i < 10; i++) {
            limiter1.allowRequest("user123", 10, 1000L, 15);
        }
        assertFalse(limiter1.allowRequest("user123", 10, 1000L, 15), "user123 应该被限流");

        // user456 不受影响
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter2.allowRequest("user456", 10, 1000L, 15), "user456 应该正常");
        }
    }

    // ==================== 参数验证测试 ====================

    @Test
    @DisplayName("参数验证：拒绝 null 配置")
    void testRejectNullProperties() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimitRegistry(null, null));
    }

    @Test
    @DisplayName("参数验证：拒绝非法配置")
    void testRejectInvalidProperties() {
        RateLimiterProperties invalidProps = new RateLimiterProperties();
        invalidProps.setFreq(0);  // 非法

        assertThrows(IllegalArgumentException.class, () -> new RateLimitRegistry(invalidProps, null));
    }

    @Test
    @DisplayName("参数验证：拒绝空用户ID")
    void testRejectEmptyUserId() {
        assertThrows(IllegalArgumentException.class, () -> registry.getLimiter(""));
        assertThrows(IllegalArgumentException.class, () -> registry.getLimiter("   "));
        assertThrows(IllegalArgumentException.class, () -> registry.getLimiter(null));
    }

    // ==================== 缓存管理测试 ====================

    @Test
    @DisplayName("缓存管理：hasLimiter 正确判断")
    void testHasLimiter() {
        assertFalse(registry.hasLimiter("user123"), "初始时应该不存在");

        registry.getLimiter("user123");
        assertTrue(registry.hasLimiter("user123"), "创建后应该存在");
    }

    @Test
    @DisplayName("缓存管理：手动移除限流器")
    void testRemoveLimiter() {
        registry.getLimiter("user123");
        assertTrue(registry.hasLimiter("user123"), "创建后应该存在");

        registry.removeLimiter("user123");
        assertFalse(registry.hasLimiter("user123"), "移除后不应该存在");
    }

    @Test
    @DisplayName("缓存管理：清空所有限流器")
    void testClearAll() {
        // 创建独立的 registry，并设置更大的缓存避免自动清理干扰
        RateLimiterProperties testProps = new RateLimiterProperties();
        testProps.setFreq(10);
        testProps.setInterval(1000L);
        testProps.setCapacity(15);
        testProps.setCacheExpireAfterAccessMinutes(1L);
        testProps.setCacheMaximumSize(1000L);

        RateLimitRegistry testRegistry = new RateLimitRegistry(testProps, executorFactory);
        testRegistry.getLimiter("user1");
        testRegistry.getLimiter("user2");
        testRegistry.getLimiter("user3");

        // 验证限流器存在
        assertTrue(testRegistry.hasLimiter("user1"), "user1 应该存在");
        assertTrue(testRegistry.hasLimiter("user2"), "user2 应该存在");
        assertTrue(testRegistry.hasLimiter("user3"), "user3 应该存在");

        // 清空所有
        testRegistry.clearAll();

        // 验证所有限流器已被清除
        assertFalse(testRegistry.hasLimiter("user1"), "清空后 user1 不应该存在");
        assertFalse(testRegistry.hasLimiter("user2"), "清空后 user2 不应该存在");
        assertFalse(testRegistry.hasLimiter("user3"), "清空后 user3 不应该存在");
    }

    @Test
    @DisplayName("缓存管理：removeLimiter 容错处理")
    void testRemoveLimiterWithNull() {
        // 不应该抛出异常
        assertDoesNotThrow(() -> registry.removeLimiter(null));
        assertDoesNotThrow(() -> registry.removeLimiter(""));
        assertDoesNotThrow(() -> registry.removeLimiter("nonexistent"));
    }

    // ==================== 统计信息测试 ====================

    @Test
    @DisplayName("统计信息：创建计数正确")
    void testCreationCount() {
        assertEquals(0, registry.getTotalCreatedLimiters(), "初始应该为0");

        registry.getLimiter("user1");
        assertEquals(1, registry.getTotalCreatedLimiters(), "创建1个后应该为1");

        registry.getLimiter("user1");  // 重复获取
        assertEquals(1, registry.getTotalCreatedLimiters(), "重复获取不应该增加计数");

        registry.getLimiter("user2");
        assertEquals(2, registry.getTotalCreatedLimiters(), "创建第2个后应该为2");
    }

    @Test
    @DisplayName("统计信息：getStats 返回正确信息")
    void testGetStats() {
        registry.getLimiter("user1");
        registry.getLimiter("user2");

        String stats = registry.getStats();
        assertTrue(stats.contains("totalCreated=2"), "应该包含创建总数");
        assertTrue(stats.contains("currentCacheSize"), "应该包含当前缓存大小");
    }

    @Test
    @DisplayName("统计信息：高级统计信息")
    void testGetAdvancedStats() {
        // 先进行一些操作
        registry.getLimiter("user1");
        registry.getLimiter("user1");  // 命中
        registry.getLimiter("user2");
        registry.hasLimiter("user1");  // 命中

        RateLimitRegistry.CacheStats stats = registry.getAdvancedStats();

        assertNotNull(stats, "统计信息不应该为 null");
        assertTrue(stats.getRequestCount() > 0, "请求数应该大于0");
        assertTrue(stats.getHitCount() > 0, "命中数应该大于0");
    }

    // ==================== 并发安全测试 ====================

    @Test
    @DisplayName("并发安全：多线程同时获取同一用户限流器")
    void testConcurrentGetSameUser() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger uniqueInstances = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    RateLimiter limiter = registry.getLimiter("user123");
                    // 使用同步来统计唯一实例（虽然理论上应该只有一个）
                    synchronized (RateLimitRegistryTest.this) {
                        uniqueInstances.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 虽然有10个线程，但应该只创建了1个限流器实例
        assertEquals(1, registry.getTotalCreatedLimiters(), "应该只创建1个限流器");
    }

    @Test
    @DisplayName("并发安全：多线程同时获取不同用户限流器")
    void testConcurrentGetDifferentUsers() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    registry.getLimiter("user" + userId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 应该创建了100个不同的限流器
        assertEquals(100, registry.getTotalCreatedLimiters(), "应该创建100个限流器");
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("边界条件：最小合法配置")
    void testMinimumValidConfiguration() {
        RateLimiterProperties minProps = new RateLimiterProperties();
        minProps.setFreq(1);
        minProps.setInterval(1L);
        minProps.setCapacity(1);
        minProps.setCacheExpireAfterAccessMinutes(1L);
        minProps.setCacheMaximumSize(1L);

        assertDoesNotThrow(() -> new RateLimitRegistry(minProps, executorFactory));
    }

    @Test
    @DisplayName("边界条件：大量用户")
    void testLargeNumberOfUsers() {
        // 使用独立的 registry，并设置更大的缓存限制
        RateLimiterProperties largeProps = new RateLimiterProperties();
        largeProps.setFreq(10);
        largeProps.setInterval(1000L);
        largeProps.setCapacity(15);
        largeProps.setCacheExpireAfterAccessMinutes(1L);
        largeProps.setCacheMaximumSize(1000L);  // 设置足够大的缓存

        RateLimitRegistry largeRegistry = new RateLimitRegistry(largeProps, executorFactory);
        int userCount = 50;  // 测试50个用户（避免触发自动清理）

        for (int i = 0; i < userCount; i++) {
            largeRegistry.getLimiter("user" + i);
        }

        // 检查创建的总数（应该等于 userCount）
        assertEquals(userCount, largeRegistry.getTotalCreatedLimiters(), "应该创建50个限流器");

        // 检查当前缓存大小（应该接近 userCount，可能因为自动清理而略小）
        assertTrue(largeRegistry.getCurrentCacheSize() >= userCount - 5,
                "缓存中应该有接近50个限流器（允许少量自动清理）");
    }
}
