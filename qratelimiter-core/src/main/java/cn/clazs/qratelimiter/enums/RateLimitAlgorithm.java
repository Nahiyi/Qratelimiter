package cn.clazs.qratelimiter.enums;

/**
 * 限流算法类型枚举
 *
 * @author clazs
 * @since 1.0.0
 */
public enum RateLimitAlgorithm {

    /**
     * 滑动窗口日志算法（基于环形数组 + 二分查找）
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

    RateLimitAlgorithm(String code, String description, int priority) {
        this.code = code;
        this.description = description;
        this.priority = priority;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getPriority() {
        return priority;
    }

    public static RateLimitAlgorithm fromCode(String code) {
        for (RateLimitAlgorithm algorithm : values()) {
            if (algorithm.code.equalsIgnoreCase(code)) {
                return algorithm;
            }
        }
        throw new IllegalArgumentException("Unknown algorithm code: " + code);
    }

    /**
     * 是否要求 capacity 必须大于等于 freq。
     */
    public boolean requiresCapacityAtLeastFreq() {
        return this == SLIDING_WINDOW_LOG;
    }
}
