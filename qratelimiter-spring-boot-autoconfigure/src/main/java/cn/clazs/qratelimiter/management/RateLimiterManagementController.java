package cn.clazs.qratelimiter.management;

import cn.clazs.qratelimiter.core.RateLimiterOptions;
import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;
import cn.clazs.qratelimiter.management.RateLimitRefreshStrategy;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Optional runtime management endpoints for QRateLimiter.
 */
@RestController
@RequestMapping("${clazs.ratelimiter.management.base-path:/qratelimiter}")
public class RateLimiterManagementController {

    private final RateLimitRegistry registry;

    public RateLimiterManagementController(RateLimitRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry cannot be null");
        }
        this.registry = registry;
    }

    @GetMapping("/stats")
    public RateLimitSnapshot stats() {
        return registry.snapshot();
    }

    @GetMapping("/config")
    public RateLimiterOptions config() {
        return registry.getOptions();
    }

    @PostMapping("/config")
    public RateLimiterOptions refresh(@RequestBody RefreshRequest request) {
        RateLimiterOptions current = registry.getOptions();
        RateLimiterOptions.Builder builder = RateLimiterOptions.builder()
                .freq(request.freq > 0 ? request.freq : current.getFreq())
                .interval(request.interval > 0 ? request.interval : current.getInterval())
                .capacity(request.capacity > 0 ? request.capacity : current.getCapacity())
                .cacheExpireAfterAccessMinutes(current.getCacheExpireAfterAccessMinutes())
                .cacheMaximumSize(current.getCacheMaximumSize())
                .algorithm(request.algorithm != null ? request.algorithm : current.getAlgorithm())
                .storage(request.storage != null ? request.storage : current.getStorage());
        RateLimiterOptions refreshed = builder.build();
        RateLimitRefreshStrategy strategy = request.strategy == null
                ? RateLimitRefreshStrategy.APPLY_TO_NEW_LIMITERS_ONLY
                : request.strategy;
        registry.refreshOptions(refreshed, strategy);
        return registry.getOptions();
    }

    @DeleteMapping("/cache")
    public void resetAll() {
        registry.clearAll();
    }

    @DeleteMapping("/cache/{key}")
    public void reset(@PathVariable String key) {
        registry.removeLimiter(key);
    }

    public static class RefreshRequest {
        private int freq;
        private long interval;
        private int capacity;
        private RateLimitAlgorithm algorithm;
        private RateLimitStorage storage;
        private RateLimitRefreshStrategy strategy;

        public int getFreq() {
            return freq;
        }

        public void setFreq(int freq) {
            this.freq = freq;
        }

        public long getInterval() {
            return interval;
        }

        public void setInterval(long interval) {
            this.interval = interval;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public RateLimitAlgorithm getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(RateLimitAlgorithm algorithm) {
            this.algorithm = algorithm;
        }

        public RateLimitStorage getStorage() {
            return storage;
        }

        public void setStorage(RateLimitStorage storage) {
            this.storage = storage;
        }

        public RateLimitRefreshStrategy getStrategy() {
            return strategy;
        }

        public void setStrategy(RateLimitRefreshStrategy strategy) {
            this.strategy = strategy;
        }
    }
}
