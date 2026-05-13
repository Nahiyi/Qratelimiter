package cn.clazs.qratelimiter.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = QRateLimiterExampleApplication.class)
@AutoConfigureMockMvc
class QRateLimiterExampleApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void basicEndpointReturns429WithLimitKeyAfterConfiguredThreshold() throws Exception {
        String userId = uniqueKey("basic");

        mockMvc.perform(get("/examples/basic/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("basic"))
                .andExpect(jsonPath("$.key").value(userId));

        mockMvc.perform(get("/examples/basic/users/{userId}", userId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/examples/basic/users/{userId}", userId))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.message").value("basic demo rate limited"))
                .andExpect(jsonPath("$.limitKey").value(containsString(userId)));
    }

    @Test
    void spelObjectEndpointUsesCombinedRequestFieldsAsLimitKey() throws Exception {
        String userId = uniqueKey("spel");
        String body = "{\"userId\":\"" + userId + "\",\"apiType\":\"search\"}";

        mockMvc.perform(post("/examples/spel/object")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("spel-object"))
                .andExpect(jsonPath("$.key").value(userId + ":search"));

        mockMvc.perform(post("/examples/spel/object")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.limitKey").value(containsString(userId + ":search")));
    }

    @Test
    void methodScopeKeepsTwoMethodsIndependent() throws Exception {
        String userId = uniqueKey("method");

        mockMvc.perform(get("/examples/scope/method-a/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("method-a"));

        mockMvc.perform(get("/examples/scope/method-a/{userId}", userId))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/examples/scope/method-b/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("method-b"));
    }

    @Test
    void globalScopeSharesQuotaAcrossMethods() throws Exception {
        String userId = uniqueKey("global");

        mockMvc.perform(get("/examples/scope/global-a/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("global-a"));

        mockMvc.perform(get("/examples/scope/global-b/{userId}", userId))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.limitKey").value(userId));
    }

    @Test
    void algorithmEndpointShowsActiveAlgorithmAndStorage() throws Exception {
        String key = uniqueKey("algorithm");

        mockMvc.perform(get("/examples/algorithms/current/{key}", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("algorithm-current"))
                .andExpect(jsonPath("$.algorithm").value("SLIDING_WINDOW_LOG"))
                .andExpect(jsonPath("$.storage").value("LOCAL"));
    }

    @Test
    void redisDemoEndpointUsesSameAnnotationContractInLocalProfile() throws Exception {
        String userId = uniqueKey("redis");

        mockMvc.perform(get("/examples/redis/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("redis-profile"))
                .andExpect(jsonPath("$.storage").value("LOCAL"));
    }

    private String uniqueKey(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
