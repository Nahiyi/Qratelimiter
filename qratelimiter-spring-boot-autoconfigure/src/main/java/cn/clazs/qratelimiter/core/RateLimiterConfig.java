package cn.clazs.qratelimiter.core;

import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;
import lombok.Data;

import java.util.Objects;

/**
 * 限流器配置类
 *
 * @author clazs
 * @since 1.0.0
 */
@Data
public class RateLimiterConfig {

    /**
     * 算法类型
     */
    private RateLimitAlgorithm algorithm = RateLimitAlgorithm.SLIDING_WINDOW_LOG;

    /**
     * 存储类型
     */
    private RateLimitStorage storage = RateLimitStorage.LOCAL;

    /**
     * 主限流额度参数：
     * <ul>
     *     <li>滑动窗口类算法：窗口内允许的最大请求次数</li>
     *     <li>令牌桶：每个 interval 生成的令牌数</li>
     *     <li>漏桶：每个 interval 泄放的请求数</li>
     * </ul>
     */
    private int freq = 100;

    /**
     * 时间基准参数（毫秒）：
     * <ul>
     *     <li>滑动窗口类算法：统计窗口长度</li>
     *     <li>令牌桶：令牌补充周期</li>
     *     <li>漏桶：桶内请求泄放周期</li>
     * </ul>
     */
    private long interval = 60000L;

    /**
     * 容量或精度参数：
     * <ul>
     *     <li>滑动窗口日志：环形缓冲区容量，必须 >= freq</li>
     *     <li>滑动窗口计数器：时间分片数量</li>
     *     <li>令牌桶：桶容量</li>
     *     <li>漏桶：最大积压容量</li>
     * </ul>
     */
    private int capacity = 150;

    /**
     * 构建器模式
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder 类
     */
    public static class Builder {
        private final RateLimiterConfig config = new RateLimiterConfig();

        public Builder algorithm(RateLimitAlgorithm algorithm) {
            config.algorithm = Objects.requireNonNull(algorithm, "algorithm cannot be null");
            return this;
        }

        public Builder storage(RateLimitStorage storage) {
            config.storage = Objects.requireNonNull(storage, "storage cannot be null");
            return this;
        }

        public Builder freq(int freq) {
            config.freq = freq;
            return this;
        }

        public Builder interval(long interval) {
            config.interval = interval;
            return this;
        }

        public Builder capacity(int capacity) {
            config.capacity = capacity;
            return this;
        }

        /**
         * 构建配置对象
         *
         * @return 配置对象
         * @throws IllegalArgumentException 如果参数不合法
         */
        public RateLimiterConfig build() {
            // 参数校验
            if (config.freq <= 0) {
                throw new IllegalArgumentException("freq must be > 0");
            }
            if (config.interval <= 0) {
                throw new IllegalArgumentException("interval must be > 0");
            }
            if (config.algorithm.requiresCapacityAtLeastFreq() && config.capacity < config.freq) {
                throw new IllegalArgumentException("capacity must be >= freq");
            }
            return config;
        }
    }
}
