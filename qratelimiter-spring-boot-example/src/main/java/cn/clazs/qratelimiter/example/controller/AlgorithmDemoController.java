package cn.clazs.qratelimiter.example.controller;

import cn.clazs.qratelimiter.annotation.DoRateLimit;
import cn.clazs.qratelimiter.example.model.DemoResponse;
import cn.clazs.qratelimiter.properties.RateLimiterProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/examples/algorithms")
public class AlgorithmDemoController {

    private final RateLimiterProperties properties;

    public AlgorithmDemoController(RateLimiterProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/current/{key}")
    @DoRateLimit(key = "#key", freq = 2, interval = 60000L, capacity = 3, message = "algorithm demo rate limited")
    public DemoResponse currentAlgorithm(@PathVariable String key) {
        return DemoResponse.of("algorithm-current", key, "Active algorithm profile", properties);
    }
}
