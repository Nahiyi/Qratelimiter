package cn.clazs.qratelimiter.properties;

import cn.clazs.qratelimiter.core.RateLimiterOptions;
import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;
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
 *     algorithm: sliding-window-log
 *     storage: local
 * </pre>
 *
 * @author clazs
 * @since 1.0.0
 */
@Component
@ConfigurationProperties(prefix = "clazs.ratelimiter")
public class RateLimiterProperties {

    /**
     * 是否启用限流器
     */
    private boolean enabled = true;

    /**
     * 主限流额度参数（默认：100）
     */
    private int freq = 100;

    /**
     * 时间基准参数，单位：毫秒（默认：60000ms = 1分钟）
     */
    private long interval = 60000L;

    /**
     * 容量或精度参数（默认：150）
     *
     * <p>滑动窗口日志中建议使用 {@code freq * 1.5} 作为环形缓冲区容量；
     * 在其他算法中则分别表示统计分片数、令牌桶容量或漏桶最大积压容量。
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
     * 限流算法类型（默认：滑动窗口日志算法）
     */
    private RateLimitAlgorithm algorithm = RateLimitAlgorithm.SLIDING_WINDOW_LOG;

    /**
     * 存储类型（默认：本地内存）
     */
    private RateLimitStorage storage = RateLimitStorage.LOCAL;

    /**
     * Redis 配置
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * Runtime management endpoint configuration.
     */
    private ManagementConfig management = new ManagementConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getFreq() {
        return freq;
    }

    public void setFreq(int freq) {
        this.freq = freq;
    }

    public long getInterval() {
        return interval;
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public long getCacheExpireAfterAccessMinutes() {
        return cacheExpireAfterAccessMinutes;
    }

    public void setCacheExpireAfterAccessMinutes(long cacheExpireAfterAccessMinutes) {
        this.cacheExpireAfterAccessMinutes = cacheExpireAfterAccessMinutes;
    }

    public long getCacheMaximumSize() {
        return cacheMaximumSize;
    }

    public void setCacheMaximumSize(long cacheMaximumSize) {
        this.cacheMaximumSize = cacheMaximumSize;
    }

    public RateLimitAlgorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(RateLimitAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    public RateLimitStorage getStorage() {
        return storage;
    }

    public void setStorage(RateLimitStorage storage) {
        this.storage = storage;
    }

    public RedisConfig getRedis() {
        return redis;
    }

    public void setRedis(RedisConfig redis) {
        this.redis = redis;
    }

    public ManagementConfig getManagement() {
        return management;
    }

    public void setManagement(ManagementConfig management) {
        this.management = management;
    }

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
        if (algorithm != null && algorithm.requiresCapacityAtLeastFreq() && capacity < freq) {
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
        if (algorithm == null) {
            throw new IllegalArgumentException("配置错误：algorithm 不能为 null");
        }
        if (storage == null) {
            throw new IllegalArgumentException("配置错误：storage 不能为 null");
        }
    }

    /**
     * 获取配置摘要信息（用于日志输出）
     */
    public String getSummary() {
        return String.format(
                "RateLimiterProperties{enabled=%s, freq=%d, interval=%dms, capacity=%d, " +
                        "algorithm=%s, storage=%s, cacheExpireAfterAccessMinutes=%d, cacheMaximumSize=%d}",
                enabled, freq, interval, capacity, algorithm, storage,
                cacheExpireAfterAccessMinutes, cacheMaximumSize
        );
    }

    /**
     * Convert Spring Boot configuration properties to the Spring-independent core options.
     */
    public RateLimiterOptions toOptions() {
        return RateLimiterOptions.builder()
                .freq(freq)
                .interval(interval)
                .capacity(capacity)
                .algorithm(algorithm)
                .storage(storage)
                .cacheExpireAfterAccessMinutes(cacheExpireAfterAccessMinutes)
                .cacheMaximumSize(cacheMaximumSize)
                .build();
    }

    /**
     * Redis 配置类
     */
    public static class RedisConfig {
        /**
         * Redis 键前缀（默认：qratelimiter:）
         */
        private String keyPrefix = "qratelimiter:";

        /**
         * Lua 脚本位置（默认：classpath:redis/）
         */
        private String scriptLocation = "classpath:redis/";

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public String getScriptLocation() {
            return scriptLocation;
        }

        public void setScriptLocation(String scriptLocation) {
            this.scriptLocation = scriptLocation;
        }
    }

    public static class ManagementConfig {
        private boolean enabled = false;
        private String basePath = "/qratelimiter";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }
    }
}
