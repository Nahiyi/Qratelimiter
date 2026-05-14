package cn.clazs.qratelimiter.factory;

import cn.clazs.qratelimiter.core.LimiterExecutor;
import cn.clazs.qratelimiter.core.RateLimiterOptions;
import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;
import cn.clazs.qratelimiter.executor.local.LocalLeakyBucketExecutor;
import cn.clazs.qratelimiter.executor.local.LocalSlidingWindowCounterExecutor;
import cn.clazs.qratelimiter.executor.local.LocalSlidingWindowLogExecutor;
import cn.clazs.qratelimiter.executor.local.LocalTokenBucketExecutor;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流执行器工厂。
 *
 * <p>Core 模块默认只注册本地执行器。Spring Boot 集成层可以通过
 * {@link #registerProvider(RateLimitAlgorithm, RateLimitStorage, ExecutorProvider)}
 * 挂载 Redis 等外部存储实现。
 *
 * @author clazs
 * @since 1.0.0
 */
public class LimiterExecutorFactory {

    private final Map<String, LimiterExecutor> executorCache = new ConcurrentHashMap<>();
    private final Map<String, ExecutorProvider> providers = new ConcurrentHashMap<>();
    private final long localCacheExpireAfterAccessMinutes;
    private final long localCacheMaximumSize;

    public LimiterExecutorFactory() {
        this(RateLimiterOptions.defaults());
    }

    public LimiterExecutorFactory(RateLimiterOptions options) {
        RateLimiterOptions safeOptions = options == null ? RateLimiterOptions.defaults() : options;
        this.localCacheExpireAfterAccessMinutes = safeOptions.getCacheExpireAfterAccessMinutes();
        this.localCacheMaximumSize = safeOptions.getCacheMaximumSize();
        registerLocalProviders();
    }

    public void registerProvider(RateLimitAlgorithm algorithm,
                                 RateLimitStorage storage,
                                 ExecutorProvider provider) {
        Objects.requireNonNull(algorithm, "algorithm cannot be null");
        Objects.requireNonNull(storage, "storage cannot be null");
        Objects.requireNonNull(provider, "provider cannot be null");
        providers.put(buildCacheKey(algorithm, storage), provider);
        executorCache.remove(buildCacheKey(algorithm, storage));
    }

    public LimiterExecutor getExecutor(RateLimitAlgorithm algorithm, RateLimitStorage storage) {
        Objects.requireNonNull(algorithm, "algorithm cannot be null");
        Objects.requireNonNull(storage, "storage cannot be null");

        String cacheKey = buildCacheKey(algorithm, storage);
        return executorCache.computeIfAbsent(cacheKey, key -> {
            ExecutorProvider provider = providers.get(key);
            if (provider == null) {
                throw new IllegalStateException("No limiter executor registered for " + key);
            }
            return provider.create();
        });
    }

    public void clearCache() {
        executorCache.clear();
    }

    public int getCacheSize() {
        return executorCache.size();
    }

    private void registerLocalProviders() {
        registerProvider(RateLimitAlgorithm.SLIDING_WINDOW_LOG, RateLimitStorage.LOCAL,
                () -> new LocalSlidingWindowLogExecutor(localCacheExpireAfterAccessMinutes, localCacheMaximumSize));
        registerProvider(RateLimitAlgorithm.SLIDING_WINDOW_COUNTER, RateLimitStorage.LOCAL,
                () -> new LocalSlidingWindowCounterExecutor(localCacheExpireAfterAccessMinutes, localCacheMaximumSize));
        registerProvider(RateLimitAlgorithm.TOKEN_BUCKET, RateLimitStorage.LOCAL,
                () -> new LocalTokenBucketExecutor(localCacheExpireAfterAccessMinutes, localCacheMaximumSize));
        registerProvider(RateLimitAlgorithm.LEAKY_BUCKET, RateLimitStorage.LOCAL,
                () -> new LocalLeakyBucketExecutor(localCacheExpireAfterAccessMinutes, localCacheMaximumSize));
    }

    private String buildCacheKey(RateLimitAlgorithm algorithm, RateLimitStorage storage) {
        return algorithm.getCode() + ":" + storage.getCode();
    }

    public interface ExecutorProvider {
        LimiterExecutor create();
    }
}
