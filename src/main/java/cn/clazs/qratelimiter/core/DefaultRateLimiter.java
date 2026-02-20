package cn.clazs.qratelimiter.core;

import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认限流器实现（门面模式）
 *
 * <p>这是桥接模式中的"RefinedAbstraction"角色
 * 内部持有 LimiterExecutor 实例，将请求委托给执行器
 *
 * @author clazs
 * @since 1.0.0
 */
@Slf4j
public class DefaultRateLimiter implements RateLimiter {

    private final LimiterExecutor executor;
    private final RateLimiterConfig config;
    private final String key;

    /**
     * 构造函数
     *
     * @param executor 执行器实例
     * @param key 限流键
     * @param config 限流配置
     */
    public DefaultRateLimiter(LimiterExecutor executor, String key, RateLimiterConfig config) {
        this.executor = executor;
        this.key = key;
        this.config = config;
    }

    @Override
    public boolean allowRequest(String key, int freq, long interval, int capacity) {
        boolean allowed = executor.tryAcquire(key, freq, interval, capacity);

        if (!allowed) {
            log.debug("限流触发: key={}, algorithm={}, storage={}",
                    this.key, getAlgorithm(), getStorage());
        }

        return allowed;
    }

    @Override
    public RateLimitAlgorithm getAlgorithm() {
        return config.getAlgorithm();
    }

    @Override
    public RateLimitStorage getStorage() {
        return config.getStorage();
    }

    @Override
    public RateLimiterConfig getConfig() {
        return config;
    }

    public String getKey() {
        return key;
    }
}
