package cn.clazs.qratelimiter.example.controller;

import cn.clazs.qratelimiter.annotation.DoRateLimit;
import cn.clazs.qratelimiter.example.model.DemoRequest;
import cn.clazs.qratelimiter.example.model.DemoResponse;
import cn.clazs.qratelimiter.properties.RateLimiterProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/examples/spel")
public class SpelKeyController {

    private final RateLimiterProperties properties;

    public SpelKeyController(RateLimiterProperties properties) {
        this.properties = properties;
    }

    @PostMapping("/object")
    @DoRateLimit(key = "#request.userId + ':' + #request.apiType", freq = 1, interval = 60000L, capacity = 2,
            message = "spel object demo rate limited")
    public DemoResponse objectKey(@RequestBody DemoRequest request) {
        return DemoResponse.of("spel-object", request.getUserId() + ":" + request.getApiType(),
                "Key comes from request.userId and request.apiType", properties);
    }

    @GetMapping("/constant")
    @DoRateLimit(key = "'constant_api'", freq = 1, interval = 60000L, capacity = 2,
            message = "constant key demo rate limited")
    public DemoResponse constantKey() {
        return DemoResponse.of("spel-constant", "constant_api", "Constant keys share one quota", properties);
    }

    @GetMapping("/combined")
    @DoRateLimit(key = "#userId + ':' + #apiType", freq = 1, interval = 60000L, capacity = 2,
            message = "combined key demo rate limited")
    public DemoResponse combinedKey(@RequestParam String userId, @RequestParam String apiType) {
        return DemoResponse.of("spel-combined", userId + ":" + apiType,
                "Key comes from two request parameters", properties);
    }
}
