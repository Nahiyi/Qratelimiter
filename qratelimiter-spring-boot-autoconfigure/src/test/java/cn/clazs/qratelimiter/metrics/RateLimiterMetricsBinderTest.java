package cn.clazs.qratelimiter.metrics;

import cn.clazs.qratelimiter.core.RateLimiter;
import cn.clazs.qratelimiter.core.RateLimiterOptions;
import cn.clazs.qratelimiter.factory.LimiterExecutorFactory;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("RateLimiterMetricsBinder")
class RateLimiterMetricsBinderTest {

    @Test
    @DisplayName("binds registry gauges and request counters")
    void bindsRegistryGaugesAndRequestCounters() {
        RateLimiterOptions options = RateLimiterOptions.builder()
                .freq(1)
                .interval(60_000L)
                .capacity(2)
                .cacheExpireAfterAccessMinutes(10L)
                .cacheMaximumSize(100L)
                .build();
        RateLimitRegistry registry = new RateLimitRegistry(options, new LimiterExecutorFactory(options));
        RateLimiter limiter = registry.getLimiter("metrics:user");
        limiter.allowRequest("metrics:user", 1, 60_000L, 2);
        limiter.allowRequest("metrics:user", 1, 60_000L, 2);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        new RateLimiterMetricsBinder(registry).bindTo(meterRegistry);

        assertNotNull(meterRegistry.find("qratelimiter.limiters.cache.size").gauge());
        assertNotNull(meterRegistry.find("qratelimiter.limiters.created.total").gauge());
        assertEquals(1D, meterRegistry.find("qratelimiter.limiters.cache.size").gauge().value());
        assertEquals(1D, meterRegistry.find("qratelimiter.requests.allowed").functionCounter().count());
        assertEquals(1D, meterRegistry.find("qratelimiter.requests.rejected").functionCounter().count());
    }
}
