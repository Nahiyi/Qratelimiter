package cn.clazs.qratelimiter.aspect;

import cn.clazs.qratelimiter.annotation.DoRateLimit;
import cn.clazs.qratelimiter.exception.RateLimitException;
import cn.clazs.qratelimiter.registry.RateLimitRegistry;
import cn.clazs.qratelimiter.value.UserLimiter;
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
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 限流切面
 * 拦截标注了 @DoRateLimit 注解的方法，进行限流控制
 *
 * <p>核心功能：
 * <ul>
 *     <li>解析 SpEL 表达式获取限流 Key</li>
 *     <li>从注册中心获取用户的限流器</li>
 *     <li>判断是否允许请求</li>
 *     <li>限流时抛出 RateLimitException</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * {@code
 * @DoRateLimit(key = "#userId")
 * public String getUserInfo(String userId) {
 *     return "info";
 * }
 * }
 * </pre>
 *
 * @author clazs
 * @since 1.0
 */
@Slf4j
@Aspect
@Component
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

        // 解析SpEL表达式获取限流Key
        String key = parseKey(doRateLimit.key(), method, args, parameterNames);

        if (log.isDebugEnabled()) {
            log.debug("限流拦截：method={}, key={}", method.getName(), key);
        }

        // 获取或创建用户的限流器
        UserLimiter limiter = getLimiter(doRateLimit, key);

        // 判断是否允许请求
        boolean allowed = limiter.allowRequest();

        if (!allowed) {
            // 限流触发，抛出异常
            String message = doRateLimit.message();
            log.warn("限流触发：key={}, method={}", key, method.getName());
            throw new RateLimitException(key, message);
        }

        // 允许通过，执行目标方法
        return joinPoint.proceed();
    }


    /**
     * 获取限流器（支持自定义配置覆盖全局配置）
     *
     * @param doRateLimit 限流注解
     * @param key 限流 Key
     * @return 用户的限流器
     */
    private UserLimiter getLimiter(DoRateLimit doRateLimit, String key) {
        // 这里暂时简化处理，直接使用全局配置
        // 下步 TODO：支持创建同key，同方法级别

        // 直接从注册中心获取限流器（使用全局配置）
        return rateLimitRegistry.getLimiter(key);
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
