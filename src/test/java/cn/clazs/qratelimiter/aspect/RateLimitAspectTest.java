package cn.clazs.qratelimiter.aspect;

import cn.clazs.qratelimiter.annotation.DoRateLimit;
import cn.clazs.qratelimiter.exception.RateLimitException;
import cn.clazs.qratelimiter.properties.RateLimiterProperties;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;
import cn.clazs.qratelimiter.value.UserLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RateLimitAspect 测试类
 * 测试 AOP 限流切面的功能（不依赖 Spring 上下文）
 */
@DisplayName("RateLimitAspect 切面测试")
class RateLimitAspectTest {

    private RateLimitRegistry registry;
    private RateLimitAspect aspect;

    @BeforeEach
    void setUp() {
        // 创建配置
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setFreq(3);  // 频率：3次
        properties.setInterval(1000L);  // 时间窗口：1000ms
        properties.setCapacity(5);  // 容量：5
        properties.setCacheExpireAfterAccessMinutes(1L);
        properties.setCacheMaximumSize(100L);

        // 创建注册中心
        registry = new RateLimitRegistry(properties);

        // 创建切面
        aspect = new RateLimitAspect(registry);
    }

    // ==================== 基本功能测试 ====================

    @Test
    @DisplayName("基本功能：限流器注册和获取")
    void testRegistryAndGetLimiter() {
        UserLimiter limiter = registry.getLimiter("user123");

        assertNotNull(limiter, "限流器不应该为 null");
        assertEquals(5, limiter.getCapacity());
        assertEquals(3, limiter.getFreq());
    }

    @Test
    @DisplayName("基本功能：正常请求应该被允许")
    void testNormalRequestAllowed() {
        UserLimiter limiter = registry.getLimiter("user123");

        // 前3次请求应该成功
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.allowRequest(), "第" + (i + 1) + "次请求应该被允许");
        }
    }

    @Test
    @DisplayName("基本功能：超频请求应该被拦截")
    void testRateLimitTriggered() {
        UserLimiter limiter = registry.getLimiter("user456");

        // 前3次请求应该成功
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.allowRequest());
        }

        // 第4次请求应该被限流
        assertFalse(limiter.allowRequest(), "第4次请求应该被限流");
    }

    @Test
    @DisplayName("基本功能：不同用户独立限流")
    void testDifferentUsersIndependentLimiting() {
        UserLimiter limiterA = registry.getLimiter("userA");
        UserLimiter limiterB = registry.getLimiter("userB");

        // userA 耗完配额
        for (int i = 0; i < 3; i++) {
            limiterA.allowRequest();
        }

        // userA 应该被限流
        assertFalse(limiterA.allowRequest(), "userA 应该被限流");

        // userB 不受影响
        for (int i = 0; i < 3; i++) {
            assertTrue(limiterB.allowRequest(), "userB 应该正常");
        }
    }

    @Test
    @DisplayName("基本功能：常量 Key 应该正常工作")
    void testConstantKey() {
        UserLimiter limiter1 = registry.getLimiter("'api_constant_key'");
        UserLimiter limiter2 = registry.getLimiter("'api_constant_key'");

        // 应该返回同一个限流器
        assertSame(limiter1, limiter2, "常量 Key 应该返回同一个限流器");
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("边界条件：特殊字符 Key 应该正常工作")
    void testSpecialCharactersInKey() {
        String specialKey = "user_123@example.com";
        UserLimiter limiter = registry.getLimiter(specialKey);

        assertNotNull(limiter, "特殊字符 Key 的限流器不应该为 null");

        // 前3次请求应该成功
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.allowRequest());
        }

        // 应该被限流
        assertFalse(limiter.allowRequest());
    }

    @Test
    @DisplayName("边界条件：Long 类型 Key 应该正常工作")
    void testLongTypeKey() {
        Long userId = 123456L;
        UserLimiter limiter = registry.getLimiter(userId.toString());

        assertNotNull(limiter);
        assertEquals(3, limiter.getFreq());
    }

    @Test
    @DisplayName("边界条件：中文 Key 应该正常工作")
    void testChineseKey() {
        String chineseKey = "用户张三";
        UserLimiter limiter = registry.getLimiter(chineseKey);

        assertNotNull(limiter);

        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.allowRequest());
        }

        assertFalse(limiter.allowRequest());
    }

    // ==================== 异常测试 ====================

    @Test
    @DisplayName("异常测试：RateLimitException 应该包含正确的信息")
    void testRateLimitException() {
        RateLimitException exception = new RateLimitException("user123", "自定义错误信息");

        assertEquals("user123", exception.getLimitKey());
        assertEquals("自定义错误信息", exception.getMessage());
    }

    @Test
    @DisplayName("异常测试：RateLimitException 默认错误信息")
    void testRateLimitExceptionDefaultMessage() {
        RateLimitException exception = new RateLimitException("user456");

        assertEquals("user456", exception.getLimitKey());
        assertEquals("访问过于频繁，请稍后再试", exception.getMessage());
    }

    // ==================== 注册中心测试 ====================

    @Test
    @DisplayName("注册中心：同一用户多次获取返回相同实例")
    void testSameInstanceForSameUser() {
        UserLimiter limiter1 = registry.getLimiter("user789");
        UserLimiter limiter2 = registry.getLimiter("user789");

        assertSame(limiter1, limiter2, "应该返回同一个限流器实例");
    }

    @Test
    @DisplayName("注册中心：统计信息正确")
    void testStatistics() {
        registry.getLimiter("user1");
        registry.getLimiter("user2");
        registry.getLimiter("user3");

        assertEquals(3, registry.getTotalCreatedLimiters(), "应该创建了3个限流器");
    }

    // ==================== 注解测试 ====================

    @Test
    @DisplayName("注解：@DoRateLimit 注解属性正确")
    void testDoRateLimitAnnotation() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("getUserInfo", String.class);
        DoRateLimit annotation = method.getAnnotation(DoRateLimit.class);

        assertNotNull(annotation, "@DoRateLimit 注解不应该为 null");
        assertEquals("#userId", annotation.key());
        assertEquals(-1, annotation.freq());
        assertEquals(-1, annotation.interval());
        assertEquals(-1, annotation.capacity());
        assertEquals("访问过于频繁，请稍后再试", annotation.message());
    }

    @Test
    @DisplayName("注解：自定义配置的 @DoRateLimit 注解")
    void testCustomConfigAnnotation() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("apiWithCustomConfig");
        DoRateLimit annotation = method.getAnnotation(DoRateLimit.class);

        assertNotNull(annotation);
        assertEquals("'custom_api'", annotation.key());
        assertEquals(100, annotation.freq());
        assertEquals(60000L, annotation.interval());
        assertEquals(150, annotation.capacity());
        assertEquals("自定义限流提示", annotation.message());
    }

    // ==================== 测试服务类（模拟被拦截的方法）====================

    /**
     * 测试服务类
     * 模拟真实的业务方法（标记了 @DoRateLimit 注解）
     */
    private static class TestService {

        @DoRateLimit(key = "#userId")
        public String getUserInfo(String userId) {
            return "User info for: " + userId;
        }

        @DoRateLimit(key = "'custom_api'", freq = 100, interval = 60000, capacity = 150, message = "自定义限流提示")
        public String apiWithCustomConfig() {
            return "API response";
        }

        @DoRateLimit(key = "#user.id")
        public String updateUser(User user) {
            return "User updated: " + user.getId();
        }
    }

    /**
     * 测试用的 User 类
     */
    private static class User {
        private String id;

        public User(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }
}
