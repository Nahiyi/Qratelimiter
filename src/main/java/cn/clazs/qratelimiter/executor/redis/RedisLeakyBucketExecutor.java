package cn.clazs.qratelimiter.executor.redis;

import cn.clazs.qratelimiter.core.LimiterExecutor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Redis 漏桶执行器
 *
 * <p>Redis 侧统一使用服务器时间推进泄放过程，避免多实例本地时钟差异导致桶内水量不一致
 */
public class RedisLeakyBucketExecutor implements LimiterExecutor {

    private static final String DEFAULT_KEY_PREFIX = "qratelimiter:";
    private static final String SCRIPT_PATH = "redis/leaky_bucket.lua";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> leakyBucketScript;
    private final String keyPrefix;

    public RedisLeakyBucketExecutor(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_KEY_PREFIX);
    }

    public RedisLeakyBucketExecutor(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.leakyBucketScript = new DefaultRedisScript<>();
        this.leakyBucketScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(SCRIPT_PATH)));
        this.leakyBucketScript.setResultType(Long.class);
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

        long expireMillis = Math.max(interval * 2L, interval + 60_000L);
        Long result = redisTemplate.execute(
                leakyBucketScript,
                java.util.Collections.singletonList(buildRedisKey(key)),
                String.valueOf(freq),
                String.valueOf(interval),
                String.valueOf(capacity),
                String.valueOf(expireMillis)
        );
        return result != null && result == 1L;
    }

    @Override
    public int getCurrentCount(String key) {
        String water = (String) redisTemplate.opsForHash().get(buildRedisKey(key), "water");
        if (water == null) {
            return 0;
        }
        return (int) Math.ceil(Double.parseDouble(water));
    }

    @Override
    public void reset(String key) {
        redisTemplate.delete(buildRedisKey(key));
    }

    private String buildRedisKey(String key) {
        return keyPrefix + "leaky_bucket:" + key;
    }
}
