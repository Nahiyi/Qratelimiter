package cn.clazs.qratelimiter.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("DefaultRateLimitExceptionHandler 测试")
class DefaultRateLimitExceptionHandlerTest {

    @Test
    @DisplayName("限流响应应包含触发限流的完整 key")
    void shouldIncludeLimitKeyInErrorResponse() {
        DefaultRateLimitExceptionHandler handler = new DefaultRateLimitExceptionHandler();
        RateLimitException exception = new RateLimitException("user:123", "too many requests");

        ResponseEntity<DefaultRateLimitExceptionHandler.ErrorResponse> response =
                handler.handleRateLimitException(exception);

        assertEquals(429, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(429, response.getBody().getStatus());
        assertEquals("TOO_MANY_REQUESTS", response.getBody().getError());
        assertEquals("too many requests", response.getBody().getMessage());
        assertEquals("user:123", response.getBody().getLimitKey());
    }
}
