package cn.clazs.qratelimiter.exception;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * 默认的限流异常处理器
 *
 * <p>这个类提供了一个默认的全局异常处理器，用于捕获和处理 {@link RateLimitException}。
 *
 * <p><b>注意：</b>此处理器仅在 Web 环境且 DispatcherServlet 存在时生效。
 *
 * @author clazs
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
@Order(1)  // 优先级最高，确保最先捕获
@ConditionalOnWebApplication
@ConditionalOnClass(DispatcherServlet.class)
public class DefaultRateLimitExceptionHandler {

    /**
     * 处理限流异常
     *
     * <p>当接口触发限流时，返回 HTTP 429 (Too Many Requests) 状态码，
     * 并只打印简洁的 WARN 日志，不必打印完整的异常堆栈
     *
     * @param e 限流异常
     * @return 429 状态码 + 错误信息
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitException(RateLimitException e) {
        // 只打印简洁的 WARN 日志，不打印堆栈
        log.warn("限流触发：key={}, message={}", e.getLimitKey(), e.getMessage());

        // 返回 429 状态码
        ErrorResponse response = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "TOO_MANY_REQUESTS",
                e.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(response);
    }

    /**
     * 错误响应结构
     */
    @Data
    public static class ErrorResponse {
        private int status;
        private String error;
        private String message;
        private String limitKey;

        public ErrorResponse(int status, String error, String message) {
            this.status = status;
            this.error = error;
            this.message = message;
        }

        @Override
        public String toString() {
            return "ErrorResponse{" +
                    "status=" + status +
                    ", error='" + error + '\'' +
                    ", message='" + message + '\'' +
                    '}';
        }
    }
}
