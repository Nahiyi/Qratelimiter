package cn.clazs.qratelimiter.autoconfigure;

import cn.clazs.qratelimiter.aspect.RateLimitAspect;
import cn.clazs.qratelimiter.exception.DefaultRateLimitExceptionHandler;
import cn.clazs.qratelimiter.factory.LimiterExecutorFactory;
import cn.clazs.qratelimiter.properties.RateLimiterProperties;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
 *     <li>自动注册 {@link LimiterExecutorFactory} 执行器工厂 Bean</li>
 *     <li>自动注册 {@link RateLimitRegistry} 限流器注册中心 Bean</li>
 *     <li>自动注册 {@link RateLimitAspect} AOP 切面 Bean</li>
 *     <li>支持通过 {@code clazs.ratelimiter.enabled=false} 关闭自动配置</li>
 *     <li>支持用户自定义 Bean 覆盖（@ConditionalOnMissingBean）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 1. 添加依赖（pom.xml）
 * <dependency>
 *     <groupId>cn.clazs</groupId>
 *     <artifactId>qratelimiter-spring-boot-starter</artifactId>
 *     <version>1.0.0</version>
 * </dependency>
 *
 * // 2. 配置 application.yml
 * clazs:
 *   ratelimiter:
 *     enabled: true
 *     freq: 100
 *     interval: 60000
 *     capacity: 150
 *     algorithm: sliding-window-log
 *     storage: local
 *
 * // 3. 直接使用注解
 * @RestController
 * public class UserController {
 *     @DoRateLimit(key = "#userId")
 *     @GetMapping("/info")
 *     public String getUserInfo(String userId) {
 *         return "info";
 *     }
 * }
 * }</pre>
 *
 * <p>自动配置原理：
 * <ul>
 *     <li>Spring Boot 启动时，通过 {@code spring.factories} 加载此类</li>
 *     <li>{@link EnableConfigurationProperties} 启用配置属性绑定</li>
 *     <li>{@link ConditionalOnProperty} 控制自动配置的启用条件</li>
 *     <li>{@link ConditionalOnMissingBean} 允许用户自定义 Bean 覆盖</li>
 * </ul>
 *
 * @author clazs
 * @since 1.0.0
 * @see RateLimiterProperties
 * @see LimiterExecutorFactory
 * @see RateLimitRegistry
 * @see RateLimitAspect
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
@ConditionalOnProperty(prefix = "clazs.ratelimiter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimiterAutoConfiguration {

    /**
     * 注册限流执行器工厂 Bean
     *
     * <p>职责：
     * <ul>
     *     <li>根据算法类型和存储类型创建对应的执行器实例</li>
     *     <li>缓存执行器实例，避免重复创建</li>
     *     <li>支持 Local 和 Redis 两种存储方式</li>
     *     <li>支持多种限流算法（滑动窗口日志、令牌桶、漏桶等）</li>
     * </ul>
     *
     * <p>条件注解说明：
     * <ul>
     *     <li>只有当 Spring 容器中不存在 {@link LimiterExecutorFactory} Bean 时，才会创建</li>
     *     <li>允许用户自定义 {@link LimiterExecutorFactory} Bean 覆盖默认实现</li>
     * </ul>
     *
     * @param properties 从 application.yml 读取的配置属性
     * @return 限流执行器工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public LimiterExecutorFactory limiterExecutorFactory(RateLimiterProperties properties) {
        log.info("初始化 LimiterExecutorFactory Bean，配置：{}", properties.getSummary());

        // 创建工厂实例
        LimiterExecutorFactory factory = new LimiterExecutorFactory(properties);

        log.info("LimiterExecutorFactory Bean 创建成功");
        return factory;
    }

    /**
     * 注册 Redis 模板注入器（如果存在 Redis 依赖）
     *
     * <p>此方法会在 Redis 依赖存在时被调用，用于将 RedisTemplate 注入到工厂中
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
    public Object redisTemplateInitializer(
            LimiterExecutorFactory factory,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            factory.setRedisTemplate(redisTemplate);
            log.info("Redis 模板已注入到限流器工厂");
        } else {
            log.info("未检测到 Redis 模板，限流器将仅支持本地存储");
        }
        return new Object(); // 返回一个标记对象，仅用于触发注入
    }

    /**
     * 注册限流器注册中心 Bean
     *
     * <p>职责：
     * <ul>
     *     <li>管理所有限流器实例（使用 Caffeine 缓存）</li>
     *     <li>自动清理不活跃的限流器（防止内存泄漏）</li>
     *     <li>支持方法级别的自定义配置</li>
     *     <li>支持多种算法和存储方式</li>
     * </ul>
     *
     * <p>条件注解说明：
     * <ul>
     *     <li>只有当 Spring 容器中不存在 {@link RateLimitRegistry} Bean 时，才会创建</li>
     *     <li>允许用户自定义 {@link RateLimitRegistry} Bean 覆盖默认实现</li>
     * </ul>
     *
     * @param properties 从 application.yml 读取的配置属性
     * @param executorFactory 执行器工厂
     * @return 限流器注册中心
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitRegistry rateLimitRegistry(RateLimiterProperties properties,
                                               LimiterExecutorFactory executorFactory) {
        log.info("初始化 RateLimitRegistry Bean，配置：{}", properties.getSummary());

        // 验证配置
        properties.validate();

        // 创建注册中心
        RateLimitRegistry registry = new RateLimitRegistry(properties, executorFactory);

        log.info("RateLimitRegistry Bean 创建成功");
        return registry;
    }

    /**
     * 注册限流切面 Bean
     *
     * <p>职责：
     * <ul>
     *     <li>拦截标注了 {@link cn.clazs.qratelimiter.annotation.DoRateLimit} 注解的方法</li>
     *     <li>解析 SpEL 表达式获取限流 Key</li>
     *     <li>调用 {@link RateLimitRegistry} 获取限流器并判断是否允许请求</li>
     *     <li>限流时抛出 {@link cn.clazs.qratelimiter.exception.RateLimitException}</li>
     * </ul>
     *
     * <p>条件注解说明：
     * <ul>
     *     <li>只有当 Spring 容器中不存在 {@link RateLimitAspect} Bean 时，才会创建</li>
     *     <li>允许用户自定义 {@link RateLimitAspect} Bean 覆盖默认实现</li>
     * </ul>
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

    /**
     * 注册默认限流异常处理器 Bean
     *
     * <p>职责：
     * <ul>
     *     <li>全局捕获 {@link cn.clazs.qratelimiter.exception.RateLimitException}</li>
     *     <li>返回 HTTP 429 (Too Many Requests) 状态码</li>
     *     <li>避免在日志中打印冗长的异常堆栈</li>
     * </ul>
     *
     * <p>条件注解说明：
     * <ul>
     *     <li>只有在 Web 环境下才会创建</li>
     *     <li>只有当 Spring 容器中不存在 {@link DefaultRateLimitExceptionHandler} Bean 时，才会创建</li>
     *     <li>允许用户自定义异常处理器覆盖默认实现</li>
     * </ul>
     *
     * @return 默认限流异常处理器
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    @ConditionalOnMissingBean
    public DefaultRateLimitExceptionHandler defaultRateLimitExceptionHandler() {
        log.info("初始化 DefaultRateLimitExceptionHandler Bean");

        DefaultRateLimitExceptionHandler handler = new DefaultRateLimitExceptionHandler();

        log.info("DefaultRateLimitExceptionHandler Bean 创建成功");
        return handler;
    }
}
