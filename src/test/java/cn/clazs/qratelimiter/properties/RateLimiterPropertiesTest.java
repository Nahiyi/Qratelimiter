package cn.clazs.qratelimiter.properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RateLimiterProperties 配置类测试
 */
@DisplayName("RateLimiterProperties 配置类测试")
class RateLimiterPropertiesTest {

    private RateLimiterProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
    }

    // ==================== 默认值测试 ====================

    @Test
    @DisplayName("默认值：enabled 应该为 true")
    void testDefaultEnabled() {
        assertTrue(properties.isEnabled(), "默认应该启用限流器");
    }

    @Test
    @DisplayName("默认值：freq 应该为 100")
    void testDefaultFreq() {
        assertEquals(100, properties.getFreq());
    }

    @Test
    @DisplayName("默认值：interval 应该为 60000ms")
    void testDefaultInterval() {
        assertEquals(60000L, properties.getInterval());
    }

    @Test
    @DisplayName("默认值：capacity 应该为 150")
    void testDefaultCapacity() {
        assertEquals(150, properties.getCapacity());
    }

    @Test
    @DisplayName("默认值：cacheExpireAfterAccessMinutes 应该为 1440")
    void testDefaultCacheExpireAfterAccessMinutes() {
        assertEquals(1440L, properties.getCacheExpireAfterAccessMinutes());
    }

    @Test
    @DisplayName("默认值：cacheMaximumSize 应该为 10000")
    void testDefaultCacheMaximumSize() {
        assertEquals(10000L, properties.getCacheMaximumSize());
    }

    // ==================== Getter/Setter 测试 ====================

    @Test
    @DisplayName("配置设置：可以设置和获取配置值")
    void testSetterGetter() {
        properties.setEnabled(false);
        properties.setFreq(50);
        properties.setInterval(30000L);
        properties.setCapacity(75);
        properties.setCacheExpireAfterAccessMinutes(30L);
        properties.setCacheMaximumSize(50000L);

        assertFalse(properties.isEnabled());
        assertEquals(50, properties.getFreq());
        assertEquals(30000L, properties.getInterval());
        assertEquals(75, properties.getCapacity());
        assertEquals(30L, properties.getCacheExpireAfterAccessMinutes());
        assertEquals(50000L, properties.getCacheMaximumSize());
    }

    // ==================== 参数验证测试 ====================

    @Test
    @DisplayName("参数验证：默认配置应该合法")
    void testValidateDefaultConfig() {
        assertDoesNotThrow(() -> properties.validate());
    }

    @Test
    @DisplayName("参数验证：freq 必须 > 0")
    void testValidateFreqPositive() {
        properties.setFreq(0);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                properties::validate
        );
        assertTrue(ex.getMessage().contains("freq 必须大于 0"));
    }

    @Test
    @DisplayName("参数验证：interval 必须 > 0")
    void testValidateIntervalPositive() {
        properties.setInterval(0);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                properties::validate
        );
        assertTrue(ex.getMessage().contains("interval 必须大于 0"));
    }

    @Test
    @DisplayName("参数验证：capacity 必须 > 0")
    void testValidateCapacityPositive() {
        properties.setCapacity(0);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                properties::validate
        );
        assertTrue(ex.getMessage().contains("capacity 必须大于 0"));
    }

    @Test
    @DisplayName("参数验证：capacity 必须 >= freq")
    void testValidateCapacityGreaterThanOrEqualFreq() {
        properties.setFreq(100);
        properties.setCapacity(50);  // capacity < freq

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                properties::validate
        );
        assertTrue(ex.getMessage().contains("capacity 必须大于等于 freq"));
        assertTrue(ex.getMessage().contains("capacity=50"));
        assertTrue(ex.getMessage().contains("freq=100"));
    }

    @Test
    @DisplayName("参数验证：cacheExpireAfterAccessMinutes 必须 > 0")
    void testValidateCacheExpireAfterAccessMinutesPositive() {
        properties.setCacheExpireAfterAccessMinutes(0);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                properties::validate
        );
        assertTrue(ex.getMessage().contains("cacheExpireAfterAccessMinutes 必须大于 0"));
    }

    @Test
    @DisplayName("参数验证：cacheMaximumSize 必须 > 0")
    void testValidateCacheMaximumSizePositive() {
        properties.setCacheMaximumSize(0);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                properties::validate
        );
        assertTrue(ex.getMessage().contains("cacheMaximumSize 必须大于 0"));
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("边界条件：最小合法配置")
    void testMinimumValidConfig() {
        properties.setFreq(1);
        properties.setInterval(1L);
        properties.setCapacity(1);  // capacity = freq = 1

        assertDoesNotThrow(() -> properties.validate());
    }

    @Test
    @DisplayName("边界条件：capacity = freq 应该合法")
    void testCapacityEqualsFreq() {
        properties.setFreq(100);
        properties.setCapacity(100);  // capacity = freq

        assertDoesNotThrow(() -> properties.validate());
    }

    @Test
    @DisplayName("边界条件：capacity > freq 应该合法")
    void testCapacityGreaterThanFreq() {
        properties.setFreq(100);
        properties.setCapacity(150);  // capacity > freq

        assertDoesNotThrow(() -> properties.validate());
    }

    // ==================== 辅助方法测试 ====================

    @Test
    @DisplayName("辅助方法：getSummary 应该返回正确的摘要信息")
    void testGetSummary() {
        properties.setEnabled(true);
        properties.setFreq(50);
        properties.setInterval(30000L);
        properties.setCapacity(75);

        String summary = properties.getSummary();

        assertTrue(summary.contains("enabled=true"));
        assertTrue(summary.contains("freq=50"));
        assertTrue(summary.contains("interval=30000"));
        assertTrue(summary.contains("capacity=75"));
    }
}
