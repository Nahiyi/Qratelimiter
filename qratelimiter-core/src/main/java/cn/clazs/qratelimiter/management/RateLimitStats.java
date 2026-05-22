package cn.clazs.qratelimiter.management;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe request counters for one limiter.
 */
public class RateLimitStats {

    private final AtomicLong allowedRequests = new AtomicLong();
    private final AtomicLong rejectedRequests = new AtomicLong();

    public void record(boolean allowed) {
        if (allowed) {
            allowedRequests.incrementAndGet();
        } else {
            rejectedRequests.incrementAndGet();
        }
    }

    public long getAllowedRequests() {
        return allowedRequests.get();
    }

    public long getRejectedRequests() {
        return rejectedRequests.get();
    }
}
