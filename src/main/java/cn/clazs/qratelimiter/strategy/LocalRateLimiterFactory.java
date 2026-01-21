package cn.clazs.qratelimiter.strategy;

import cn.clazs.qratelimiter.core.RateLimiter;
import cn.clazs.qratelimiter.core.RateLimiterFactory;

/**
 * 本地限流器工厂
 *
 * @author clazs
 * @since 1.0
 */
public class LocalRateLimiterFactory implements RateLimiterFactory {
    @Override
    public RateLimiter create(String key, int freq, long interval, int capacity) {
        return new LocalRateLimiter(capacity, freq, interval);
    }
}
