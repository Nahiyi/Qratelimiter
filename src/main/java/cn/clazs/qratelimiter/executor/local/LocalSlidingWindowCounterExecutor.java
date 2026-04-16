package cn.clazs.qratelimiter.executor.local;

import cn.clazs.qratelimiter.core.LimiterExecutor;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地滑动窗口计数器执行器。
 *
 * <p>该实现将一个完整统计窗口切分为多个时间分片，通过分片计数近似滑动窗口内的请求数。
 * 对外仍使用统一的 {@code freq / interval / capacity} 参数语义：
 * <ul>
 *     <li>{@code freq}：完整统计窗口内允许的最大请求数</li>
 *     <li>{@code interval}：完整统计窗口长度</li>
 *     <li>{@code capacity}：时间分片数量，值越大统计越精细</li>
 * </ul>
 */
public class LocalSlidingWindowCounterExecutor implements LimiterExecutor {

    private static final long DEFAULT_CACHE_EXPIRE_AFTER_ACCESS_MINUTES = 30;
    private static final long DEFAULT_CACHE_MAXIMUM_SIZE = 10_000;

    private static final class CounterWindow {
        final long[] bucketStartTimes;
        final int[] bucketCounts;
        final int bucketCount;
        final long interval;
        final long bucketDuration;
        long lastRequestTime;
        final ReentrantLock lock = new ReentrantLock();

        CounterWindow(int bucketCount, long interval, long bucketDuration) {
            this.bucketCount = bucketCount;
            this.interval = interval;
            this.bucketDuration = bucketDuration;
            this.bucketStartTimes = new long[bucketCount];
            this.bucketCounts = new int[bucketCount];
            this.lastRequestTime = 0L;
        }
    }

    private final Cache<String, CounterWindow> limiterCache;

    public LocalSlidingWindowCounterExecutor() {
        this(DEFAULT_CACHE_EXPIRE_AFTER_ACCESS_MINUTES, DEFAULT_CACHE_MAXIMUM_SIZE);
    }

    public LocalSlidingWindowCounterExecutor(long expireAfterAccessMinutes, long maximumSize) {
        this.limiterCache = Caffeine.newBuilder()
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

        int bucketCount = Math.max(2, capacity);
        long bucketDuration = Math.max(1L, interval / bucketCount);
        CounterWindow window = limiterCache.get(key, k -> new CounterWindow(bucketCount, interval, bucketDuration));

        assert window != null;
        window.lock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            if (window.lastRequestTime > 0 && currentTime < window.lastRequestTime) {
                currentTime = window.lastRequestTime;
            }
            window.lastRequestTime = currentTime;

            double estimatedCount = estimateWindowCount(window, currentTime, window.interval, window.bucketDuration);
            if (estimatedCount >= freq) {
                return false;
            }

            incrementCurrentBucket(window, currentTime, bucketDuration);
            return true;
        } finally {
            window.lock.unlock();
        }
    }

    @Override
    public int getCurrentCount(String key) {
        CounterWindow window = limiterCache.getIfPresent(key);
        if (window == null) {
            return 0;
        }

        window.lock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            long safeNow = window.lastRequestTime > 0 && currentTime < window.lastRequestTime
                    ? window.lastRequestTime
                    : currentTime;
            double estimate = estimateWindowCount(window, safeNow, window.interval, window.bucketDuration);
            return (int) Math.ceil(estimate);
        } finally {
            window.lock.unlock();
        }
    }

    @Override
    public void reset(String key) {
        limiterCache.invalidate(key);
    }

    private double estimateWindowCount(CounterWindow window, long currentTime, long interval, long bucketDuration) {
        double estimate = 0D;
        long windowStart = currentTime - interval;

        for (int i = 0; i < window.bucketCount; i++) {
            int count = window.bucketCounts[i];
            if (count <= 0) {
                continue;
            }

            long bucketStart = window.bucketStartTimes[i];
            long bucketEnd = bucketStart + bucketDuration;

            if (bucketEnd <= windowStart || bucketStart > currentTime) {
                continue;
            }

            long overlapStart = Math.max(bucketStart, windowStart);
            long overlapEnd = Math.min(bucketEnd, currentTime + 1);
            if (overlapEnd <= overlapStart) {
                continue;
            }

            double overlapRatio = (double) (overlapEnd - overlapStart) / (double) bucketDuration;
            estimate += count * Math.min(1D, overlapRatio);
        }

        return estimate;
    }

    private void incrementCurrentBucket(CounterWindow window, long currentTime, long bucketDuration) {
        long bucketStart = alignToBucketStart(currentTime, bucketDuration);
        int bucketIndex = resolveBucketIndex(bucketStart, window.bucketCount, bucketDuration);

        if (window.bucketStartTimes[bucketIndex] != bucketStart) {
            window.bucketStartTimes[bucketIndex] = bucketStart;
            window.bucketCounts[bucketIndex] = 0;
        }

        window.bucketCounts[bucketIndex]++;
    }

    private int resolveBucketIndex(long bucketStart, int bucketCount, long bucketDuration) {
        long bucketSlot = bucketStart / bucketDuration;
        return (int) Math.floorMod(bucketSlot, bucketCount);
    }

    private long alignToBucketStart(long currentTime, long bucketDuration) {
        return (currentTime / bucketDuration) * bucketDuration;
    }
}
