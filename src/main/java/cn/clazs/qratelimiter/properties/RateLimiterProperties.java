package cn.clazs.qratelimiter.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 限流器配置属性类
 * 用于映射 application.yml 中的配置
 *
 * <p>YAML 配置示例：
 * <pre>
 * clazs:
 *   ratelimiter:
 *     enabled: true
 *     freq: 100
 *     interval: 60000
 *     capacity: 150
 * </pre>
 *
 * @author clazs
 * @since 1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "clazs.ratelimiter")
public class RateLimiterProperties {

    /**
     * 是否启用限流器
     */
    private boolean enabled = true;

    /**
     * 时间窗口内最大允许访问次数（默认：100次）
     */
    private int freq = 100;

    /**
     * 时间窗口长度，单位：毫秒（默认：60000ms = 1分钟）
     */
    private long interval = 60000L;

    /**
     * 数组容量（建议：freq * 1.5）
     * 必须满足：capacity >= freq，否则会抛出异常
     */
    private int capacity = 150;

    /**
     * 缓存过期时间，单位：分钟（默认：1440）
     * 用户在指定时间内没有访问后，其限流器实例会自动从内存中清除
     */
    private long cacheExpireAfterAccessMinutes = 1440L;

    /**
     * 缓存最大用户数（默认：10000）
     * 防止恶意攻击导致内存溢出
     */
    private long cacheMaximumSize = 10000L;

    /**
     * 验证配置参数的合法性
     *
     * @throws IllegalArgumentException 如果配置不合法
     */
    public void validate() {
        if (freq <= 0) {
            throw new IllegalArgumentException("配置错误：freq 必须大于 0，当前值：" + freq);
        }
        if (interval <= 0) {
            throw new IllegalArgumentException("配置错误：interval 必须大于 0，当前值：" + interval);
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("配置错误：capacity 必须大于 0，当前值：" + capacity);
        }
        if (capacity < freq) {
            throw new IllegalArgumentException(
                    "配置错误：capacity 必须大于等于 freq，" +
                            "否则限流器无法正常工作！当前值：capacity=" + capacity + ", freq=" + freq
            );
        }
        if (cacheExpireAfterAccessMinutes <= 0) {
            throw new IllegalArgumentException("配置错误：cacheExpireAfterAccessMinutes 必须大于 0");
        }
        if (cacheMaximumSize <= 0) {
            throw new IllegalArgumentException("配置错误：cacheMaximumSize 必须大于 0");
        }
    }

    /**
     * 获取配置摘要信息（用于日志输出）
     */
    public String getSummary() {
        return String.format(
                "RateLimiterProperties{enabled=%s, freq=%d, interval=%dms, capacity=%d, " +
                        "cacheExpireAfterAccessMinutes=%d, cacheMaximumSize=%d}",
                enabled, freq, interval, capacity, cacheExpireAfterAccessMinutes, cacheMaximumSize
        );
    }
}
