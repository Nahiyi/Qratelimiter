package cn.clazs.qratelimiter;

import lombok.Getter;

/**
 * 基于环形数组的高性能、轻量级限流器
 * 使用二分查找在O(log n)时间内判断是否允许访问
 *
 * <p>限流算法类型：滑动窗口时间算法
 * <p>时间复杂度：O(log n) - 使用二分查找统计窗口内记录数
 * <p>空间复杂度：O(capacity) - 固定大小的环形数组
 *
 * @author clazs
 * @since 1.0
 */
public class UserLimiter {
    /** 访问记录数组（环形缓冲区） */
    private final AccessRecord[] records;

    /** interval时间间隔内，最大允许freq次访问 */
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

        if (capacity < freq) {
            throw new IllegalArgumentException(
                    "Capacity must be >= frequency for correct rate limiting. " +
                            "Got capacity=" + capacity + ", freq=" + freq + ". " +
                            "When capacity < freq, the array cannot store enough records " +
                            "to accurately count requests within the time window."
            );
        }

        this.records = new AccessRecord[capacity];
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
     * 核心方法：判断是否允许访问
     * @param record 访问记录
     * @return true表示允许，false表示被限流
     */
    public boolean allowRequest(AccessRecord record) {
        long currentTime = record.getTimestamp();

        // 统计时间窗口内的访问次数（无论数组是否已满）
        long windowStart = currentTime - interval;
        int countInWindow = countRecordsInWindow(windowStart);

        // 如果超过频率限制，拒绝请求
        if (countInWindow >= freq) {
            return false;
        }

        // 允许请求，添加记录
        addRecord(record);
        return true;
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
     * 二分找到逻辑索引中第一个时间戳 >= target 的位置(视角为逻辑连续数组)
     * @param target 目标时间戳
     * @return 逻辑索引，如果不存在返回-1
     */
    private int lowerBound(long target) {
        int l = 0, r = size - 1;

        while (l <= r) {
            int mid = l + ((r - l) >> 1);
            AccessRecord midRecord = getLogical(mid);
            long midTime = midRecord.getTimestamp();

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
     * 添加记录到环形数组
     * @param record 访问记录
     */
    private void addRecord(AccessRecord record) {
        records[tail] = record;

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
     * 获取逻辑索引对应的记录
     * @param logicalIndex 逻辑索引（0=最旧，size-1=最新）
     * @return 对应的访问记录
     */
    private AccessRecord getLogical(int logicalIndex) {
        if (logicalIndex < 0 || logicalIndex >= size) {
            throw new IndexOutOfBoundsException(
                    "Index: " + logicalIndex + ", Size: " + size
            );
        }
        // 逻辑索引转物理索引
        int physicalIndex = (head + logicalIndex) % capacity;
        return records[physicalIndex];
    }

    public boolean isFull() {
        return size == capacity;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 获取最早的记录时间戳
     * @return 最旧记录的时间戳，如果数组为空返回null
     */
    public Long getOldestTimestamp() {
        if (isEmpty()) {
            return null;
        }
        return getLogical(0).getTimestamp();
    }

    /**
     * 获取最新的记录时间戳
     * @return 最新记录的时间戳，如果数组为空返回null
     */
    public Long getLatestTimestamp() {
        if (isEmpty()) {
            return null;
        }
        return getLogical(size - 1).getTimestamp();
    }
}