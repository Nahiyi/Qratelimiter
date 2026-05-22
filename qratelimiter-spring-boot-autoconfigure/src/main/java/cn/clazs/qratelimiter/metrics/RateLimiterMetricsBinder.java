package cn.clazs.qratelimiter.metrics;

import cn.clazs.qratelimiter.management.RateLimitSnapshot;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Low-cardinality Micrometer metrics for QRateLimiter.
 */
public class RateLimiterMetricsBinder implements MeterBinder {

    private final RateLimitRegistry registry;

    public RateLimiterMetricsBinder(RateLimitRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry cannot be null");
        }
        this.registry = registry;
    }

    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        Gauge.builder("qratelimiter.limiters.cache.size", registry, RateLimitRegistry::getCurrentCacheSize)
                .description("Current number of cached QRateLimiter limiters")
                .register(meterRegistry);

        Gauge.builder("qratelimiter.limiters.created.total", registry, RateLimitRegistry::getTotalCreatedLimiters)
                .description("Total number of QRateLimiter limiters created")
                .register(meterRegistry);

        FunctionCounter.builder("qratelimiter.requests.allowed", registry,
                        value -> value.snapshot().getAllowedRequests())
                .description("Total allowed QRateLimiter requests")
                .register(meterRegistry);

        FunctionCounter.builder("qratelimiter.requests.rejected", registry,
                        value -> value.snapshot().getRejectedRequests())
                .description("Total rejected QRateLimiter requests")
                .register(meterRegistry);
    }
}
