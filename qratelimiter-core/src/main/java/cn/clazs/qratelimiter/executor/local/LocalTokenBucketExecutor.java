package cn.clazs.qratelimiter.executor.local;

import cn.clazs.qratelimiter.core.LimiterExecutor;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地令牌桶执行器
 *
 * <p>对外仍使用统一的 {@code freq / interval / capacity} 参数语义：
 * <ul>
 *     <li>{@code freq}：每个 {@code interval} 周期补充的令牌数</li>
 *     <li>{@code interval}：令牌补充周期</li>
 *     <li>{@code capacity}：桶最大容量</li>
 * </ul>
 */
public class LocalTokenBucketExecutor implements LimiterExecutor {

    private static final long DEFAULT_CACHE_EXPIRE_AFTER_ACCESS_MINUTES = 30;
    private static final long DEFAULT_CACHE_MAXIMUM_SIZE = 10_000;

    private static final class TokenBucketState {
        double tokens;
        long lastRefillTime;
        final ReentrantLock lock = new ReentrantLock();

        TokenBucketState(double tokens, long lastRefillTime) {
            this.tokens = tokens;
            this.lastRefillTime = lastRefillTime;
        }
    }

    private final Cache<String, TokenBucketState> bucketCache;

    public LocalTokenBucketExecutor() {
        this(DEFAULT_CACHE_EXPIRE_AFTER_ACCESS_MINUTES, DEFAULT_CACHE_MAXIMUM_SIZE);
    }

    public LocalTokenBucketExecutor(long expireAfterAccessMinutes, long maximumSize) {
        this.bucketCache = Caffeine.newBuilder()
                .expireAfterAccess(expireAfterAccessMinutes, TimeUnit.MINUTES)
                .maximumSize(maximumSize)
                .build();
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
        TokenBucketState state = bucketCache.get(key, k -> new TokenBucketState(capacity, currentTime));

        assert state != null;
        state.lock.lock();
        try {
            long safeNow = currentTime < state.lastRefillTime ? state.lastRefillTime : currentTime;
            refillTokens(state, safeNow, freq, interval, capacity);

            if (state.tokens < 1D) {
                return false;
            }

            state.tokens -= 1D;
            return true;
        } finally {
            state.lock.unlock();
        }
    }

    @Override
    public int getCurrentCount(String key) {
        TokenBucketState state = bucketCache.getIfPresent(key);
        if (state == null) {
            return 0;
        }

        state.lock.lock();
        try {
            return (int) Math.floor(Math.max(0D, state.tokens));
        } finally {
            state.lock.unlock();
        }
    }

    @Override
    public void reset(String key) {
        bucketCache.invalidate(key);
    }

    private void refillTokens(TokenBucketState state, long currentTime, int freq, long interval, int capacity) {
        long elapsedMillis = currentTime - state.lastRefillTime;
        if (elapsedMillis <= 0L) {
            return;
        }

        double refillRatePerMillis = (double) freq / (double) interval;
        double tokensToAdd = elapsedMillis * refillRatePerMillis;
        state.tokens = Math.min(capacity, state.tokens + tokensToAdd);
        state.lastRefillTime = currentTime;
    }
}
