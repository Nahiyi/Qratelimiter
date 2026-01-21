package cn.clazs.qratelimiter.autoconfigure;

import cn.clazs.qratelimiter.aspect.RateLimitAspect;
import cn.clazs.qratelimiter.core.RateLimiterFactory;
import cn.clazs.qratelimiter.properties.RateLimiterProperties;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;
import cn.clazs.qratelimiter.strategy.LocalRateLimiterFactory;
import cn.clazs.qratelimiter.strategy.RedisRateLimiterFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 限流器自动配置类
 *
 * <p>这是 QRateLimiter Spring Boot Starter 的核心配置类，负责自动创建和注册所需的 Bean
 *
 * <p>自动配置的功能：
 * <ul>
 *     <li>自动注册 {@link RateLimiterProperties} 配置属性 Bean</li>
 *     <li>自动注册 {@link RateLimiterFactory} 限流器工厂 Bean (Local 或 Redis)</li>
 *     <li>自动注册 {@link RateLimitRegistry} 限流器注册中心 Bean</li>
 *     <li>自动注册 {@link RateLimitAspect} AOP 切面 Bean</li>
 * </ul>
 *
 * @author clazs
 * @since 1.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
@ConditionalOnProperty(prefix = "clazs.ratelimiter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimiterAutoConfiguration {

    /**
     * 注册本地限流器工厂 (当 storage-type 为 local 或不配置时生效)
     */
    @Bean
    @ConditionalOnMissingBean(RateLimiterFactory.class)
    @ConditionalOnProperty(prefix = "clazs.ratelimiter", name = "storage-type", havingValue = "local", matchIfMissing = true)
    public RateLimiterFactory localRateLimiterFactory() {
        log.info("初始化 LocalRateLimiterFactory Bean (本地内存模式)");
        return new LocalRateLimiterFactory();
    }

    /**
     * Redis 配置类 (仅当引入了 Redis 依赖且 storage-type 为 redis 时生效)
     */
    @Configuration
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "clazs.ratelimiter", name = "storage-type", havingValue = "redis")
    static class RedisConfiguration {
        @Bean
        @ConditionalOnMissingBean(RateLimiterFactory.class)
        public RateLimiterFactory redisRateLimiterFactory(StringRedisTemplate redisTemplate) {
            log.info("初始化 RedisRateLimiterFactory Bean (分布式Redis模式)");
            return new RedisRateLimiterFactory(redisTemplate);
        }
    }

    /**
     * 注册限流器注册中心 Bean
     *
     * @param properties 从 application.yml 读取的配置属性
     * @param factory 限流器工厂
     * @return 限流器注册中心
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitRegistry rateLimitRegistry(RateLimiterProperties properties, RateLimiterFactory factory) {
        log.info("初始化 RateLimitRegistry Bean");

        // 验证配置
        properties.validate();

        // 创建注册中心
        RateLimitRegistry registry = new RateLimitRegistry(properties, factory);

        log.info("RateLimitRegistry Bean 创建成功");
        return registry;
    }

    /**
     * 注册限流切面 Bean
     *
     * @param registry 限流器注册中心
     * @return 限流切面
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(RateLimitRegistry registry) {
        log.info("初始化 RateLimitAspect Bean");

        // 创建切面
        RateLimitAspect aspect = new RateLimitAspect(registry);

        log.info("RateLimitAspect Bean 创建成功");
        return aspect;
    }
}
