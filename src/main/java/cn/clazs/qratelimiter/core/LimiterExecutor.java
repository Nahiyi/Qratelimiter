package cn.clazs.qratelimiter.core;

/**
 * 限流执行器接口
 *
 * <p>这是桥接模式的核心接口，定义了"如何在特定存储介质上执行限流算法"
 * 每个实现类代表一种"算法+存储"的组合
 *
 * <p>设计原则：
 * <ul>
 *   <li>无状态：所有状态通过存储介质管理（内存/Redis）</li>
 *   <li>线程安全：实现类必须保证并发安全</li>
 *   <li>原子性：tryAcquire 操作必须是原子的</li>
 * </ul>
 *
 * @author clazs
 * @since 1.0.0
 */
public interface LimiterExecutor {

    /**
     * 尝试获取许可
     *
     * @param key 限流键（如：user:123, api:send_sms）
     * @param freq 时间窗口内最大请求次数
     * @param interval 时间窗口长度（毫秒）
     * @param capacity 容量（某些算法需要）
     * @return true-允许通过, false-被限流
     */
    boolean tryAcquire(String key, int freq, long interval, int capacity);

    /**
     * 获取当前存储的请求数量（可选实现，用于监控）
     *
     * @param key 限流键
     * @return 当前请求数量，如果不支持返回 -1
     */
    default int getCurrentCount(String key) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support getCurrentCount()");
    }

    /**
     * 重置指定 key 的限流状态（可选实现，用于测试）
     *
     * @param key 限流键
     */
    default void reset(String key) {
        // 默认不实现
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support reset()");
    }
}
