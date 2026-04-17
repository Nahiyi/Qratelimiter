package cn.clazs.qratelimiter.executor.redis;

import cn.clazs.qratelimiter.core.LimiterExecutor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Redis 令牌桶执行器
 *
 * <p>Redis 侧统一使用服务器时间推进令牌补充，避免多实例本地时钟差异导致令牌恢复速度不一致
 */
public class RedisTokenBucketExecutor implements LimiterExecutor {

    private static final String DEFAULT_KEY_PREFIX = "qratelimiter:";
    private static final String SCRIPT_PATH = "redis/token_bucket.lua";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> tokenBucketScript;
    private final String keyPrefix;

    public RedisTokenBucketExecutor(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_KEY_PREFIX);
    }

    public RedisTokenBucketExecutor(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(SCRIPT_PATH)));
        this.tokenBucketScript.setResultType(Long.class);
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
                tokenBucketScript,
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
        String tokens = (String) redisTemplate.opsForHash().get(buildRedisKey(key), "tokens");
        if (tokens == null) {
            return 0;
        }
        return (int) Math.floor(Double.parseDouble(tokens));
    }

    @Override
    public void reset(String key) {
        redisTemplate.delete(buildRedisKey(key));
    }

    private String buildRedisKey(String key) {
        return keyPrefix + "token_bucket:" + key;
    }
}
