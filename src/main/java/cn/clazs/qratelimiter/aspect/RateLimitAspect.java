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
 *     <li>从注册中心获取用户的限流器</li>
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
 * @since 1.0
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

        // 生成复合Key：全限定类名.方法名:业务Key
        // 例如：cn.clazs.controller.UserController.getUserInfo:123
        String compositeKey = method.getDeclaringClass().getName() + "." + method.getName() + ":" + bizKey;

        if (log.isDebugEnabled()) {
            log.debug("限流拦截：method={}, bizKey={}, compositeKey={}",
                    method.getName(), bizKey, compositeKey);
        }

        // 获取或创建用户的限流器（支持自定义配置）
        UserLimiter limiter = getLimiter(doRateLimit, compositeKey);

        // 判断是否允许请求
        boolean allowed = limiter.allowRequest();

        if (!allowed) {
            // 限流触发，抛出异常
            String message = doRateLimit.message();
            log.warn("限流触发：compositeKey={}, bizKey={}, method={}",
                    compositeKey, bizKey, method.getName());
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
     *     <li>如果注解中指定了自定义配置（freq>0 && interval>0 && capacity>0），使用自定义配置</li>
     *     <li>否则使用全局配置（从yml读取）</li>
     * </ul>
     *
     * @param doRateLimit 限流注解
     * @param compositeKey 复合Key（全限定类名.方法名:业务Key）
     * @return 用户的限流器
     */
    private UserLimiter getLimiter(DoRateLimit doRateLimit, String compositeKey) {
        // 判断是否使用了自定义配置（默认值 -1 表示使用全局配置）
        boolean hasCustomConfig = doRateLimit.freq() > 0
                && doRateLimit.interval() > 0
                && doRateLimit.capacity() > 0;

        if (hasCustomConfig) {
            // 使用自定义配置创建限流器
            log.debug("使用自定义配置创建限流器：key={}, freq={}, interval={}ms, capacity={}",
                    compositeKey, doRateLimit.freq(), doRateLimit.interval(), doRateLimit.capacity());
            return rateLimitRegistry.getLimiter(
                    compositeKey,
                    doRateLimit.freq(),
                    doRateLimit.interval(),
                    doRateLimit.capacity()
            );
        } else {
            // 使用全局配置创建限流器
            log.debug("使用全局配置创建限流器：key={}", compositeKey);
            return rateLimitRegistry.getLimiter(compositeKey);
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
