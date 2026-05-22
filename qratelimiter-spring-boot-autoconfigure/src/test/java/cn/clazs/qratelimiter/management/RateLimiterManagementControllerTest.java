package cn.clazs.qratelimiter.management;

import cn.clazs.qratelimiter.core.RateLimiter;
import cn.clazs.qratelimiter.core.RateLimiterOptions;
import cn.clazs.qratelimiter.factory.LimiterExecutorFactory;
import cn.clazs.qratelimiter.properties.RateLimiterProperties;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RateLimiterManagementController")
class RateLimiterManagementControllerTest {

    @Test
    @DisplayName("management properties are disabled by default")
    void managementPropertiesAreDisabledByDefault() {
        RateLimiterProperties properties = new RateLimiterProperties();

        assertFalse(properties.getManagement().isEnabled());
        assertEquals("/qratelimiter", properties.getManagement().getBasePath());
    }

    @Test
    @DisplayName("stats endpoint returns registry snapshot")
    void statsEndpointReturnsRegistrySnapshot() {
        RateLimitRegistry registry = newRegistry();
        RateLimiter limiter = registry.getLimiter("api:stats");
        limiter.allowRequest("api:stats", 1, 60_000L, 2);
        limiter.allowRequest("api:stats", 1, 60_000L, 2);
        RateLimiterManagementController controller = new RateLimiterManagementController(registry);

        RateLimitSnapshot snapshot = controller.stats();

        assertEquals(1L, snapshot.getCurrentCacheSize());
        assertEquals(1L, snapshot.getAllowedRequests());
        assertEquals(1L, snapshot.getRejectedRequests());
        assertEquals("api:stats", snapshot.getLimiters().get(0).getKey());
    }

    @Test
    @DisplayName("reset endpoint clears one key state")
    void resetEndpointClearsOneKeyState() {
        RateLimitRegistry registry = newRegistry();
        RateLimiter limiter = registry.getLimiter("api:reset");
        assertTrue(limiter.allowRequest("api:reset", 1, 60_000L, 2));
        assertFalse(limiter.allowRequest("api:reset", 1, 60_000L, 2));
        RateLimiterManagementController controller = new RateLimiterManagementController(registry);

        controller.reset("api:reset");

        assertTrue(registry.getLimiter("api:reset").allowRequest("api:reset", 1, 60_000L, 2));
    }

    @Test
    @DisplayName("config endpoint returns current registry defaults")
    void configEndpointReturnsCurrentRegistryDefaults() {
        RateLimiterManagementController controller = new RateLimiterManagementController(newRegistry());

        RateLimiterOptions options = controller.config();

        assertNotNull(options);
        assertEquals(1, options.getFreq());
        assertEquals(60_000L, options.getInterval());
        assertEquals(2, options.getCapacity());
    }

    @Test
    @DisplayName("refresh endpoint updates registry defaults with selected strategy")
    void refreshEndpointUpdatesRegistryDefaultsWithSelectedStrategy() {
        RateLimitRegistry registry = newRegistry();
        registry.getLimiter("refresh:old");
        RateLimiterManagementController controller = new RateLimiterManagementController(registry);
        RateLimiterManagementController.RefreshRequest request = new RateLimiterManagementController.RefreshRequest();
        request.setFreq(3);
        request.setInterval(60_000L);
        request.setCapacity(4);
        request.setAlgorithm("sliding-window-log");
        request.setStrategy(RateLimitRefreshStrategy.CLEAR_CACHE_AND_APPLY);

        RateLimiterOptions refreshed = controller.refresh(request);

        assertEquals(3, refreshed.getFreq());
        assertEquals(0L, registry.getCurrentCacheSize());
        assertEquals(3, registry.getLimiter("refresh:old").getConfig().getFreq());
    }

    @Test
    @DisplayName("refresh endpoint accepts enum-style algorithm names for compatibility")
    void refreshEndpointAcceptsEnumStyleAlgorithmNamesForCompatibility() {
        RateLimiterManagementController controller = new RateLimiterManagementController(newRegistry());
        RateLimiterManagementController.RefreshRequest request = new RateLimiterManagementController.RefreshRequest();
        request.setAlgorithm("TOKEN_BUCKET");
        request.setCapacity(4);

        RateLimiterOptions refreshed = controller.refresh(request);

        assertEquals("token-bucket", refreshed.getAlgorithm().getCode());
    }

    @Test
    @DisplayName("refresh endpoint treats null request as no-op refresh")
    void refreshEndpointTreatsNullRequestAsNoOpRefresh() {
        RateLimiterManagementController controller = new RateLimiterManagementController(newRegistry());

        RateLimiterOptions refreshed = controller.refresh(null);

        assertEquals(1, refreshed.getFreq());
        assertEquals(60_000L, refreshed.getInterval());
        assertEquals(2, refreshed.getCapacity());
    }

    @Test
    @DisplayName("refresh endpoint rejects invalid values as bad request")
    void refreshEndpointRejectsInvalidValuesAsBadRequest() {
        RateLimiterManagementController controller = new RateLimiterManagementController(newRegistry());
        RateLimiterManagementController.RefreshRequest request = new RateLimiterManagementController.RefreshRequest();
        request.setAlgorithm("not-a-real-algorithm");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.refresh(request));

        assertTrue(exception.getMessage().contains("400"));
    }

    private RateLimitRegistry newRegistry() {
        RateLimiterOptions options = RateLimiterOptions.builder()
                .freq(1)
                .interval(60_000L)
                .capacity(2)
                .cacheExpireAfterAccessMinutes(10L)
                .cacheMaximumSize(100L)
                .build();
        return new RateLimitRegistry(options, new LimiterExecutorFactory(options));
    }
}
