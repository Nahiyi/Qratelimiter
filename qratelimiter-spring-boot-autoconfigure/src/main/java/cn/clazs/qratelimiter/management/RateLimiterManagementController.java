package cn.clazs.qratelimiter.management;

import cn.clazs.qratelimiter.core.RateLimiterOptions;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
        try {
            RefreshRequest safeRequest = request == null ? new RefreshRequest() : request;
            RateLimiterOptions current = registry.getOptions();
            RateLimiterOptions.Builder builder = RateLimiterOptions.builder()
                    .freq(safeRequest.freq > 0 ? safeRequest.freq : current.getFreq())
                    .interval(safeRequest.interval > 0 ? safeRequest.interval : current.getInterval())
                    .capacity(safeRequest.capacity > 0 ? safeRequest.capacity : current.getCapacity())
                    .cacheExpireAfterAccessMinutes(current.getCacheExpireAfterAccessMinutes())
                    .cacheMaximumSize(current.getCacheMaximumSize())
                    .algorithm(safeRequest.resolveAlgorithm(current.getAlgorithm()))
                    .storage(safeRequest.resolveStorage(current.getStorage()));
            RateLimiterOptions refreshed = builder.build();
            RateLimitRefreshStrategy strategy = safeRequest.strategy == null
                    ? RateLimitRefreshStrategy.APPLY_TO_NEW_LIMITERS_ONLY
                    : safeRequest.strategy;
            registry.refreshOptions(refreshed, strategy);
            return registry.getOptions();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
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
        private String algorithm;
        private String storage;
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

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public String getStorage() {
            return storage;
        }

        public void setStorage(String storage) {
            this.storage = storage;
        }

        public RateLimitRefreshStrategy getStrategy() {
            return strategy;
        }

        public void setStrategy(RateLimitRefreshStrategy strategy) {
            this.strategy = strategy;
        }

        private cn.clazs.qratelimiter.enums.RateLimitAlgorithm resolveAlgorithm(
                cn.clazs.qratelimiter.enums.RateLimitAlgorithm current) {
            if (algorithm == null || algorithm.trim().isEmpty()) {
                return current;
            }
            String value = algorithm.trim();
            try {
                return cn.clazs.qratelimiter.enums.RateLimitAlgorithm.fromCode(value);
            } catch (IllegalArgumentException ignored) {
                try {
                    return cn.clazs.qratelimiter.enums.RateLimitAlgorithm.valueOf(value);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown rate limit algorithm: " + value);
                }
            }
        }

        private cn.clazs.qratelimiter.enums.RateLimitStorage resolveStorage(
                cn.clazs.qratelimiter.enums.RateLimitStorage current) {
            if (storage == null || storage.trim().isEmpty()) {
                return current;
            }
            String value = storage.trim();
            try {
                return cn.clazs.qratelimiter.enums.RateLimitStorage.fromCode(value);
            } catch (IllegalArgumentException ignored) {
                try {
                    return cn.clazs.qratelimiter.enums.RateLimitStorage.valueOf(value);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown rate limit storage: " + value);
                }
            }
        }
    }
}
