package cn.clazs.qratelimiter.spring;

import cn.clazs.qratelimiter.spring.fixture.StarterCompatibilityTestApplication;
import org.junit.jupiter.api.DisplayName;
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

@SpringBootTest(
        classes = StarterCompatibilityTestApplication.class,
        properties = {
                "clazs.ratelimiter.freq=2",
                "clazs.ratelimiter.interval=60000",
                "clazs.ratelimiter.capacity=3",
                "clazs.ratelimiter.algorithm=sliding-window-log",
                "clazs.ratelimiter.storage=local",
                "clazs.ratelimiter.cache-expire-after-access-minutes=1",
                "clazs.ratelimiter.cache-maximum-size=512"
        }
)
@AutoConfigureMockMvc
@DisplayName("Spring Boot starter local compatibility")
class SpringBootStarterLocalCompatibilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("annotation basic key returns 429 with limitKey")
    void annotationBasicKeyReturns429WithLimitKey() throws Exception {
        String userId = uniqueKey("basic");

        mockMvc.perform(get("/compat/basic/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("basic"))
                .andExpect(jsonPath("$.key").value(userId));

        mockMvc.perform(get("/compat/basic/{userId}", userId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/compat/basic/{userId}", userId))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.message").value("basic compatibility limited"))
                .andExpect(jsonPath("$.limitKey").value(containsString(userId)));
    }

    @Test
    @DisplayName("SpEL object expression builds composite limit key")
    void spelObjectExpressionBuildsCompositeLimitKey() throws Exception {
        String userId = uniqueKey("spel");
        String body = "{\"userId\":\"" + userId + "\",\"apiType\":\"search\"}";

        mockMvc.perform(post("/compat/spel/object")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("spel-object"))
                .andExpect(jsonPath("$.key").value(userId + ":search"));

        mockMvc.perform(post("/compat/spel/object")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.limitKey").value(containsString(userId + ":search")));
    }

    @Test
    @DisplayName("SpEL string literal is unquoted")
    void spelStringLiteralIsUnquoted() throws Exception {
        mockMvc.perform(get("/compat/spel/constant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("spel-constant"))
                .andExpect(jsonPath("$.key").value("constant_api"));

        mockMvc.perform(get("/compat/spel/constant"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.limitKey").value(containsString(":constant_api")));
    }

    @Test
    @DisplayName("method scope keeps same business key independent")
    void methodScopeKeepsSameBusinessKeyIndependent() throws Exception {
        String userId = uniqueKey("method");

        mockMvc.perform(get("/compat/scope/method-a/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("method-a"));

        mockMvc.perform(get("/compat/scope/method-a/{userId}", userId))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/compat/scope/method-b/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("method-b"));
    }

    @Test
    @DisplayName("global scope shares quota across methods")
    void globalScopeSharesQuotaAcrossMethods() throws Exception {
        String userId = uniqueKey("global");

        mockMvc.perform(get("/compat/scope/global-a/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("global-a"));

        mockMvc.perform(get("/compat/scope/global-b/{userId}", userId))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.limitKey").value(userId));
    }

    @Test
    @DisplayName("programmatic template bean is available through starter")
    void programmaticTemplateBeanIsAvailableThroughStarter() throws Exception {
        String userId = uniqueKey("template");

        mockMvc.perform(get("/compat/template/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("template"))
                .andExpect(jsonPath("$.key").value("template:" + userId));

        mockMvc.perform(get("/compat/template/{userId}", userId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/compat/template/{userId}", userId))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("template compatibility limited"))
                .andExpect(jsonPath("$.limitKey").value("template:" + userId));
    }

    private String uniqueKey(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
