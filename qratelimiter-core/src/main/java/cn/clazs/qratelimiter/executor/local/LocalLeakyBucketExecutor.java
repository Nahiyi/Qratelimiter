package cn.clazs.qratelimiter.executor.local;

import cn.clazs.qratelimiter.core.LimiterExecutor;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地漏桶执行器
 *
 * <p>对外仍使用统一的 {@code freq / interval / capacity} 参数语义：
 * <ul>
 *     <li>{@code freq}：每个 {@code interval} 周期泄放的请求数</li>
 *     <li>{@code interval}：泄放周期</li>
 *     <li>{@code capacity}：桶最大积压容量</li>
 * </ul>
 */
public class LocalLeakyBucketExecutor implements LimiterExecutor {

    private static final long DEFAULT_CACHE_EXPIRE_AFTER_ACCESS_MINUTES = 30;
    private static final long DEFAULT_CACHE_MAXIMUM_SIZE = 10_000;

    private static final class LeakyBucketState {
        double water;
        long lastLeakTime;
        final ReentrantLock lock = new ReentrantLock();

        LeakyBucketState(double water, long lastLeakTime) {
            this.water = water;
            this.lastLeakTime = lastLeakTime;
        }
    }

    private final Cache<String, LeakyBucketState> bucketCache;

    public LocalLeakyBucketExecutor() {
        this(DEFAULT_CACHE_EXPIRE_AFTER_ACCESS_MINUTES, DEFAULT_CACHE_MAXIMUM_SIZE);
    }

    public LocalLeakyBucketExecutor(long expireAfterAccessMinutes, long maximumSize) {
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
        LeakyBucketState state = bucketCache.get(key, k -> new LeakyBucketState(0D, currentTime));

        assert state != null;
        state.lock.lock();
        try {
            long safeNow = currentTime < state.lastLeakTime ? state.lastLeakTime : currentTime;
            leakWater(state, safeNow, freq, interval);

            if (state.water + 1D > capacity) {
                return false;
            }

            state.water += 1D;
            return true;
        } finally {
            state.lock.unlock();
        }
    }

    @Override
    public int getCurrentCount(String key) {
        LeakyBucketState state = bucketCache.getIfPresent(key);
        if (state == null) {
            return 0;
        }

        state.lock.lock();
        try {
            return (int) Math.ceil(Math.max(0D, state.water));
        } finally {
            state.lock.unlock();
        }
    }

    @Override
    public void reset(String key) {
        bucketCache.invalidate(key);
    }

    private void leakWater(LeakyBucketState state, long currentTime, int freq, long interval) {
        long elapsedMillis = currentTime - state.lastLeakTime;
        if (elapsedMillis <= 0L) {
            return;
        }

        double leakRatePerMillis = (double) freq / (double) interval;
        double leaked = elapsedMillis * leakRatePerMillis;
        state.water = Math.max(0D, state.water - leaked);
        state.lastLeakTime = currentTime;
    }
}
