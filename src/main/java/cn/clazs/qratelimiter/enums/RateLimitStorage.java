package cn.clazs.qratelimiter.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 限流存储类型枚举
 *
 * @author clazs
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum RateLimitStorage {

    /**
     * 本地内存存储（基于 Caffeine Cache）
     */
    LOCAL("local", "本地内存"),

    /**
     * Redis 存储（基于 Redis ZSet/Hash + Lua 脚本）
     */
    REDIS("redis", "Redis");

    private final String code;
    private final String description;

    /**
     * 根据代码获取枚举值
     *
     * @param code 存储代码
     * @return 对应的存储枚举
     * @throws IllegalArgumentException 如果代码不存在
     */
    public static RateLimitStorage fromCode(String code) {
        for (RateLimitStorage storage : values()) {
            if (storage.code.equalsIgnoreCase(code)) {
                return storage;
            }
        }
        throw new IllegalArgumentException("Unknown storage code: " + code);
    }
}
