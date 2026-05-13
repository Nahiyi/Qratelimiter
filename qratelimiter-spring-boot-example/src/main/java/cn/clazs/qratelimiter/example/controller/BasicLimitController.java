package cn.clazs.qratelimiter.example.controller;

import cn.clazs.qratelimiter.annotation.DoRateLimit;
import cn.clazs.qratelimiter.example.model.DemoResponse;
import cn.clazs.qratelimiter.properties.RateLimiterProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/examples/basic")
public class BasicLimitController {

    private final RateLimiterProperties properties;

    public BasicLimitController(RateLimiterProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/users/{userId}")
    @DoRateLimit(key = "#userId", freq = 2, interval = 60000L, capacity = 3, message = "basic demo rate limited")
    public DemoResponse basicLimit(@PathVariable String userId) {
        return DemoResponse.of("basic", userId, "Two requests per minute per user", properties);
    }
}
