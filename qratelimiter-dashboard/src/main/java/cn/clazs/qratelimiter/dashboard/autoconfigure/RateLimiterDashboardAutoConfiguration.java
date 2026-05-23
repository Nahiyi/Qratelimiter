package cn.clazs.qratelimiter.dashboard.autoconfigure;

import cn.clazs.qratelimiter.dashboard.properties.RateLimiterDashboardProperties;
import cn.clazs.qratelimiter.dashboard.web.RateLimiterDashboardController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration for the optional QRateLimiter dashboard module.
 */
@Configuration
@ConditionalOnClass(DispatcherServlet.class)
@EnableConfigurationProperties(RateLimiterDashboardProperties.class)
@ConditionalOnProperty(prefix = "clazs.ratelimiter.dashboard", name = "enabled", havingValue = "true")
public class RateLimiterDashboardAutoConfiguration {

    private static final String STATIC_RESOURCE_LOCATION = "classpath:/META-INF/resources/qratelimiter-dashboard/";

    @Bean
    @ConditionalOnMissingBean
    public RateLimiterDashboardController rateLimiterDashboardController(
            RateLimiterDashboardProperties properties) {
        return new RateLimiterDashboardController(properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "rateLimiterDashboardResourceHandler")
    public WebMvcConfigurer rateLimiterDashboardResourceHandler(RateLimiterDashboardProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                String basePath = normalizeBasePath(properties.getBasePath());
                registry.addResourceHandler(basePath + "/assets/**")
                        .addResourceLocations(STATIC_RESOURCE_LOCATION + "assets/");
                registry.addResourceHandler(basePath + "/favicon.svg")
                        .addResourceLocations(STATIC_RESOURCE_LOCATION);
            }
        };
    }

    private String normalizeBasePath(String basePath) {
        if (basePath == null || basePath.trim().isEmpty()) {
            return "/qratelimiter/dashboard";
        }
        String normalized = basePath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
