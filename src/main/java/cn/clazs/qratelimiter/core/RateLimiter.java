package cn.clazs.qratelimiter.core;

/**
 * 限流器核心接口
 * 定义限流器的标准行为，支持多态扩展（本地/分布式、滑动窗口/令牌桶等）
 *
 * @author clazs
 * @since 1.0
 */
public interface RateLimiter {

    /**
     * 判断是否允许请求
     *
     * @return true表示允许，false表示被限流
     */
    boolean allowRequest();

    /**
     * 获取限流器配置的频率
     */
    int getFreq();

    /**
     * 获取限流器配置的时间窗口（毫秒）
     */
    long getInterval();
}
