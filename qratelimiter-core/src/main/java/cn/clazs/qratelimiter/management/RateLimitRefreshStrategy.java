package cn.clazs.qratelimiter.management;

/**
 * Runtime option refresh behavior.
 */
public enum RateLimitRefreshStrategy {
    APPLY_TO_NEW_LIMITERS_ONLY,
    CLEAR_CACHE_AND_APPLY
}
