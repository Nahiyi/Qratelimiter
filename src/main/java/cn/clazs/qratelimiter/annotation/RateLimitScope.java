package cn.clazs.qratelimiter.annotation;

/**
 * 限流范围策略
 *
 * <p>控制不同方法之间的限流器隔离策略，平衡隔离性和资源复用
 *
 * @author clazs
 * @since 1.0
 */
public enum RateLimitScope {

    /**
     * 方法级隔离（默认）
     *
     * <p>Key 格式：全限定类名.方法名:业务Key
     * <p>示例：cn.clazs.controller.UserController.getUserInfo:123
     *
     * <p>特点：
     * <ul>
     *     <li>每个方法的限流器完全独立</li>
     *     <li>不同方法互不影响，实现"舱壁模式"（Bulkhead Pattern）</li>
     *     <li>接口A被限流不影响接口B</li>
     *     <li>默认推荐，安全性高</li>
     * </ul>
     *
     * <p>使用场景：
     * <ul>
     *     <li>大多数业务场景</li>
     *     <li>不同接口重要性不同</li>
     *     <li>避免"无辜躺枪"</li>
     * </ul>
     *
     * <p>示例：
     * <pre>
     * {@code
     * // 方法A和 方法B 各自独立限流
     * @DoRateLimit(key = "#userId", scope = RateLimitScope.METHOD)
     * public void methodA(String userId) {}
     *
     * @DoRateLimit(key = "#userId", scope = RateLimitScope.METHOD)
     * public void methodB(String userId) {}
     * }
     * </pre>
     */
    METHOD,

    /**
     * 全局共享
     *
     * <p>Key 格式：业务Key（不拼接类名和方法名）
     * <p>示例：userId = 123
     *
     * <p>特点：
     * <ul>
     *     <li>所有标记为 GLOBAL 的方法共享同一个限流器</li>
     *     <li>不同方法间抢占额度</li>
     *     <li>资源复用，但存在"雪崩风险"</li>
     * </ul>
     *
     * <p>使用场景：
     * <ul>
     *     <li>限制用户全站总访问频率</li>
     *     <li>VIP用户全站统一限流</li>
     *     <li>需要方法间共享额度的特殊场景</li>
     * </ul>
     *
     * <p>⚠️ 警告：
     * 使用 GLOBAL 时，一个方法被高频访问可能导致其他方法也被限流
     *
     * <p>示例：
     * <pre>
     * {@code
     * // 方法A和方法B共享限流器，总共100次/分钟
     * @DoRateLimit(key = "#userId", scope = RateLimitScope.GLOBAL, freq = 100)
     * public void methodA(String userId) {}
     *
     * @DoRateLimit(key = "#userId", scope = RateLimitScope.GLOBAL, freq = 100)
     * public void methodB(String userId) {}
     * }
     * </pre>
     *
     * <p>场景示例：
     * <pre>
     * {@code
     * // 需求：VIP用户全站每秒只能发起1000次请求，不管访问哪个接口
     * @DoRateLimit(key = "#userId", scope = RateLimitScope.GLOBAL, freq = 1000, interval = 1000)
     * public void queryUserInfo(String userId) {}
     *
     * @DoRateLimit(key = "#userId", scope = RateLimitScope.GLOBAL)
     * public void createOrder(String userId) {}
     * }
     * </pre>
     */
    GLOBAL
}
