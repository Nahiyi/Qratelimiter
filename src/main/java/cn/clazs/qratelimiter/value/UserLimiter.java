package cn.clazs.qratelimiter.value;

import cn.clazs.qratelimiter.util.TimestampUtil;
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
@Deprecated
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
     * 核心方法：判断是否允许访问（线程安全，自动获取时间）
     * 使用ReentrantLock保证同一用户的并发请求顺序执行，避免竞态条件导致限流失效
     *
     * @return true表示允许，false表示被限流
     */
    public boolean allowRequest() {
        return allowRequestInternal(System.currentTimeMillis());
    }

    /**
     * 核心方法：判断是否允许访问（线程安全，手动指定时间）
     *
     * @param currentTime 当前时间戳（必须为毫秒级别）
     * @return true表示允许，false表示被限流
     * @throws IllegalArgumentException 如果时间戳不是毫秒级别
     */
    public boolean allowRequest(long currentTime) {
        // 使用工具类检查时间戳是否为毫秒级别
        TimestampUtil.validateMillisecondTimestamp(currentTime);
        return allowRequestInternal(currentTime);
    }

    /**
     * 内部实现：限流逻辑核心
     */
    private boolean allowRequestInternal(long currentTime) {
        lock.lock();
        try {
            if (size > 0) {
                long lastTime = getLogical(size - 1);  // 获取最后一个时间戳
                if (currentTime < lastTime) {
                    // check: 发生时钟回拨或请求乱序，强制修正为最后一条记录的时间
                    currentTime = lastTime;
                }
            }

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
     *
     * <p>数学优化：利用 lowerBound 的返回值特性
     * <ul>
     *     <li>lowerBound 返回范围：[0, size]</li>
     *     <li>如果所有记录都 < windowStart，返回 size，计算结果为 0</li>
     *     <li>否则返回第一个 >= windowStart 的位置，正确计算窗口内记录数</li>
     * </ul>
     *
     * @param windowStart 时间窗口起始时间戳
     * @return 窗口内的记录数量
     */
    private int countRecordsInWindow(long windowStart) {
        if (isEmpty()) {
            return 0;
        }

        // lowerBound 直接返回 l (取值范围 [0, size])
        int firstIdx = lowerBound(windowStart);

        // 如果 firstIdx == size，说明所有记录都在窗口外，结果为 0
        // 则结果就是 size - firstIdx
        return size - firstIdx;
    }

    /**
     * 二分查找：找到逻辑索引中第一个时间戳 >= target 的位置
     *
     * <p>经典的 lowerBound 算法实现（红蓝染色法）：
     * <ul>
     *     <li>红色（< target）：l 左侧</li>
     *     <li>蓝色（>= target）：r 右侧</li>
     *     <li>最终 l 停留在第一个蓝色位置</li>
     * </ul>
     *
     * <p>返回值特性：
     * <ul>
     *     <li>范围：[0, size]</li>
     *     <li>如果所有元素都 < target，返回 size</li>
     *     <li>如果所有元素都 >= target，返回 0</li>
     * </ul>
     *
     * @param target 目标时间戳
     * @return 第一个 >= target 的位置，范围 [0, size]
     */
    private int lowerBound(long target) {
        int l = 0, r = size - 1;

        while (l <= r) {
            int mid = l + ((r - l) >> 1); // 防溢出
            if (getLogical(mid) < target)
                l = mid + 1;
            else
                r = mid - 1;
        }

        // 直接返回 l 即可；另外， l 的取值范围是 [0, size]
        return l;
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
