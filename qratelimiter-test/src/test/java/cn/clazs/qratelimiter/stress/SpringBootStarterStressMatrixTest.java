package cn.clazs.qratelimiter.stress;

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
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

abstract class AbstractSpringBootStarterStressMatrixTest {

    private static final int ATTEMPTS = 80;

    @Autowired
    private MockMvc mockMvc;

    abstract String expectedAlgorithm();

    abstract String expectedStorage();

    abstract int expectedMaxAllowed();

    @BeforeEach
    void requireRedisWhenRedisStorageIsActive() {
        if ("REDIS".equals(expectedStorage())) {
            Assumptions.assumeTrue(RedisTestSupport.isRedisRequiredOrAvailable(),
                    RedisTestSupport.unavailableMessage());
        }
    }

    @Test
    void concurrentRequestsRespectConfiguredQuota() throws Exception {
        String key = "stress-starter-" + expectedAlgorithm().toLowerCase() + "-"
                + expectedStorage().toLowerCase() + "-" + UUID.randomUUID();

        int allowed = StressTestSupport.runConcurrentAttempts(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                MvcResult result = mockMvc.perform(get("/compat/algorithm/{key}", key)).andReturn();
                int status = result.getResponse().getStatus();
                assertThat(status).isIn(200, 429);
                return status == 200;
            }
        }, ATTEMPTS);

        assertThat(allowed)
                .as("%s + %s allowed more concurrent requests than configured",
                        expectedAlgorithm(), expectedStorage())
                .isLessThanOrEqualTo(expectedMaxAllowed());
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clazs.ratelimiter.freq=8",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=10",
        "clazs.ratelimiter.algorithm=sliding-window-log",
        "clazs.ratelimiter.storage=local",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class SlidingWindowLogLocalStarterStressTest extends AbstractSpringBootStarterStressMatrixTest {
    String expectedAlgorithm() {
        return "SLIDING_WINDOW_LOG";
    }

    String expectedStorage() {
        return "LOCAL";
    }

    int expectedMaxAllowed() {
        return 8;
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clazs.ratelimiter.freq=8",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=10",
        "clazs.ratelimiter.algorithm=sliding-window-counter",
        "clazs.ratelimiter.storage=local",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class SlidingWindowCounterLocalStarterStressTest extends AbstractSpringBootStarterStressMatrixTest {
    String expectedAlgorithm() {
        return "SLIDING_WINDOW_COUNTER";
    }

    String expectedStorage() {
        return "LOCAL";
    }

    int expectedMaxAllowed() {
        return 8;
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clazs.ratelimiter.freq=8",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=10",
        "clazs.ratelimiter.algorithm=token-bucket",
        "clazs.ratelimiter.storage=local",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class TokenBucketLocalStarterStressTest extends AbstractSpringBootStarterStressMatrixTest {
    String expectedAlgorithm() {
        return "TOKEN_BUCKET";
    }

    String expectedStorage() {
        return "LOCAL";
    }

    int expectedMaxAllowed() {
        return 10;
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "clazs.ratelimiter.freq=8",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=10",
        "clazs.ratelimiter.algorithm=leaky-bucket",
        "clazs.ratelimiter.storage=local",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class LeakyBucketLocalStarterStressTest extends AbstractSpringBootStarterStressMatrixTest {
    String expectedAlgorithm() {
        return "LEAKY_BUCKET";
    }

    String expectedStorage() {
        return "LOCAL";
    }

    int expectedMaxAllowed() {
        return 10;
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.redis.port=${QRL_REDIS_PORT:6379}",
        "spring.data.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.data.redis.port=${QRL_REDIS_PORT:6379}",
        "clazs.ratelimiter.freq=8",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=10",
        "clazs.ratelimiter.algorithm=sliding-window-log",
        "clazs.ratelimiter.storage=redis",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class SlidingWindowLogRedisStarterStressTest extends AbstractSpringBootStarterStressMatrixTest {
    String expectedAlgorithm() {
        return "SLIDING_WINDOW_LOG";
    }

    String expectedStorage() {
        return "REDIS";
    }

    int expectedMaxAllowed() {
        return 8;
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.redis.port=${QRL_REDIS_PORT:6379}",
        "spring.data.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.data.redis.port=${QRL_REDIS_PORT:6379}",
        "clazs.ratelimiter.freq=8",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=10",
        "clazs.ratelimiter.algorithm=sliding-window-counter",
        "clazs.ratelimiter.storage=redis",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class SlidingWindowCounterRedisStarterStressTest extends AbstractSpringBootStarterStressMatrixTest {
    String expectedAlgorithm() {
        return "SLIDING_WINDOW_COUNTER";
    }

    String expectedStorage() {
        return "REDIS";
    }

    int expectedMaxAllowed() {
        return 8;
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.redis.port=${QRL_REDIS_PORT:6379}",
        "spring.data.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.data.redis.port=${QRL_REDIS_PORT:6379}",
        "clazs.ratelimiter.freq=8",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=10",
        "clazs.ratelimiter.algorithm=token-bucket",
        "clazs.ratelimiter.storage=redis",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class TokenBucketRedisStarterStressTest extends AbstractSpringBootStarterStressMatrixTest {
    String expectedAlgorithm() {
        return "TOKEN_BUCKET";
    }

    String expectedStorage() {
        return "REDIS";
    }

    int expectedMaxAllowed() {
        return 10;
    }
}

@SpringBootTest(classes = StarterCompatibilityTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.redis.port=${QRL_REDIS_PORT:6379}",
        "spring.data.redis.host=${QRL_REDIS_HOST:localhost}",
        "spring.data.redis.port=${QRL_REDIS_PORT:6379}",
        "clazs.ratelimiter.freq=8",
        "clazs.ratelimiter.interval=60000",
        "clazs.ratelimiter.capacity=10",
        "clazs.ratelimiter.algorithm=leaky-bucket",
        "clazs.ratelimiter.storage=redis",
        "clazs.ratelimiter.cache-expire-after-access-minutes=1",
        "clazs.ratelimiter.cache-maximum-size=512"
})
class LeakyBucketRedisStarterStressTest extends AbstractSpringBootStarterStressMatrixTest {
    String expectedAlgorithm() {
        return "LEAKY_BUCKET";
    }

    String expectedStorage() {
        return "REDIS";
    }

    int expectedMaxAllowed() {
        return 10;
    }
}
