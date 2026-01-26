package cn.clazs.qratelimiter.core;

import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;

/**
 * 限流器统一接口
 *
 * <p>这是对外的统一门面，内部持有 LimiterExecutor 实例
 * 通过桥接模式将算法和存储解耦
 *
 * @author clazs
 * @since 1.0.0
 */
public interface RateLimiter {

    /**
     * 尝试获取许可（是否允许请求通过）
     *
     * @param key 限流键（用户ID、API标识等）
     * @param freq 时间窗口内最大请求次数
     * @param interval 时间窗口长度（毫秒）
     * @param capacity 容量（某些算法需要）
     * @return true-允许通过, false-被限流
     */
    boolean allowRequest(String key, int freq, long interval, int capacity);

    /**
     * 获取当前限流器使用的算法类型
     *
     * @return 算法类型枚举
     */
    RateLimitAlgorithm getAlgorithm();

    /**
     * 获取当前限流器使用的存储类型
     *
     * @return 存储类型枚举
     */
    RateLimitStorage getStorage();

    /**
     * 获取限流配置
     *
     * @return 限流配置对象
     */
    RateLimiterConfig getConfig();
}
