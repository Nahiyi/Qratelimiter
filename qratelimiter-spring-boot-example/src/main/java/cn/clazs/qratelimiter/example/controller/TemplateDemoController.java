package cn.clazs.qratelimiter.example.controller;

import cn.clazs.qratelimiter.core.RateLimiterTemplate;
import cn.clazs.qratelimiter.example.model.DemoResponse;
import cn.clazs.qratelimiter.exception.RateLimitException;
import cn.clazs.qratelimiter.properties.RateLimiterProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/examples/template")
public class TemplateDemoController {

    private final RateLimiterTemplate rateLimiterTemplate;
    private final RateLimiterProperties properties;

    public TemplateDemoController(RateLimiterTemplate rateLimiterTemplate,
                                  RateLimiterProperties properties) {
        this.rateLimiterTemplate = rateLimiterTemplate;
        this.properties = properties;
    }

    @GetMapping("/users/{userId}")
    public DemoResponse templateLimit(@PathVariable String userId) {
        String key = "template:" + userId;
        if (!rateLimiterTemplate.tryAcquire(key, 2, 60000L, 3)) {
            throw new RateLimitException(key, "template demo rate limited");
        }
        return DemoResponse.of("template", key, "Programmatic RateLimiterTemplate usage", properties);
    }
}
