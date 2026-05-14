package cn.clazs.qratelimiter.enums;

/**
 * 限流存储类型枚举
 *
 * @author clazs
 * @since 1.0.0
 */
public enum RateLimitStorage {

    /**
     * 本地内存存储（基于 Caffeine Cache）
     */
    LOCAL("local", "本地内存"),

    /**
     * Redis 存储（由 Spring Boot 集成层提供实现）
     */
    REDIS("redis", "Redis");

    private final String code;
    private final String description;

    RateLimitStorage(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static RateLimitStorage fromCode(String code) {
        for (RateLimitStorage storage : values()) {
            if (storage.code.equalsIgnoreCase(code)) {
                return storage;
            }
        }
        throw new IllegalArgumentException("Unknown storage code: " + code);
    }
}
