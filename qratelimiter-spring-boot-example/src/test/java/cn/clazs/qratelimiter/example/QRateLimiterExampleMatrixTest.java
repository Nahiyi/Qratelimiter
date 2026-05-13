package cn.clazs.qratelimiter.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
