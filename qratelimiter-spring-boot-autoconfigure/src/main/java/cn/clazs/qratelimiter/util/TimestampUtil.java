package cn.clazs.qratelimiter.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 高性能时间戳验证工具类
 * 静态工具类，无需创建实例，线程安全
 */
public final class TimestampUtil {

    // 私有构造器防止实例化
    private TimestampUtil() {
        throw new AssertionError("工具类禁止实例化");
    }

    // 常量定义
    private static final long MILLISECONDS_IN_SECOND = 1000L;
    private static final long MILLISECONDS_IN_MINUTE = 60 * MILLISECONDS_IN_SECOND;
    private static final long MILLISECONDS_IN_HOUR = 60 * MILLISECONDS_IN_MINUTE;
    private static final long MILLISECONDS_IN_DAY = 24 * MILLISECONDS_IN_HOUR;
    private static final long MILLISECONDS_IN_YEAR = 365 * MILLISECONDS_IN_DAY;

    // 时间戳范围常量（毫秒）
    private static final long MIN_VALID_MILLIS = 0L;  // 1970-01-01
    private static final long MAX_VALID_MILLIS;      // 2100-01-01

    // 时间戳范围常量（秒）
    private static final long MIN_VALID_SECONDS;
    private static final long MAX_VALID_SECONDS;

    static {
        // 2100-01-01的毫秒时间戳
        MAX_VALID_MILLIS = 4102444800000L;

        // 计算秒级别的有效范围
        MIN_VALID_SECONDS = MIN_VALID_MILLIS / MILLISECONDS_IN_SECOND;
        MAX_VALID_SECONDS = MAX_VALID_MILLIS / MILLISECONDS_IN_SECOND;
    }

    /**
     * 判断时间戳是否为毫秒级别，高性能实现，避免不必要的对象创建
     */
    public static boolean isMillisecondTimestamp(long timestamp) {
        // 快速检查：如果在毫秒有效范围内
        if (timestamp >= MIN_VALID_MILLIS && timestamp <= MAX_VALID_MILLIS) {
            // 进一步验证：检查是否可能是秒时间戳
            return !isLikelySecondTimestamp(timestamp);
        }
        return false;
    }

    /**
     * 判断时间戳是否可能是秒级别
     */
    private static boolean isLikelySecondTimestamp(long timestamp) {
        // 如果乘以1000后仍在毫秒有效范围内，则可能是秒时间戳
        long asMillis = timestamp * MILLISECONDS_IN_SECOND;
        return asMillis >= MIN_VALID_MILLIS && asMillis <= MAX_VALID_MILLIS;
    }

    /**
     * 严格验证时间戳必须为毫秒级别
     * @throws IllegalArgumentException 如果是秒级别时间戳
     */
    public static void validateMillisecondTimestamp(long timestamp) {
        if (!isMillisecondTimestamp(timestamp)) {
            // 检查是否为秒级别
            if (isLikelySecondTimestamp(timestamp)) {
                throw new IllegalArgumentException(
                        String.format("时间戳为秒级别(%d), 请传入毫秒级别时间戳(%d)",
                                timestamp, timestamp * MILLISECONDS_IN_SECOND));
            } else {
                throw new IllegalArgumentException(
                        String.format("时间戳格式无效: %d, 请传入毫秒级别时间戳", timestamp));
            }
        }
    }

    /**
     * 转换秒时间戳为毫秒时间戳
     */
    public static long convertSecondsToMillis(long secondsTimestamp) {
        return secondsTimestamp * MILLISECONDS_IN_SECOND;
    }

    /**
     * 转换毫秒时间戳为秒时间戳
     */
    public static long convertMillisToSeconds(long millisTimestamp) {
        return millisTimestamp / MILLISECONDS_IN_SECOND;
    }

    /**
     * 获取当前时间的毫秒时间戳
     */
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 获取当前时间的秒时间戳
     */
    public static long currentTimeSeconds() {
        return System.currentTimeMillis() / MILLISECONDS_IN_SECOND;
    }

    /**
     * 判断时间戳是否为未来时间
     */
    public static boolean isFutureTimestamp(long millisTimestamp) {
        return millisTimestamp > System.currentTimeMillis();
    }

    /**
     * 判断时间戳是否为过去时间
     */
    public static boolean isPastTimestamp(long millisTimestamp) {
        return millisTimestamp < System.currentTimeMillis();
    }

    /**
     * 判断时间戳是否在最近N天内
     */
    public static boolean isWithinLastNDays(long millisTimestamp, int days) {
        long cutoffTime = System.currentTimeMillis() - (days * MILLISECONDS_IN_DAY);
        return millisTimestamp >= cutoffTime;
    }

    /**
     * 判断时间戳是否在最近N小时内
     */
    public static boolean isWithinLastNHours(long millisTimestamp, int hours) {
        long cutoffTime = System.currentTimeMillis() - (hours * MILLISECONDS_IN_HOUR);
        return millisTimestamp >= cutoffTime;
    }

    /**
     * 格式化毫秒时间戳为字符串(使用系统时区)
     */
    public static String formatMillis(long millisTimestamp, String pattern) {
        Instant instant = Instant.ofEpochMilli(millisTimestamp);
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 格式化毫秒时间戳为默认格式字符串
     */
    public static String formatMillis(long millisTimestamp) {
        validateMillisecondTimestamp(millisTimestamp);
        return formatMillis(millisTimestamp, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 计算两个时间戳之间的时间差（毫秒）
     */
    public static long timeDifferenceMillis(long timestamp1, long timestamp2) {
        return Math.abs(timestamp1 - timestamp2);
    }

    /**
     * 计算两个时间戳之间的时间差（秒）
     */
    public static long timeDifferenceSeconds(long timestamp1, long timestamp2) {
        return timeDifferenceMillis(timestamp1, timestamp2) / MILLISECONDS_IN_SECOND;
    }

    /**
     * 计算两个时间戳之间的时间差（分钟）
     */
    public static long timeDifferenceMinutes(long timestamp1, long timestamp2) {
        return timeDifferenceMillis(timestamp1, timestamp2) / MILLISECONDS_IN_MINUTE;
    }

    /**
     * 计算两个时间戳之间的时间差（天）
     */
    public static long timeDifferenceDays(long timestamp1, long timestamp2) {
        return timeDifferenceMillis(timestamp1, timestamp2) / MILLISECONDS_IN_DAY;
    }
}
