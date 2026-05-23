package cn.clazs.qratelimiter.dashboard.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional dashboard configuration.
 */
@ConfigurationProperties(prefix = "clazs.ratelimiter.dashboard")
public class RateLimiterDashboardProperties {

    private boolean enabled = false;
    private String basePath = "/qratelimiter/dashboard";
    private String apiBasePath = "/qratelimiter";
    private String title = "QRateLimiter Dashboard";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = normalizePath(basePath, "/qratelimiter/dashboard");
    }

    public String getApiBasePath() {
        return apiBasePath;
    }

    public void setApiBasePath(String apiBasePath) {
        this.apiBasePath = normalizePath(apiBasePath, "/qratelimiter");
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title != null && !title.trim().isEmpty()) {
            this.title = title.trim();
        }
    }

    private String normalizePath(String path, String fallback) {
        if (path == null || path.trim().isEmpty()) {
            return fallback;
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
