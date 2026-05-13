package cn.clazs.qratelimiter.example.controller;

import cn.clazs.qratelimiter.annotation.DoRateLimit;
import cn.clazs.qratelimiter.annotation.RateLimitScope;
import cn.clazs.qratelimiter.example.model.DemoResponse;
import cn.clazs.qratelimiter.properties.RateLimiterProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/examples/scope")
public class ScopeLimitController {

    private final RateLimiterProperties properties;

    public ScopeLimitController(RateLimiterProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/method-a/{userId}")
    @DoRateLimit(key = "#userId", scope = RateLimitScope.METHOD, freq = 1, interval = 60000L, capacity = 2,
            message = "method scope demo rate limited")
    public DemoResponse methodA(@PathVariable String userId) {
        return DemoResponse.of("method-a", userId, "METHOD scope isolates this method", properties);
    }

    @GetMapping("/method-b/{userId}")
    @DoRateLimit(key = "#userId", scope = RateLimitScope.METHOD, freq = 1, interval = 60000L, capacity = 2,
            message = "method scope demo rate limited")
    public DemoResponse methodB(@PathVariable String userId) {
        return DemoResponse.of("method-b", userId, "METHOD scope gives this method a separate quota", properties);
    }

    @GetMapping("/global-a/{userId}")
    @DoRateLimit(key = "#userId", scope = RateLimitScope.GLOBAL, freq = 1, interval = 60000L, capacity = 2,
            message = "global scope demo rate limited")
    public DemoResponse globalA(@PathVariable String userId) {
        return DemoResponse.of("global-a", userId, "GLOBAL scope shares this user quota", properties);
    }

    @GetMapping("/global-b/{userId}")
    @DoRateLimit(key = "#userId", scope = RateLimitScope.GLOBAL, freq = 1, interval = 60000L, capacity = 2,
            message = "global scope demo rate limited")
    public DemoResponse globalB(@PathVariable String userId) {
        return DemoResponse.of("global-b", userId, "GLOBAL scope reuses the same user quota", properties);
    }
}
