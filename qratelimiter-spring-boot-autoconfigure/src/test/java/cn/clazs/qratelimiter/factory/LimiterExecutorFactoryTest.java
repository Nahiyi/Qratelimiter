package cn.clazs.qratelimiter.factory;

import cn.clazs.qratelimiter.core.LimiterExecutor;
import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;
import cn.clazs.qratelimiter.properties.RateLimiterProperties;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LimiterExecutorFactory 测试")
class LimiterExecutorFactoryTest {

    @Test
    @DisplayName("本地执行器应继承用户配置的缓存生命周期")
    void localExecutorsShouldUseConfiguredCachePolicy() throws IllegalAccessException {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setCacheExpireAfterAccessMinutes(2L);
        properties.setCacheMaximumSize(7L);

        LimiterExecutorFactory factory = new LimiterExecutorFactory(properties);

        for (RateLimitAlgorithm algorithm : RateLimitAlgorithm.values()) {
            LimiterExecutor executor = factory.getExecutor(algorithm, RateLimitStorage.LOCAL);
            Cache<?, ?> cache = findExecutorCache(executor);

            assertEquals(7L, cache.policy().eviction().orElseThrow(AssertionError::new).getMaximum(),
                    algorithm + " should use configured maximum cache size");
            assertEquals(2L, cache.policy().expireAfterAccess().orElseThrow(AssertionError::new)
                            .getExpiresAfter(TimeUnit.MINUTES),
                    algorithm + " should use configured expire-after-access minutes");
        }
    }

    private Cache<?, ?> findExecutorCache(LimiterExecutor executor) throws IllegalAccessException {
        for (Field field : executor.getClass().getDeclaredFields()) {
            if (Cache.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                Object value = field.get(executor);
                assertTrue(value instanceof Cache);
                return (Cache<?, ?>) value;
            }
        }
        throw new AssertionError("No Caffeine cache field found in " + executor.getClass().getName());
    }
}
