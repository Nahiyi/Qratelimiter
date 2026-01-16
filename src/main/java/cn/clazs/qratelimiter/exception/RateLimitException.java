package cn.clazs.qratelimiter.exception;

import lombok.Getter;

/**
 * 限流异常
 * 当请求超过限流阈值时抛出此异常
 *
 * <p>使用示例：
 * <pre>
 * try {
 *     // 业务代码
 * } catch (RateLimitException e) {
 *     log.warn("限流触发：{}", e.getMessage());
 *     return "请求过于频繁，请稍后再试";
 * }
 * </pre>
 *
 * @author clazs
 * @since 1.0
 */
public class RateLimitException extends RuntimeException {

    /**
     * 限流的 Key（用户ID、API标识等）
     */
    @Getter
    private final String limitKey;

    /**
     * @param limitKey 限流的 Key
     */
    public RateLimitException(String limitKey) {
        super("访问过于频繁，请稍后再试");
        this.limitKey = limitKey;
    }

    /**
     * @param limitKey 限流的 Key
     * @param message  错误提示信息
     */
    public RateLimitException(String limitKey, String message) {
        super(message);
        this.limitKey = limitKey;
    }

    @Override
    public String toString() {
        return String.format("RateLimitException{limitKey='%s', message='%s'}", limitKey, getMessage());
    }
}
