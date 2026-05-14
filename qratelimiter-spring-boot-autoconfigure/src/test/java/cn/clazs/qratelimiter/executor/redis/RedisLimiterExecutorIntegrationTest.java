package cn.clazs.qratelimiter.executor.redis;

import cn.clazs.qratelimiter.core.LimiterExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Redis 限流执行器集成测试")
class RedisLimiterExecutorIntegrationTest {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private String keyPrefix;

    @BeforeEach
    void setUp() {
        String host = redisHost();
        int port = redisPort();
        connectionFactory = new LettuceConnectionFactory(host, port);
        connectionFactory.afterPropertiesSet();

        Assumptions.assumeTrue(isRedisAvailable(connectionFactory),
                host + ":" + port + " Redis 不可用，跳过 Redis 集成测试");

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        keyPrefix = "qratelimiter:it:" + UUID.randomUUID() + ":";
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("Redis 滑动窗口日志：窗口内达到阈值后拒绝")
    void redisSlidingWindowLogRejectsAfterThreshold() {
        LimiterExecutor executor = new RedisSlidingWindowLogExecutor(redisTemplate, keyPrefix);
        String key = "redis:log";

        assertTrue(executor.tryAcquire(key, 2, 1000L, 3));
        assertTrue(executor.tryAcquire(key, 2, 1000L, 3));
        assertFalse(executor.tryAcquire(key, 2, 1000L, 3));
    }

    @Test
    @DisplayName("Redis 滑动窗口计数器：窗口内达到阈值后拒绝")
    void redisSlidingWindowCounterRejectsAfterThreshold() {
        LimiterExecutor executor = new RedisSlidingWindowCounterExecutor(redisTemplate, keyPrefix);
        String key = "redis:counter";

        assertTrue(executor.tryAcquire(key, 2, 1000L, 10));
        assertTrue(executor.tryAcquire(key, 2, 1000L, 10));
        assertFalse(executor.tryAcquire(key, 2, 1000L, 10));
    }

    @Test
    @DisplayName("Redis 令牌桶：耗尽令牌后拒绝")
    void redisTokenBucketRejectsWhenTokensAreExhausted() {
        LimiterExecutor executor = new RedisTokenBucketExecutor(redisTemplate, keyPrefix);
        String key = "redis:token";

        assertTrue(executor.tryAcquire(key, 1, 60_000L, 2));
        assertTrue(executor.tryAcquire(key, 1, 60_000L, 2));
        assertFalse(executor.tryAcquire(key, 1, 60_000L, 2));
    }

    @Test
    @DisplayName("Redis 漏桶：满桶后拒绝")
    void redisLeakyBucketRejectsWhenBucketIsFull() {
        LimiterExecutor executor = new RedisLeakyBucketExecutor(redisTemplate, keyPrefix);
        String key = "redis:leaky";

        assertTrue(executor.tryAcquire(key, 1, 60_000L, 2));
        assertTrue(executor.tryAcquire(key, 1, 60_000L, 2));
        assertFalse(executor.tryAcquire(key, 1, 60_000L, 2));
    }

    private boolean isRedisAvailable(LettuceConnectionFactory factory) {
        try (RedisConnection connection = factory.getConnection()) {
            return "PONG".equals(connection.ping());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String redisHost() {
        return firstNonBlank(
                System.getProperty("qratelimiter.redis.host"),
                System.getenv("QRL_REDIS_HOST"),
                "localhost");
    }

    private int redisPort() {
        String value = firstNonBlank(
                System.getProperty("qratelimiter.redis.port"),
                System.getenv("QRL_REDIS_PORT"),
                "6379");
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 6379;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
