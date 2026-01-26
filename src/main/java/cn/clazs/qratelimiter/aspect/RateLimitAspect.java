package cn.clazs.qratelimiter.aspect;

import cn.clazs.qratelimiter.annotation.DoRateLimit;
import cn.clazs.qratelimiter.annotation.RateLimitScope;
import cn.clazs.qratelimiter.core.RateLimiter;
import cn.clazs.qratelimiter.enums.RateLimitAlgorithm;
import cn.clazs.qratelimiter.enums.RateLimitStorage;
import cn.clazs.qratelimiter.exception.RateLimitException;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

/**
 * 限流切面
 * 拦截标注了 @DoRateLimit 注解的方法，进行限流控制
 *
 * <p>核心功能：
 * <ul>
 *     <li>解析 SpEL 表达式获取限流 Key</li>
 *     <li>生成复合Key（全限定类名.方法名:业务Key）实现方法级别隔离</li>
 *     <li>支持自定义限流配置（覆盖全局配置）</li>
 *     <li>从注册中心获取限流器</li>
 *     <li>判断是否允许请求</li>
 *     <li>限流时抛出 RateLimitException</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * {@code
 * // 使用全局配置
 * @DoRateLimit(key = "#userId")
 * public String getUserInfo(String userId) {
 *     return "info";
 * }
 *
 * // 使用自定义配置（覆盖全局配置）
 * @DoRateLimit(key = "#userId", freq = 1000, interval = 60000, capacity = 1500)
 * public String highFrequencyAPI(String userId) {
 *     return "vip api";
 * }
 * }
 * </pre>
 *
 * <p>注意：
 * <ul>
 *     <li>不同方法的限流器是独立的，互不影响</li>
 *     <li>复合Key格式：全限定类名.方法名:业务Key</li>
 *     <li>注解中的自定义配置会覆盖全局配置</li>
 * </ul>
 *
 * @author clazs
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class RateLimitAspect {

    /**
     * 限流器注册中心
     */
    private final RateLimitRegistry rateLimitRegistry;

    /**
     * SpEL 表达式解析器
     */
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 参数名称发现器（用于获取方法参数名）
     */
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Autowired
    public RateLimitAspect(RateLimitRegistry rateLimitRegistry) {
        this.rateLimitRegistry = rateLimitRegistry;
    }

    /**
     * 环绕通知：拦截`@DoRateLimit`注解的方法
     *
     * @param joinPoint AOP连接点
     * @param doRateLimit 限流注解
     * @throws Throwable 执行异常或限流异常
     */
    @Around("@annotation(doRateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, DoRateLimit doRateLimit) throws Throwable {
        // 获取方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] parameterNames = nameDiscoverer.getParameterNames(method);

        // 解析SpEL表达式获取业务Key
        String bizKey = parseKey(doRateLimit.key(), method, args, parameterNames);

        // 根据 scope 决定 Key 的生成策略
        String finalKey;
        if (doRateLimit.scope() == RateLimitScope.GLOBAL) {
            // 全局模式：直接使用 bizKey，不同方法间共享额度
            finalKey = bizKey;
            if (log.isDebugEnabled()) {
                log.debug("限流拦截（GLOBAL模式）：method={}, bizKey={}, finalKey={}",
                        method.getName(), bizKey, finalKey);
            }
        } else {
            // 方法模式（默认）：拼接类名方法名
            // 例如：cn.clazs.UserController.getUserInfo:1001
            finalKey = method.getDeclaringClass().getName() + "." + method.getName() + ":" + bizKey;
            if (log.isDebugEnabled()) {
                log.debug("限流拦截（METHOD模式）：method={}, bizKey={}, finalKey={}",
                        method.getName(), bizKey, finalKey);
            }
        }

        // 获取或创建限流器（支持自定义配置）
        RateLimiter limiter = getLimiter(doRateLimit, finalKey);

        // 获取限流器配置
        int freq = limiter.getConfig().getFreq();
        long interval = limiter.getConfig().getInterval();
        int capacity = limiter.getConfig().getCapacity();

        // 判断是否允许请求
        boolean allowed = limiter.allowRequest(finalKey, freq, interval, capacity);

        if (!allowed) {
            // 限流触发，抛出异常
            String message = doRateLimit.message();
            RateLimitAlgorithm algorithm = limiter.getAlgorithm();
            RateLimitStorage storage = limiter.getStorage();
            log.warn("限流触发：finalKey={}, bizKey={}, method={}, scope={}, algorithm={}, storage={}",
                    finalKey, bizKey, method.getName(), doRateLimit.scope(), algorithm, storage);
            throw new RateLimitException(bizKey, message);
        }

        // 允许通过，执行目标方法
        return joinPoint.proceed();
    }


    /**
     * 获取限流器（支持自定义配置覆盖全局配置）
     *
     * <p>判断逻辑：
     * <ul>
     *     <li>如果注解中指定了自定义配置（freq>0 && interval>0），使用自定义配置</li>
     *     <li>capacity 如果用户未指定（默认值 -1），RateLimitRegistry 会自动计算为 freq * 1.5</li>
     *     <li>否则使用全局配置（从yml读取）</li>
     * </ul>
     *
     * <p>设计理念：用户只需关心业务参数（频率和时间窗口），不应被迫关心内部实现细节（capacity）
     *
     * @param doRateLimit 限流注解
     * @param finalKey 最终Key：可能是全限定类名.方法名:业务Key；也可能是直接业务Key
     * @return 限流器
     */
    private RateLimiter getLimiter(DoRateLimit doRateLimit, String finalKey) {
        int freq = doRateLimit.freq();
        long interval = doRateLimit.interval();
        boolean hasCustomConfig = freq > 0 && interval > 0;

        if (hasCustomConfig) {
            log.debug("使用自定义配置创建限流器：key={}, freq={}, interval={}ms, capacity={}",
                    finalKey, freq, interval, doRateLimit.capacity());

            // RateLimitRegistry 会自动处理 capacity 的计算（若<=0）
            return rateLimitRegistry.getLimiter(
                    finalKey,
                    freq,
                    interval,
                    doRateLimit.capacity()
            );
        } else {
            // 使用全局配置创建限流器
            log.debug("使用全局配置创建限流器：key={}", finalKey);
            return rateLimitRegistry.getLimiter(finalKey);
        }
    }

    /**
     * 解析 SpEL 表达式获取限流 Key
     *
     * <p>支持以下表达式：
     * <ul>
     *     <li>{@code #userId} - 获取方法参数 userId 的值</li>
     *     <li>{@code #user.id} - 获取方法参数 user 对象的 id 属性</li>
     *     <li>{@code 'constant'} - 常量字符串（单引号）</li>
     * </ul>
     *
     * @param keyExpression SpEL 表达式
     * @param method 目标方法
     * @param args 方法参数值
     * @param parameterNames 方法参数名
     * @return 解析后的 Key
     */
    private String parseKey(String keyExpression, Method method, Object[] args, String[] parameterNames) {
        // 如果表达式不包含 #，则认为是常量字符串，直接返回
        if (!keyExpression.contains("#")) {
            return keyExpression;
        }

        // 创建 SpEL 上下文
        EvaluationContext context = new StandardEvaluationContext();

        // 设置方法参数到上下文
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        // 解析表达式
        Expression expression = parser.parseExpression(keyExpression);
        Object value = expression.getValue(context);

        if (value == null) {
            throw new IllegalArgumentException("无法解析限流 Key，SpEL 表达式: " + keyExpression);
        }

        return value.toString();
    }
}
