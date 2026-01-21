package cn.clazs.qratelimiter.strategy;

import cn.clazs.qratelimiter.core.RateLimiter;
import cn.clazs.qratelimiter.core.RateLimiterFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis限流器工厂
 *
 * @author clazs
 * @since 1.0
 */
public class RedisRateLimiterFactory implements RateLimiterFactory {

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiterFactory(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimiter create(String key, int freq, long interval, int capacity) {
        // 自动添加前缀
        String redisKey = "qratelimiter:" + key;
        return new RedisRateLimiter(redisTemplate, redisKey, freq, interval, capacity);
    }
}
