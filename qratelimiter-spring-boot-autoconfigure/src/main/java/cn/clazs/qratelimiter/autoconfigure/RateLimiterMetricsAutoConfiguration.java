package cn.clazs.qratelimiter.autoconfigure;

import cn.clazs.qratelimiter.metrics.RateLimiterMetricsBinder;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer metrics auto-configuration for QRateLimiter.
 */
@Configuration
@ConditionalOnClass({MeterRegistry.class, MeterBinder.class})
@ConditionalOnBean(RateLimitRegistry.class)
@AutoConfigureAfter(RateLimiterAutoConfiguration.class)
public class RateLimiterMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "rateLimiterMetricsBinder")
    public MeterBinder rateLimiterMetricsBinder(RateLimitRegistry registry) {
        return new RateLimiterMetricsBinder(registry);
    }
}
