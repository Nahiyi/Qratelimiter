package cn.clazs.qratelimiter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解
 * 用于标记需要进行限流的方法
 *
 * <p>使用示例：
 * <pre>{@code
 * // 1. 直接指定 key
 * @DoRateLimit(key = "user123")
 * public String getUserInfo() {
 *     return "info";
 * }
 *
 * // 2. 使用 SpEL 表达式（推荐）
 * @DoRateLimit(key = "#userId")
 * public String getUserInfo(String userId) {
 *     return "info";
 * }
 *
 * // 3. 使用对象属性
 * @DoRateLimit(key = "#user.id")
 * public String updateUser(User user) {
 *     return "success";
 * }
 *
 * // 4. 自定义配置（覆盖全局配置）
 * @DoRateLimit(key = "#apiId", freq = 1000, interval = 60000, capacity = 1500)
 * public String callApi(String apiId) {
 *     return "response";
 * }
 * }</pre>
 *
 * @author clazs
 * @since 1.0
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DoRateLimit {

    /**
     * 限流Key（支持SpEL表达式）
     *
     * <p>SpEL 表达式示例：
     * <ul>
     *     <li>{@code #userId} - 获取方法参数 userId 的值</li>
     *     <li>{@code #user.id} - 获取方法参数 user 对象的 id 属性</li>
     *     <li>{@code #request.remoteAddr} - 获取 request 对象的 remoteAddr 属性</li>
     *     <li>{@code 'constant_key'} - 使用常量字符串（注意单引号）</li>
     * </ul>
     *
     * @return SpEL 表达式或常量字符串
     */
    String key();

    /**
     * 限流范围策略（可选）
     *
     * <p>控制不同方法之间的限流器隔离策略：
     * <ul>
     *     <li>{@link RateLimitScope#METHOD}（默认）：方法级隔离，每个方法独立限流</li>
     *     <li>{@link RateLimitScope#GLOBAL}：全局共享，不同方法共享限流器</li>
     * </ul>
     *
     * <p>示例：
     * <pre>{@code
     * // 默认：方法级隔离（推荐）
     * @DoRateLimit(key = "#userId")
     * public void methodA(String userId) {}  // 独立限流
     *
     * @DoRateLimit(key = "#userId")
     * public void methodB(String userId) {}  // 独立限流，不受methodA影响
     *
     * // 全局共享：方法间共享额度
     * @DoRateLimit(key = "#userId", scope = RateLimitScope.GLOBAL, freq = 100)
     * public void methodA(String userId) {}  // 共享限流器
     *
     * @DoRateLimit(key = "#userId", scope = RateLimitScope.GLOBAL)
     * public void methodB(String userId) {}  // 共享限流器，与methodA抢占额度
     * }</pre>
     *
     * @return 限流范围策略
     */
    RateLimitScope scope() default RateLimitScope.METHOD;

    /**
     * 时间窗口内最大允许访问次数（可选，默认使用全局配置）
     * 如果设置为负数，则使用全局配置的值
     */
    int freq() default -1;

    /**
     * 时间窗口长度，单位：毫秒（可选，默认使用全局配置）
     * 如果设置为负数，则使用全局配置的值
     */
    long interval() default -1;

    /**
     * 单个限流器队列容量（可选，自动计算或使用全局配置）
     *
     * <p>容量说明：
     * <ul>
     *     <li>如果用户指定了 {@code freq} 和 {@code interval}，但未指定 {@code capacity}（默认值 -1），
     *         系统会自动计算为 {@code freq * 1.5}，预留缓冲空间</li>
     *     <li>如果用户指定了 {@code capacity > 0}，使用用户指定的值（必须 >= freq）</li>
     *     <li>如果未指定 {@code freq} 和 {@code interval}，使用全局配置的值</li>
     * </ul>
     *
     * <p>注意：通常情况下用户无需手动指定此参数，系统会自动计算合理值
     */
    int capacity() default -1;

    /**
     * 限流失败时的错误信息（可选）
     */
    String message() default "访问过于频繁，请稍后再试";
}
