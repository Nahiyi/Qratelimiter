package cn.clazs.qratelimiter.spring.fixture;

import cn.clazs.qratelimiter.annotation.DoRateLimit;
import cn.clazs.qratelimiter.annotation.RateLimitScope;
import cn.clazs.qratelimiter.core.RateLimiterTemplate;
import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;
import cn.clazs.qratelimiter.exception.RateLimitException;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/compat")
public class StarterCompatibilityController {

    private final RateLimiterTemplate rateLimiterTemplate;
    private final RateLimitRegistry rateLimitRegistry;

    public StarterCompatibilityController(RateLimiterTemplate rateLimiterTemplate,
                                          RateLimitRegistry rateLimitRegistry) {
        this.rateLimiterTemplate = rateLimiterTemplate;
        this.rateLimitRegistry = rateLimitRegistry;
    }

    @GetMapping("/basic/{userId}")
    @DoRateLimit(key = "#userId", message = "basic compatibility limited")
    public Map<String, Object> basic(@PathVariable String userId) {
        Map<String, Object> response = response("basic");
        response.put("key", userId);
        return response;
    }

    @PostMapping("/spel/object")
    @DoRateLimit(key = "#request.userId + ':' + #request.apiType",
            freq = 1, interval = 60000L, capacity = 1,
            message = "spel object compatibility limited")
    public Map<String, Object> spelObject(@RequestBody CompatibilityRequest request) {
        Map<String, Object> response = response("spel-object");
        response.put("key", request.getUserId() + ":" + request.getApiType());
        return response;
    }

    @GetMapping("/spel/constant")
    @DoRateLimit(key = "'constant_api'",
            freq = 1, interval = 60000L, capacity = 1,
            message = "constant compatibility limited")
    public Map<String, Object> spelConstant() {
        Map<String, Object> response = response("spel-constant");
        response.put("key", "constant_api");
        return response;
    }

    @GetMapping("/scope/method-a/{userId}")
    @DoRateLimit(key = "#userId", freq = 1, interval = 60000L, capacity = 1,
            message = "method scope compatibility limited")
    public Map<String, Object> methodA(@PathVariable String userId) {
        Map<String, Object> response = response("method-a");
        response.put("key", userId);
        return response;
    }

    @GetMapping("/scope/method-b/{userId}")
    @DoRateLimit(key = "#userId", freq = 1, interval = 60000L, capacity = 1,
            message = "method scope compatibility limited")
    public Map<String, Object> methodB(@PathVariable String userId) {
        Map<String, Object> response = response("method-b");
        response.put("key", userId);
        return response;
    }

    @GetMapping("/scope/global-a/{userId}")
    @DoRateLimit(key = "#userId", scope = RateLimitScope.GLOBAL,
            freq = 1, interval = 60000L, capacity = 1,
            message = "global scope compatibility limited")
    public Map<String, Object> globalA(@PathVariable String userId) {
        Map<String, Object> response = response("global-a");
        response.put("key", userId);
        return response;
    }

    @GetMapping("/scope/global-b/{userId}")
    @DoRateLimit(key = "#userId", scope = RateLimitScope.GLOBAL,
            freq = 1, interval = 60000L, capacity = 1,
            message = "global scope compatibility limited")
    public Map<String, Object> globalB(@PathVariable String userId) {
        Map<String, Object> response = response("global-b");
        response.put("key", userId);
        return response;
    }

    @GetMapping("/template/{userId}")
    public Map<String, Object> template(@PathVariable String userId) {
        String key = "template:" + userId;
        if (!rateLimiterTemplate.tryAcquire(key, 2, 60000L, 3)) {
            throw new RateLimitException(key, "template compatibility limited");
        }

        Map<String, Object> response = response("template");
        response.put("key", key);
        return response;
    }

    @GetMapping("/algorithm/{key}")
    @DoRateLimit(key = "#key", message = "algorithm compatibility limited")
    public Map<String, Object> algorithm(@PathVariable String key) {
        Map<String, Object> response = response("algorithm");
        response.put("key", key);
        response.put("algorithm", activeAlgorithm().name());
        response.put("storage", activeStorage().name());
        return response;
    }

    private RateLimitAlgorithm activeAlgorithm() {
        return rateLimitRegistry.getOptions().getAlgorithm();
    }

    private RateLimitStorage activeStorage() {
        return rateLimitRegistry.getOptions().getStorage();
    }

    private Map<String, Object> response(String scenario) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("scenario", scenario);
        return response;
    }
}
