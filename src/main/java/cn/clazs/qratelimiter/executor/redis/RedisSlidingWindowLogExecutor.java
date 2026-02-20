package cn.clazs.qratelimiter.executor.redis;

import cn.clazs.qratelimiter.core.LimiterExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis 滑动窗口日志执行器
 *
 * <p>使用 Redis ZSet 数据结构实现滑动窗口日志算法
 * <p>Score = 时间戳，Member = 时间戳:唯一ID（解决ZSet Member唯一性）
 * <p>使用 Lua 脚本保证原子性操作
 *
 * @author clazs
 * @since 1.0.0
 */
@Slf4j
public class RedisSlidingWindowLogExecutor implements LimiterExecutor {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> slidingWindowLogScript;
    private final String keyPrefix;

    /**
     * Redis 键默认前缀
     */
    private static final String DEFAULT_KEY_PREFIX = "qratelimiter:";

    /**
     * 默认过期时间（秒）
     */
    private static final int DEFAULT_EXPIRE_TIME_SECONDS = 3600;

    /**
     * Lua 脚本路径
     */
    private static final String SCRIPT_PATH = "redis/sliding_window_log.lua";

    /**
     * 构造函数
     *
     * @param redisTemplate Redis 模板
     */
    public RedisSlidingWindowLogExecutor(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_KEY_PREFIX);
    }

    /**
     * 构造函数
     *
     * @param redisTemplate Redis 模板
     * @param keyPrefix Redis 键前缀
     */
    public RedisSlidingWindowLogExecutor(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;

        // 加载 Lua 脚本
        this.slidingWindowLogScript = new DefaultRedisScript<>();
        this.slidingWindowLogScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(SCRIPT_PATH)));
        this.slidingWindowLogScript.setResultType(Long.class);
    }

    @Override
    public boolean tryAcquire(String key, int freq, long interval, int capacity) {
        String redisKey = buildRedisKey(key);
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - interval;

        // 计算 Redis 过期时间（至少为 interval + 60秒，确保窗口外数据能被清理）
        int expireTimeSeconds = (int) ((interval / 1000) + 60);

        // 生成唯一标识（解决同一毫秒并发问题）
        // 使用 ThreadLocalRandom 性能更好，且碰撞概率极低（2^64分之一）
        String uniqueId = Long.toString(ThreadLocalRandom.current().nextLong());

        // 执行 Lua 脚本
        List<String> keys = Collections.singletonList(redisKey);
        Long result = redisTemplate.execute(
                slidingWindowLogScript,
                keys,
                String.valueOf(windowStart),      // ARGV[1]: 窗口起始时间
                String.valueOf(freq),             // ARGV[2]: 频率限制
                String.valueOf(currentTime),      // ARGV[3]: 当前时间戳（Score）
                String.valueOf(expireTimeSeconds),// ARGV[4]: 过期时间
                uniqueId                          // ARGV[5]: 唯一标识（并发不丢失）
        );

        return result != null && result == 1L;
    }

    @Override
    public int getCurrentCount(String key) {
        String redisKey = buildRedisKey(key);
        Long count = redisTemplate.opsForZSet().size(redisKey);
        return count != null ? count.intValue() : 0;
    }

    @Override
    public void reset(String key) {
        String redisKey = buildRedisKey(key);
        redisTemplate.delete(redisKey);
    }

    /**
     * 构建 Redis 键
     *
     * @param key 原始键
     * @return Redis 键
     */
    private String buildRedisKey(String key) {
        return keyPrefix + "sliding_window_log:" + key;
    }
}
