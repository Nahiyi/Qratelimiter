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
     * 单个限流器队列容量（可选，默认使用全局配置）
     * 如果设置为负数，则使用全局配置的值
     * 注意：capacity 必须 >= freq，否则会抛出异常
     */
    int capacity() default -1;

    /**
     * 限流失败时的错误信息（可选）
     */
    String message() default "访问过于频繁，请稍后再试";
}
