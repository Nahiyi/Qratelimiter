package cn.clazs.qratelimiter.executor.redis;

import cn.clazs.qratelimiter.core.LimiterExecutor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Redis 滑动窗口计数器执行器。
 *
 * <p>使用两个连续整窗口计数近似滑动窗口，并通过加权的上一窗口计数估算当前滑动窗口内的请求数。
 * 对外仍使用统一的 {@code freq / interval / capacity} 参数语义，其中 {@code capacity} 作为本地实现的精度参数，
 * Redis 实现中保留该入参以维持公共接口一致，但不额外切分更多分片。
 */
public class RedisSlidingWindowCounterExecutor implements LimiterExecutor {

    private static final String DEFAULT_KEY_PREFIX = "qratelimiter:";
    private static final String SCRIPT_PATH = "redis/sliding_window_counter.lua";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> slidingWindowCounterScript;
    private final String keyPrefix;

    public RedisSlidingWindowCounterExecutor(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_KEY_PREFIX);
    }

    public RedisSlidingWindowCounterExecutor(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.slidingWindowCounterScript = new DefaultRedisScript<>();
        this.slidingWindowCounterScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(SCRIPT_PATH)));
        this.slidingWindowCounterScript.setResultType(Long.class);
    }

    @Override
    public boolean tryAcquire(String key, int freq, long interval, int capacity) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Key cannot be blank");
        }
        if (freq <= 0) {
            throw new IllegalArgumentException("Frequency must be positive, got: " + freq);
        }
        if (interval <= 0) {
            throw new IllegalArgumentException("Interval must be positive, got: " + interval);
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive, got: " + capacity);
        }

        long currentTime = System.currentTimeMillis();
        long currentWindowStart = alignWindowStart(currentTime, interval);
        long previousWindowStart = currentWindowStart - interval;
        long elapsedInCurrentWindow = currentTime - currentWindowStart;
        double previousWindowWeight = 1D - ((double) elapsedInCurrentWindow / (double) interval);
        long expireMillis = Math.max(interval * 2L, interval + 60_000L);

        List<String> keys = Arrays.asList(
                buildRedisKey(key, currentWindowStart),
                buildRedisKey(key, previousWindowStart)
        );

        Long result = redisTemplate.execute(
                slidingWindowCounterScript,
                keys,
                String.valueOf(previousWindowWeight),
                String.valueOf(freq),
                String.valueOf(expireMillis)
        );

        return result != null && result == 1L;
    }

    @Override
    public int getCurrentCount(String key) {
        throw new UnsupportedOperationException(
                "RedisSlidingWindowCounterExecutor does not support precise getCurrentCount() without interval context."
        );
    }

    @Override
    public void reset(String key) {
        Set<String> keys = redisTemplate.keys(keyPrefix + "sliding_window_counter:" + key + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private long alignWindowStart(long currentTime, long interval) {
        return (currentTime / interval) * interval;
    }

    private String buildRedisKey(String key, long windowStart) {
        return keyPrefix + "sliding_window_counter:" + key + ":" + windowStart;
    }
}
