package cn.clazs.qratelimiter.dashboard.autoconfigure;

import cn.clazs.qratelimiter.dashboard.web.RateLimiterDashboardController;
import cn.clazs.qratelimiter.dashboard.properties.RateLimiterDashboardProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RateLimiterDashboardAutoConfiguration")
class RateLimiterDashboardAutoConfigurationTest {

    @Test
    @DisplayName("dashboard properties are disabled by default and use isolated paths")
    void dashboardPropertiesAreDisabledByDefaultAndUseIsolatedPaths() {
        RateLimiterDashboardProperties properties = new RateLimiterDashboardProperties();

        assertFalse(properties.isEnabled());
        assertEquals("/qratelimiter/dashboard", properties.getBasePath());
        assertEquals("/qratelimiter", properties.getApiBasePath());
        assertEquals("QRateLimiter Dashboard", properties.getTitle());
    }

    @Test
    @DisplayName("dashboard controller exposes runtime config for frontend")
    void dashboardControllerExposesRuntimeConfigForFrontend() {
        RateLimiterDashboardProperties properties = new RateLimiterDashboardProperties();
        properties.setBasePath("/ops/limits");
        properties.setApiBasePath("/ops/qratelimiter");
        properties.setTitle("Limits Console");
        RateLimiterDashboardController controller = new RateLimiterDashboardController(properties);

        RateLimiterDashboardController.RuntimeConfig config = controller.runtimeConfig();

        assertEquals("/ops/limits", config.getBasePath());
        assertEquals("/ops/qratelimiter", config.getApiBasePath());
        assertEquals("Limits Console", config.getTitle());
    }

    @Test
    @DisplayName("dashboard controller serves packaged index resource")
    void dashboardControllerServesPackagedIndexResource() throws IOException {
        RateLimiterDashboardController controller =
                new RateLimiterDashboardController(new RateLimiterDashboardProperties());

        Resource index = controller.index().getBody();

        assertNotNull(index);
        assertTrue(index.exists());
        assertTrue(index.getFilename().endsWith("index.html"));
    }

    @Test
    @DisplayName("spring.factories registers dashboard auto-configuration for Spring Boot 2")
    void springFactoriesRegistersDashboardAutoConfigurationForBoot2() throws IOException {
        String factoriesPath = "META-INF/spring.factories";

        assertNotNull(getClass().getClassLoader().getResource(factoriesPath));

        String content = readResource(factoriesPath);
        assertTrue(content.contains(RateLimiterDashboardAutoConfiguration.class.getName()));
    }

    @Test
    @DisplayName("AutoConfiguration imports register dashboard auto-configuration for Spring Boot 3")
    void autoConfigurationImportsRegisterDashboardAutoConfigurationForBoot3() throws IOException {
        String importsPath = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

        assertNotNull(getClass().getClassLoader().getResource(importsPath));

        Set<String> autoConfigurations = readLines(importsPath);
        assertTrue(autoConfigurations.contains(RateLimiterDashboardAutoConfiguration.class.getName()));
    }

    private String readResource(String path) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream(path), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private Set<String> readLines(String path) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream(path), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .collect(Collectors.toSet());
        }
    }
}
