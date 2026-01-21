package cn.clazs.qratelimiter.core;

/**
 * 限流器工厂接口
 * 负责创建具体的限流器实例
 *
 * @author clazs
 * @since 1.0
 */
public interface RateLimiterFactory {
    /**
     * 创建限流器实例
     *
     * @param key      限流Key
     * @param freq     频率
     * @param interval 时间窗口
     * @param capacity 容量
     * @return 限流器实例
     */
    RateLimiter create(String key, int freq, long interval, int capacity);
}
