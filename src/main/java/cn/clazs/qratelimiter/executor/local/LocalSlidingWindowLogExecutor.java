package cn.clazs.qratelimiter.executor.local;

import cn.clazs.qratelimiter.core.LimiterExecutor;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地滑动窗口日志执行器
 *
 * <p>基于环形数组 + 二分查找的高性能限流器实现
 * <p>时间复杂度：O(log n) - 使用二分查找统计窗口内记录数
 * <p>空间复杂度：O(capacity) - 固定大小的环形数组
 * <p>线程安全：使用 ReentrantLock 保证并发安全
 *
 * @author clazs
 * @since 1.0.0
 */
@Slf4j
public class LocalSlidingWindowLogExecutor implements LimiterExecutor {

    /**
     * 环形缓冲区：存储每个 key 的时间戳数组
     */
    private static class RingBuffer {
        /** 时间戳数组（环形缓冲区） */
        final long[] timestamps;

        /** 当前有效元素数量 */
        int size;

        /** 数组总容量 */
        final int capacity;

        /** 指向最旧的元素（逻辑索引0） */
        int head;

        /** 指向下一个写入位置 */
        int tail;

        /** 并发锁：保证同一 key 并发请求的顺序执行 */
        final ReentrantLock lock = new ReentrantLock();

        RingBuffer(int capacity) {
            this.timestamps = new long[capacity];
            this.capacity = capacity;
            this.size = 0;
            this.head = 0;
            this.tail = 0;
        }

        /**
         * 判断数组是否为空
         */
        boolean isEmpty() {
            return size == 0;
        }

        /**
         * 获取逻辑索引对应的时间戳
         *
         * @param logicalIndex 逻辑索引（0=最旧，size-1=最新）
         * @return 对应的时间戳
         */
        long getLogical(int logicalIndex) {
            if (logicalIndex < 0 || logicalIndex >= size) {
                throw new IndexOutOfBoundsException("Index: " + logicalIndex + ", Size: " + size);
            }
            // 逻辑索引转物理索引
            int physicalIndex = (head + logicalIndex) % capacity;
            return timestamps[physicalIndex];
        }

        /**
         * 二分查找：找到逻辑索引中第一个时间戳 >= target 的位置
         *
         * @param target 目标时间戳
         * @return 第一个 >= target 的位置，范围 [0, size]
         */
        int lowerBound(long target) {
            int l = 0, r = size - 1;

            while (l <= r) {
                int mid = l + ((r - l) >> 1); // 防溢出
                if (getLogical(mid) < target)
                    l = mid + 1;
                else
                    r = mid - 1;
            }

            return l;
        }

        /**
         * 二分查找统计时间窗口内的记录数量
         *
         * @param windowStart 时间窗口起始时间戳
         * @return 窗口内的记录数量
         */
        int countRecordsInWindow(long windowStart) {
            if (isEmpty()) {
                return 0;
            }

            int firstIdx = lowerBound(windowStart);
            return size - firstIdx;
        }

        /**
         * 添加时间戳到环形数组
         *
         * @param timestamp 时间戳
         */
        void addTimestamp(long timestamp) {
            timestamps[tail] = timestamp;

            if (size < capacity) {
                // 数组未满，size增加
                size++;
            } else {
                // 数组已满，head被迫向前移动
                head = (head + 1) % capacity;
            }

            // tail向前移动
            tail = (tail + 1) % capacity;
        }
    }

    /**
     * 限流器缓存：key -> RingBuffer
     */
    private final Cache<String, RingBuffer> limiterCache;

    /**
     * 默认缓存过期时间（分钟）
     */
    private static final long DEFAULT_CACHE_EXPIRE_AFTER_ACCESS_MINUTES = 30;

    /**
     * 默认缓存最大容量
     */
    private static final long DEFAULT_CACHE_MAXIMUM_SIZE = 10_000;

    /**
     * 构造函数：使用默认缓存配置
     */
    public LocalSlidingWindowLogExecutor() {
        this(DEFAULT_CACHE_EXPIRE_AFTER_ACCESS_MINUTES, DEFAULT_CACHE_MAXIMUM_SIZE);
    }

    /**
     * 构造函数：自定义缓存配置
     *
     * @param expireAfterAccessMinutes 缓存过期时间（分钟）
     * @param maximumSize 缓存最大容量
     */
    public LocalSlidingWindowLogExecutor(long expireAfterAccessMinutes, long maximumSize) {
        this.limiterCache = Caffeine.newBuilder()
                .expireAfterAccess(expireAfterAccessMinutes, TimeUnit.MINUTES)
                .maximumSize(maximumSize)
                .build();
    }

    @Override
    public boolean tryAcquire(String key, int freq, long interval, int capacity) {
        // 参数校验
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive, got: " + capacity);
        }
        if (freq <= 0) {
            throw new IllegalArgumentException("Frequency must be positive, got: " + freq);
        }
        if (interval <= 0) {
            throw new IllegalArgumentException("Interval must be positive, got: " + interval);
        }
        if (capacity < freq) {
            throw new IllegalArgumentException(
                    "Capacity must be >= frequency. Got capacity=" + capacity + ", freq=" + freq);
        }

        // 获取或创建 RingBuffer
        RingBuffer buffer = limiterCache.get(key, k -> new RingBuffer(capacity));

        // 加锁执行限流逻辑
        assert buffer != null;
        buffer.lock.lock();
        try {
            long currentTime = System.currentTimeMillis();

            // 时钟回拨检测与修正
            if (buffer.size > 0) {
                long lastTime = buffer.getLogical(buffer.size - 1);
                if (currentTime < lastTime) {
                    currentTime = lastTime;
                }
            }

            // 统计时间窗口内的访问次数
            long windowStart = currentTime - interval;
            int countInWindow = buffer.countRecordsInWindow(windowStart);

            // 如果超过频率限制，拒绝请求
            if (countInWindow >= freq) {
                return false;
            }

            // 允许请求，添加时间戳
            buffer.addTimestamp(currentTime);
            return true;
        } finally {
            buffer.lock.unlock();
        }
    }

    @Override
    public int getCurrentCount(String key) {
        RingBuffer buffer = limiterCache.getIfPresent(key);
        if (buffer == null) {
            return 0;
        }

        buffer.lock.lock();
        try {
            return buffer.size;
        } finally {
            buffer.lock.unlock();
        }
    }

    @Override
    public void reset(String key) {
        limiterCache.invalidate(key);
    }
}
