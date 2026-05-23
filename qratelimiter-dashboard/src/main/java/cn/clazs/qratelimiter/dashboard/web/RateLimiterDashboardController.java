package cn.clazs.qratelimiter.dashboard.web;

import cn.clazs.qratelimiter.dashboard.properties.RateLimiterDashboardProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Serves the optional dashboard shell and its runtime frontend configuration.
 */
@Controller
public class RateLimiterDashboardController {

    private static final String INDEX_RESOURCE = "META-INF/resources/qratelimiter-dashboard/index.html";

    private final RateLimiterDashboardProperties properties;

    public RateLimiterDashboardController(RateLimiterDashboardProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties cannot be null");
        }
        this.properties = properties;
    }

    @GetMapping({
            "${clazs.ratelimiter.dashboard.base-path:/qratelimiter/dashboard}",
            "${clazs.ratelimiter.dashboard.base-path:/qratelimiter/dashboard}/",
            "${clazs.ratelimiter.dashboard.base-path:/qratelimiter/dashboard}/index.html"
    })
    public ResponseEntity<String> index() throws IOException {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noCache())
                .body(indexHtml());
    }

    @GetMapping(
            value = "${clazs.ratelimiter.dashboard.base-path:/qratelimiter/dashboard}/runtime-config.js",
            produces = "application/javascript"
    )
    @ResponseBody
    public ResponseEntity<String> runtimeConfigScript() {
        RuntimeConfig config = runtimeConfig();
        String script = "window.__QRATELIMITER_DASHBOARD_CONFIG__ = {"
                + "basePath:\"" + escape(config.getBasePath()) + "\","
                + "apiBasePath:\"" + escape(config.getApiBasePath()) + "\","
                + "title:\"" + escape(config.getTitle()) + "\""
                + "};";
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "javascript"))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS))
                .body(script);
    }

    public RuntimeConfig runtimeConfig() {
        return new RuntimeConfig(properties.getBasePath(), properties.getApiBasePath(), properties.getTitle());
    }

    private String indexHtml() throws IOException {
        ClassPathResource resource = new ClassPathResource(INDEX_RESOURCE);
        String html = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        String basePath = properties.getBasePath();
        return html.replace("./runtime-config.js", basePath + "/runtime-config.js")
                .replace("./assets/", basePath + "/assets/");
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static class RuntimeConfig {
        private final String basePath;
        private final String apiBasePath;
        private final String title;

        public RuntimeConfig(String basePath, String apiBasePath, String title) {
            this.basePath = basePath;
            this.apiBasePath = apiBasePath;
            this.title = title;
        }

        public String getBasePath() {
            return basePath;
        }

        public String getApiBasePath() {
            return apiBasePath;
        }

        public String getTitle() {
            return title;
        }
    }
}
