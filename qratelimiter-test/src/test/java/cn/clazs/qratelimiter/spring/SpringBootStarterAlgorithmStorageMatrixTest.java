package cn.clazs.qratelimiter.spring;

import cn.clazs.qratelimiter.spring.fixture.StarterCompatibilityTestApplication;
import cn.clazs.qratelimiter.testsupport.RedisTestSupport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AbstractSpringBootStarterAlgorithmStorageMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    abstract String expectedAlgorithm();

    abstract String expectedStorage();

    @BeforeEach
    void requireRedisWhenRedisStorageIsActive() {
        if ("REDIS".equals(expectedStorage())) {
            Assumptions.assumeTrue(RedisTestSupport.isRedisRequiredOrAvailable(),
                    RedisTestSupport.unavailableMessage());
        }
    }

    @Test
    void configuredAlgorithmAndStorageEventuallyReject() throws Exception {
        String key = "matrix-" + expectedAlgorithm().toLowerCase() + "-"
                + expectedStorage().toLowerCase() + "-" + UUID.randomUUID();

        mockMvc.perform(get("/compat/algorithm/{key}", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("algorithm"))
                .andExpect(jsonPath("$.algorithm").value(expectedAlgorithm()))
                .andExpect(jsonPath("$.storage").value(expectedStorage()))
                .andExpect(jsonPath("$.key").value(key));

        boolean rejected = false;
        String rejectedBody = "";
        for (int i = 0; i < 6 && !rejected; i++) {
            MvcResult result = mockMvc.perform(get("/compat/algorithm/{key}", key)).andReturn();
            int status = result.getResponse().getStatus();
            assertThat(status).isIn(200, 429);
            if (status == 429) {
                rejected = true;
                rejectedBody = result.getResponse().getContentAsString();
            }
        }

        assertThat(rejected)
                .as("%s + %s should reject within a bounded short burst",
                        expectedAlgorithm(), expectedStorage())
                .isTrue();
        assertThat(rejectedBody).contains("algorithm compatibility limited");
        assertThat(rejectedBody).contains(key);
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clazs.ratelimiter.freq=2",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=3",
        "clazs.ratelimiter.algorithm=sliding-window-log",
        "clazs.ratelimiter.storage=local",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class SlidingWindowLogLocalStarterMatrixTest extends AbstractSpringBootStarterAlgorithmStorageMatrixTest {
    String expectedAlgorithm() {
        return "SLIDING_WINDOW_LOG";
    }

    String expectedStorage() {
        return "LOCAL";
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clazs.ratelimiter.freq=2",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=10",
        "clazs.ratelimiter.algorithm=sliding-window-counter",
        "clazs.ratelimiter.storage=local",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class SlidingWindowCounterLocalStarterMatrixTest extends AbstractSpringBootStarterAlgorithmStorageMatrixTest {
    String expectedAlgorithm() {
        return "SLIDING_WINDOW_COUNTER";
    }

    String expectedStorage() {
        return "LOCAL";
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clazs.ratelimiter.freq=2",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=3",
        "clazs.ratelimiter.algorithm=token-bucket",
        "clazs.ratelimiter.storage=local",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class TokenBucketLocalStarterMatrixTest extends AbstractSpringBootStarterAlgorithmStorageMatrixTest {
    String expectedAlgorithm() {
        return "TOKEN_BUCKET";
    }

    String expectedStorage() {
        return "LOCAL";
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clazs.ratelimiter.freq=2",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=3",
        "clazs.ratelimiter.algorithm=leaky-bucket",
        "clazs.ratelimiter.storage=local",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class LeakyBucketLocalStarterMatrixTest extends AbstractSpringBootStarterAlgorithmStorageMatrixTest {
    String expectedAlgorithm() {
        return "LEAKY_BUCKET";
    }

    String expectedStorage() {
        return "LOCAL";
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.redis.port=${QRL_REDIS_PORT:6379}",
        "spring.data.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.data.redis.port=${QRL_REDIS_PORT:6379}",
        "clazs.ratelimiter.freq=2",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=3",
        "clazs.ratelimiter.algorithm=sliding-window-log",
        "clazs.ratelimiter.storage=redis",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class SlidingWindowLogRedisStarterMatrixTest extends AbstractSpringBootStarterAlgorithmStorageMatrixTest {
    String expectedAlgorithm() {
        return "SLIDING_WINDOW_LOG";
    }

    String expectedStorage() {
        return "REDIS";
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.redis.port=${QRL_REDIS_PORT:6379}",
        "spring.data.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.data.redis.port=${QRL_REDIS_PORT:6379}",
        "clazs.ratelimiter.freq=2",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=10",
        "clazs.ratelimiter.algorithm=sliding-window-counter",
        "clazs.ratelimiter.storage=redis",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class SlidingWindowCounterRedisStarterMatrixTest extends AbstractSpringBootStarterAlgorithmStorageMatrixTest {
    String expectedAlgorithm() {
        return "SLIDING_WINDOW_COUNTER";
    }

    String expectedStorage() {
        return "REDIS";
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.redis.port=${QRL_REDIS_PORT:6379}",
        "spring.data.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.data.redis.port=${QRL_REDIS_PORT:6379}",
        "clazs.ratelimiter.freq=2",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=3",
        "clazs.ratelimiter.algorithm=token-bucket",
        "clazs.ratelimiter.storage=redis",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class TokenBucketRedisStarterMatrixTest extends AbstractSpringBootStarterAlgorithmStorageMatrixTest {
    String expectedAlgorithm() {
        return "TOKEN_BUCKET";
    }

    String expectedStorage() {
        return "REDIS";
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.redis.port=${QRL_REDIS_PORT:6379}",
        "spring.data.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.data.redis.port=${QRL_REDIS_PORT:6379}",
        "clazs.ratelimiter.freq=2",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=3",
        "clazs.ratelimiter.algorithm=leaky-bucket",
        "clazs.ratelimiter.storage=redis",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class LeakyBucketRedisStarterMatrixTest extends AbstractSpringBootStarterAlgorithmStorageMatrixTest {
    String expectedAlgorithm() {
        return "LEAKY_BUCKET";
    }

    String expectedStorage() {
        return "REDIS";
    }
}
