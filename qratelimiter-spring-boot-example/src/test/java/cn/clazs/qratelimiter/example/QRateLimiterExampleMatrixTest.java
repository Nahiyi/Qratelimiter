package cn.clazs.qratelimiter.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AbstractQRateLimiterExampleMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    abstract String expectedAlgorithm();

    abstract String expectedStorage();

    @Test
    void algorithmEndpointUsesConfiguredAlgorithmAndStorageAndEventuallyRejects() throws Exception {
        String key = "matrix-" + expectedAlgorithm().toLowerCase() + "-" + expectedStorage().toLowerCase()
                + "-" + UUID.randomUUID();

        mockMvc.perform(get("/examples/algorithms/current/{key}", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("algorithm-current"))
                .andExpect(jsonPath("$.algorithm").value(expectedAlgorithm()))
                .andExpect(jsonPath("$.storage").value(expectedStorage()));

        boolean rejected = false;
        String rejectedBody = "";
        for (int i = 0; i < 5 && !rejected; i++) {
            MvcResult result = mockMvc.perform(get("/examples/algorithms/current/{key}", key))
                    .andReturn();
            int status = result.getResponse().getStatus();
            assertThat(status).isIn(200, 429);
            if (status == 429) {
                rejected = true;
                rejectedBody = result.getResponse().getContentAsString();
            }
        }

        assertThat(rejected)
                .as("%s + %s should trigger rate limiting within a short burst",
                        expectedAlgorithm(), expectedStorage())
                .isTrue();
        assertThat(rejectedBody).contains("algorithm demo rate limited");
        assertThat(rejectedBody).contains(key);
    }
}

@SpringBootTest(classes = QRateLimiterExampleApplication.class)
@AutoConfigureMockMvc
class SlidingWindowLogLocalExampleMatrixTest extends AbstractQRateLimiterExampleMatrixTest {

    @Override
    String expectedAlgorithm() {
        return "SLIDING_WINDOW_LOG";
    }

    @Override
    String expectedStorage() {
        return "LOCAL";
    }
}

@SpringBootTest(classes = QRateLimiterExampleApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("sliding-window-counter")
class SlidingWindowCounterLocalExampleMatrixTest extends AbstractQRateLimiterExampleMatrixTest {

    @Override
    String expectedAlgorithm() {
        return "SLIDING_WINDOW_COUNTER";
    }

    @Override
    String expectedStorage() {
        return "LOCAL";
    }
}

@SpringBootTest(classes = QRateLimiterExampleApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("token-bucket")
class TokenBucketLocalExampleMatrixTest extends AbstractQRateLimiterExampleMatrixTest {

    @Override
    String expectedAlgorithm() {
        return "TOKEN_BUCKET";
    }

    @Override
    String expectedStorage() {
        return "LOCAL";
    }
}

@SpringBootTest(classes = QRateLimiterExampleApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("leaky-bucket")
class LeakyBucketLocalExampleMatrixTest extends AbstractQRateLimiterExampleMatrixTest {

    @Override
    String expectedAlgorithm() {
        return "LEAKY_BUCKET";
    }

    @Override
    String expectedStorage() {
        return "LOCAL";
    }
}

@SpringBootTest(classes = QRateLimiterExampleApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("redis")
@RequiresRedis
class SlidingWindowLogRedisExampleMatrixTest extends AbstractQRateLimiterExampleMatrixTest {

    @Override
    String expectedAlgorithm() {
        return "SLIDING_WINDOW_LOG";
    }

    @Override
    String expectedStorage() {
        return "REDIS";
    }
}

@SpringBootTest(classes = QRateLimiterExampleApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles({"redis", "sliding-window-counter"})
@RequiresRedis
class SlidingWindowCounterRedisExampleMatrixTest extends AbstractQRateLimiterExampleMatrixTest {

    @Override
    String expectedAlgorithm() {
        return "SLIDING_WINDOW_COUNTER";
    }

    @Override
    String expectedStorage() {
        return "REDIS";
    }
}

@SpringBootTest(classes = QRateLimiterExampleApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles({"redis", "token-bucket"})
@RequiresRedis
class TokenBucketRedisExampleMatrixTest extends AbstractQRateLimiterExampleMatrixTest {

    @Override
    String expectedAlgorithm() {
        return "TOKEN_BUCKET";
    }

    @Override
    String expectedStorage() {
        return "REDIS";
    }
}

@SpringBootTest(classes = QRateLimiterExampleApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles({"redis", "leaky-bucket"})
@RequiresRedis
class LeakyBucketRedisExampleMatrixTest extends AbstractQRateLimiterExampleMatrixTest {

    @Override
    String expectedAlgorithm() {
        return "LEAKY_BUCKET";
    }

    @Override
    String expectedStorage() {
        return "REDIS";
    }
}

@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.METHOD})
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Documented
@org.junit.jupiter.api.extension.ExtendWith(RedisAvailabilityCondition.class)
@interface RequiresRedis {
}

final class RedisAvailabilityCondition implements ExecutionCondition {
    private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("Redis is reachable");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String forced = firstNonBlank(
                System.getProperty("qratelimiter.redis.tests.enabled"),
                System.getenv("QRL_REDIS_TESTS_ENABLED"));
        if ("true".equalsIgnoreCase(forced)) {
            return ENABLED;
        }

        String host = firstNonBlank(
                System.getProperty("qratelimiter.redis.host"),
                System.getenv("QRL_REDIS_HOST"),
                "localhost");
        int port = parsePort(firstNonBlank(
                System.getProperty("qratelimiter.redis.port"),
                System.getenv("QRL_REDIS_PORT"),
                "6379"));

        if (isTcpReachable(host, port)) {
            return ENABLED;
        }

        return ConditionEvaluationResult.disabled("Redis is not reachable at " + host + ":" + port);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 6379;
        }
    }

    private boolean isTcpReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
