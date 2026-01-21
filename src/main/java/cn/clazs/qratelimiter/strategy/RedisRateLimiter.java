package cn.clazs.qratelimiter.strategy;

import cn.clazs.qratelimiter.core.RateLimiter;
import lombok.Getter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;

/**
 * 分布式限流器（Redis Implementation）
 * 基于 Redis List + Lua 脚本模拟滑动窗口算法
 *
 * <p>核心逻辑：
 * <ul>
 *     <li>使用 Redis List 存储时间戳</li>
 *     <li>Lua 脚本中读取 List 转为数组</li>
 *     <li>在 Lua 中执行二分查找（Binary Search）统计窗口内数量</li>
 *     <li>保持了与本地限流器一致的算法逻辑</li>
 * </ul>
 *
 * @author clazs
 * @since 1.0
 */
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final String key;

    @Getter
    private final int freq;

    @Getter
    private final long interval;

    @Getter
    private final int capacity;

    /**
     * Lua 脚本：滑动窗口算法
     */
    private static final String LUA_SCRIPT_TEXT =
            "local key = KEYS[1]\n" +
            "local freq = tonumber(ARGV[1])\n" +
            "local interval = tonumber(ARGV[2])\n" +
            "local now = tonumber(ARGV[3])\n" +
            "local capacity = tonumber(ARGV[4])\n" +
            "\n" +
            "-- 1. Get all records\n" +
            "local timestamps = redis.call('lrange', key, 0, -1)\n" +
            "local size = #timestamps\n" +
            "\n" +
            "-- 2. Calculate window start\n" +
            "local windowStart = now - interval\n" +
            "\n" +
            "-- 3. Binary Search (Find first index >= windowStart)\n" +
            "local l = 1\n" +
            "local r = size\n" +
            "local index = size + 1\n" +
            "\n" +
            "while l <= r do\n" +
            "    local mid = math.floor((l + r) / 2)\n" +
            "    local ts = tonumber(timestamps[mid])\n" +
            "    if ts < windowStart then\n" +
            "        l = mid + 1\n" +
            "    else\n" +
            "        index = mid\n" +
            "        r = mid - 1\n" +
            "    end\n" +
            "end\n" +
            "\n" +
            "-- 4. Count records in window\n" +
            "local count = 0\n" +
            "if index <= size then\n" +
            "    count = size - index + 1\n" +
            "end\n" +
            "\n" +
            "-- 5. Check limit\n" +
            "if count >= freq then\n" +
            "    return 0\n" +
            "end\n" +
            "\n" +
            "-- 6. Allow: push current timestamp\n" +
            "redis.call('rpush', key, now)\n" +
            "\n" +
            "-- 7. Trim (Simulate Ring Buffer)\n" +
            "if size >= capacity then\n" +
            "    redis.call('ltrim', key, -capacity, -1)\n" +
            "end\n" +
            "\n" +
            "-- 8. Expire\n" +
            "redis.call('pexpire', key, interval * 2)\n" +
            "\n" +
            "return 1";

    private static final RedisScript<Long> LUA_SCRIPT = new DefaultRedisScript<>(LUA_SCRIPT_TEXT, Long.class);

    public RedisRateLimiter(StringRedisTemplate redisTemplate, String key, int freq, long interval, int capacity) {
        this.redisTemplate = redisTemplate;
        this.key = key;
        this.freq = freq;
        this.interval = interval;
        this.capacity = capacity;
    }

    @Override
    public boolean allowRequest() {
        long now = System.currentTimeMillis();
        List<String> keys = Collections.singletonList(key);
        
        // args: freq, interval, now, capacity
        Long result = redisTemplate.execute(
                LUA_SCRIPT,
                keys,
                String.valueOf(freq),
                String.valueOf(interval),
                String.valueOf(now),
                String.valueOf(capacity)
        );

        return result != null && result == 1L;
    }
}
