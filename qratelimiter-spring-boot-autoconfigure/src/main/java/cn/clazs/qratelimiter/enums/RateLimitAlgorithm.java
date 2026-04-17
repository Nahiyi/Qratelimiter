package cn.clazs.qratelimiter.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 限流算法类型枚举
 *
 * @author clazs
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum RateLimitAlgorithm {

    /**
     * 滑动窗口日志算法（基于环形数组 + 二分查找）
     * 当前版本的核心实现算法
     */
    SLIDING_WINDOW_LOG("sliding-window-log", "滑动窗口日志", 1),

    /**
     * 窗口计数器算法
     */
    SLIDING_WINDOW_COUNTER("sliding-window-counter", "滑动窗口计数器", 2),

    /**
     * 令牌桶算法
     */
    TOKEN_BUCKET("token-bucket", "令牌桶", 3),

    /**
     * 漏桶算法
     */
    LEAKY_BUCKET("leaky-bucket", "漏桶", 4);

    private final String code;
    private final String description;
    private final int priority;

    /**
     * 根据代码获取枚举值
     *
     * @param code 算法代码
     * @return 对应的算法枚举
     * @throws IllegalArgumentException 如果代码不存在
     */
    public static RateLimitAlgorithm fromCode(String code) {
        for (RateLimitAlgorithm algorithm : values()) {
            if (algorithm.code.equalsIgnoreCase(code)) {
                return algorithm;
            }
        }
        throw new IllegalArgumentException("Unknown algorithm code: " + code);
    }

    /**
     * 是否要求 capacity 必须大于等于 freq
     *
     * <p>当前只有滑动窗口日志算法依赖该约束来保证时间戳缓冲区能够容纳完整窗口内的请求数
     * 其他算法中的 capacity 表示桶容量或统计精度，不应强制绑定到 freq
     */
    public boolean requiresCapacityAtLeastFreq() {
        return this == SLIDING_WINDOW_LOG;
    }
}
