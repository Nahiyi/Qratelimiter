package cn.clazs.qratelimiter.autoconfigure;

import cn.clazs.qratelimiter.aspect.RateLimitAspect;
import cn.clazs.qratelimiter.properties.RateLimiterProperties;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;
import cn.clazs.qratelimiter.value.UserLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RateLimiterAutoConfiguration 测试
 *
 * <p>测试自动配置类的功能（不依赖 Spring 容器）
 *
 * <p>测试内容：
 * <ul>
 *     <li>验证自动配置类的注解是否正确</li>
 *     <li>验证手动创建 Bean 是否正常工作</li>
 *     <li>验证配置属性的默认值</li>
 *     <li>验证限流功能是否正常</li>
 * </ul>
 *
 * @author clazs
 * @since 1.0
 */
@DisplayName("RateLimiterAutoConfiguration 测试")
class RateLimiterAutoConfigurationTest {

    private RateLimiterProperties properties;
    private RateLimitRegistry registry;
    private RateLimitAspect aspect;

    @BeforeEach
    void setUp() {
        // 创建配置属性（模拟 Spring 的 @EnableConfigurationProperties）
        properties = new RateLimiterProperties();
        properties.setEnabled(true);
        properties.setFreq(100);
        properties.setInterval(60000L);
        properties.setCapacity(150);
        properties.setCacheExpireAfterAccessMinutes(1L);
        properties.setCacheMaximumSize(100L);

        // 创建注册中心（模拟 Spring 的 @Bean）
        registry = new RateLimitRegistry(properties);

        // 创建切面（模拟 Spring 的 @Bean）
        aspect = new RateLimitAspect(registry);
    }

    // ==================== 配置属性测试 ====================

    @Test
    @DisplayName("配置属性：默认值应该正确")
    void testDefaultProperties() {
        RateLimiterProperties defaultProperties = new RateLimiterProperties();

        assertTrue(defaultProperties.isEnabled(), "enabled 默认值应该是 true");
        assertEquals(100, defaultProperties.getFreq(), "freq 默认值应该是 100");
        assertEquals(60000L, defaultProperties.getInterval(), "interval 默认值应该是 60000");
        assertEquals(150, defaultProperties.getCapacity(), "capacity 默认值应该是 150");
    }

    @Test
    @DisplayName("配置属性：validate() 方法应该正确校验参数")
    void testPropertiesValidation() {
        // 默认配置应该通过校验
        assertDoesNotThrow(() -> properties.validate(), "默认配置应该通过校验");
    }

    // ==================== Bean 创建测试 ====================

    @Test
    @DisplayName("Bean 创建：RateLimitRegistry 应该正确创建")
    void testRegistryCreation() {
        assertNotNull(registry, "RateLimitRegistry 应该被成功创建");
        assertEquals(0L, registry.getCurrentCacheSize(), "初始缓存大小应该是 0");
    }

    @Test
    @DisplayName("Bean 创建：RateLimitAspect 应该正确创建")
    void testAspectCreation() {
        assertNotNull(aspect, "RateLimitAspect 应该被成功创建");
    }

    // ==================== 功能集成测试 ====================

    @Test
    @DisplayName("功能集成：限流器应该正常工作")
    void testRateLimiterIntegration() {
        // 创建限流器（使用默认配置）
        UserLimiter limiter = registry.getLimiter("test-user");

        // 前100次请求应该成功
        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.allowRequest(), "第" + (i + 1) + "次请求应该被允许");
        }

        // 第101次请求应该被限流
        assertFalse(limiter.allowRequest(), "第101次请求应该被限流");
    }

    @Test
    @DisplayName("功能集成：相同 Key 应该返回同一个限流器")
    void testSameKeySameLimiter() {
        UserLimiter limiter1 = registry.getLimiter("user123");
        UserLimiter limiter2 = registry.getLimiter("user123");

        assertSame(limiter1, limiter2, "相同 Key 应该返回同一个限流器实例");
    }

    @Test
    @DisplayName("功能集成：不同 Key 应该返回不同的限流器")
    void testDifferentKeyDifferentLimiter() {
        UserLimiter limiter1 = registry.getLimiter("userA");
        UserLimiter limiter2 = registry.getLimiter("userB");

        assertNotSame(limiter1, limiter2, "不同 Key 应该返回不同的限流器实例");
    }

    @Test
    @DisplayName("功能集成：注册中心统计信息应该正确")
    void testRegistryStats() {
        registry.getLimiter("user1");
        registry.getLimiter("user2");
        registry.getLimiter("user3");

        assertEquals(3, registry.getTotalCreatedLimiters(), "应该创建了 3 个限流器");
        assertEquals(3L, registry.getCurrentCacheSize(), "当前缓存大小应该是 3");
    }

    // ==================== AutoConfiguration 注解测试 ====================

    @Test
    @DisplayName("注解检查：自动配置类应该有正确的注解")
    void testAutoConfigurationAnnotations() {
        // 检查类是否被 @Configuration 注解修饰
        assertTrue(RateLimiterAutoConfiguration.class.isAnnotationPresent(org.springframework.context.annotation.Configuration.class),
                "RateLimiterAutoConfiguration 应该有 @Configuration 注解");
    }

    @Test
    @DisplayName("注解检查：spring.factories 文件应该存在")
    void testSpringFactoriesExists() {
        // 这个测试验证 spring.factories 文件是否在正确的位置
        // 在实际运行时，Spring Boot 会自动加载这个文件
        assertNotNull(getClass().getClassLoader().getResource("META-INF/spring.factories"),
                "META-INF/spring.factories 文件应该存在");
    }
}
