package cn.clazs.qratelimiter;

import lombok.Getter;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于环形数组的高性能、线程安全限流器
 * 使用二分查找在O(log n)时间内判断是否允许访问
 *
 * <p>限流算法类型：滑动窗口时间算法
 * <p>时间复杂度：O(log n) - 使用二分查找统计窗口内记录数
 * <p>空间复杂度：O(capacity) - 固定大小的环形数组
 * <p>线程安全：使用ReentrantLock保证同一用户并发请求的顺序执行
 *
 * @author clazs
 * @since 1.0
 */
public class UserLimiter {
    /** 时间戳数组（环形缓冲区） */
    private final long[] timestamps;

    /** 时间窗口内最大允许访问次数 */
    @Getter
    private final int freq;

    /** 时间窗口长度（单位：毫秒） */
    @Getter
    private final long interval;

    /** 当前有效元素数量 */
    @Getter
    private int size;

    /** 数组总容量 */
    @Getter
    private final int capacity;

    /** 指向最旧的元素（逻辑索引0） */
    private int head;

    /** 指向下一个写入位置 */
    private int tail;

    /** 并发锁：保证同一用户并发请求的顺序执行 */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * @param capacity 数组容量（必须 >= freq）
     * @param freq 时间窗口内最大访问次数
     * @param interval 时间窗口长度（毫秒）
     */
    public UserLimiter(int capacity, int freq, long interval) {
        // 参数合法性校验
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive, got: " + capacity);
        }
        if (freq <= 0) {
            throw new IllegalArgumentException("Frequency must be positive, got: " + freq);
        }
        if (interval <= 0) {
            throw new IllegalArgumentException("Interval must be positive, got: " + interval);
        }

        // 约束：capacity 必须 >= freq
        if (capacity < freq) {
            throw new IllegalArgumentException(
                    "Capacity must be >= frequency for correct rate limiting. " +
                            "Got capacity=" + capacity + ", freq=" + freq + ". " +
                            "When capacity < freq, the array cannot store enough records " +
                            "to accurately count requests within the time window."
            );
        }

        this.timestamps = new long[capacity];
        this.freq = freq;
        this.interval = interval;
        this.capacity = capacity;
        this.head = 0;
        this.tail = 0;
        this.size = 0;
    }

    public UserLimiter(int freq, long interval) {
        this(freq, freq, interval);
    }

    /**
     * 核心方法：判断是否允许访问（线程安全）
     *
     * <p>使用ReentrantLock保证同一用户的并发请求顺序执行，避免竞态条件导致限流失效
     *
     * @param currentTime 当前时间戳（毫秒）
     * @return true表示允许，false表示被限流
     */
    public boolean allowRequest(long currentTime) {
        lock.lock();
        try {
            // 统计时间窗口内的访问次数
            long windowStart = currentTime - interval;
            int countInWindow = countRecordsInWindow(windowStart);

            // 如果超过频率限制，拒绝请求
            if (countInWindow >= freq) {
                return false;
            }

            // 允许请求，添加时间戳
            addTimestamp(currentTime);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 二分查找统计时间窗口内的记录数量
     * @param windowStart 时间窗口起始时间戳
     * @return 窗口内的记录数量
     */
    private int countRecordsInWindow(long windowStart) {
        if (isEmpty()) {
            return 0;
        }

        // 使用二分查找找到第一个 >= windowStart 的记录
        int firstIdx = lowerBound(windowStart);

        if (firstIdx == -1) {
            // 所有记录都在窗口之外
            return 0;
        }

        // 返回从 firstIdx 到末尾的记录数量
        return size - firstIdx;
    }

    /**
     * 二分找到逻辑索引中第一个时间戳 >= target 的位置
     * 基于逻辑连续数组的视角进行二分查找
     *
     * @param target 目标时间戳
     * @return 逻辑索引，如果不存在返回-1
     */
    private int lowerBound(long target) {
        int l = 0, r = size - 1;

        while (l <= r) {
            int mid = l + ((r - l) >> 1);
            long midTime = getLogical(mid);

            if (midTime < target) {
                l = mid + 1;
            } else {
                r = mid - 1;
            }
        }

        // l 是第一个 >= target 的位置
        if (l < size) {
            return l;
        }
        return -1;
    }

    /**
     * 添加时间戳到环形数组
     * @param timestamp 时间戳
     */
    private void addTimestamp(long timestamp) {
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

    /**
     * 获取逻辑索引对应的时间戳
     *
     * <p>此处也可以不进行getLogical方法的封装，可参考：
     *    <ul>
     *        <li>力扣153. 寻找旋转排序数组中的最小值</li>
     *        <li>力扣33. 搜索旋转排序数组</li>
     *    </ul>
     *    直接实现：在两段有序数组中的查找的算法求得最小值，然后继续二分即可
     *
     * <p>此处保障可读性与封装性，单独封装方法
     *
     * @param logicalIndex 逻辑索引（0=最旧，size-1=最新）
     * @return 对应的时间戳
     */
    private long getLogical(int logicalIndex) {
        if (logicalIndex < 0 || logicalIndex >= size) {
            throw new IndexOutOfBoundsException("Index: " + logicalIndex + ", Size: " + size);
        }
        // 逻辑索引转物理索引
        int physicalIndex = (head + logicalIndex) % capacity;
        return timestamps[physicalIndex];
    }

    /**
     * 判断数组是否已满
     * @return true表示已满
     */
    public boolean isFull() {
        return size == capacity;
    }

    /**
     * 判断数组是否为空
     * @return true表示为空
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 获取最早的记录时间戳
     * @return 最旧记录的时间戳，如果数组为空返回0
     */
    public long getOldestTimestamp() {
        if (isEmpty()) {
            return 0;
        }
        return getLogical(0);
    }

    /**
     * 获取最新的记录时间戳
     * @return 最新记录的时间戳，如果数组为空返回0
     */
    public long getLatestTimestamp() {
        if (isEmpty()) {
            return 0;
        }
        return getLogical(size - 1);
    }

    /**
     * 获取当前锁状态
     * @return true表示锁被持有
     */
    public boolean isLocked() {
        return lock.isLocked();
    }

    /**
     * 获取等待队列长度
     * @return 等待获取锁的线程数
     */
    public int getQueueLength() {
        return lock.getQueueLength();
    }
}
