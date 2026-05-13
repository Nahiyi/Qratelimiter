package cn.clazs.qratelimiter.example.controller;

import cn.clazs.qratelimiter.annotation.DoRateLimit;
import cn.clazs.qratelimiter.example.model.DemoResponse;
import cn.clazs.qratelimiter.properties.RateLimiterProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/examples/redis")
public class RedisDemoController {

    private final RateLimiterProperties properties;

    public RedisDemoController(RateLimiterProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/users/{userId}")
    @DoRateLimit(key = "#userId", freq = 2, interval = 60000L, capacity = 3, message = "redis profile demo rate limited")
    public DemoResponse redisProfile(@PathVariable String userId) {
        return DemoResponse.of("redis-profile", userId, "Use the redis profile to back this endpoint with Redis", properties);
    }
}
