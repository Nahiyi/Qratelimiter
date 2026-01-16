# 待实现功能清单 (TODOs)

## 优先级：高

### ~~1. 支持方法级别的精细化限流配置~~

**问题描述**：

当前 `@DoRateLimit` 注解虽然支持自定义配置（freq、interval、capacity），但 `RateLimitAspect` 完全忽略了这些配置，所有方法都使用全局配置（yml 中的配置）。

**核心矛盾**：

如果同一个 userId 对应不同的限流配置，会存在冲突：

```java
@DoRateLimit(key = "api1", freq = 10)   // api1: 10次/分钟
public void method1() {}

@DoRateLimit(key = "api1", freq = 100)  // api1: 100次/分钟（期望创建新限流器，但实际复用了上面的）
public void method2() {}
```

由于 Caffeine Cache 的 key 只是 `"api1"`，第二个方法会复用第一个限流器（10次/分钟），而不是创建新的限流器（100次/分钟）。

**解决方案**：

在 `RateLimitRegistry` 中添加重载方法，支持自定义配置，并使用**复合 Key**：

```java
/**
 * 获取限流器（使用全局配置）
 */
public UserLimiter getLimiter(String userId) {}

/**
 * 获取限流器（使用自定义配置）
 *
 * @param userId    用户ID
 * @param freq      频率限制
 * @param interval  时间窗口（毫秒）
 * @param capacity  容量
 * @return 限流器实例
 */
public UserLimiter getLimiter(String userId, int freq, long interval, int capacity) {}
```

**复合 Key 格式**：

```
userId + "|" + freq + "|" + interval + "|" + capacity
```

示例：
- `"api1|10|60000|15"` → 10次/分钟，容量15
- `"api1|100|60000|150"` → 100次/分钟，容量150
- 两个是**不同的限流器**，互不影响！

**实现步骤**：

1. 修改 `RateLimitRegistry`
   - 添加 `getLimiter(userId, freq, interval, capacity)` 方法
   - 生成复合 Key：`String.format("%s|%d|%d|%d", userId, freq, interval, capacity)`
   - 使用复合 Key 从缓存获取/创建限流器

2. 修改 `RateLimitAspect`
   - 在 `getLimiter()` 方法中判断注解是否有自定义配置
   - 如果 `freq > 0` 且 `interval > 0` 且 `capacity > 0`，使用自定义配置
   - 否则使用全局配置（兼容当前行为）

3. 添加测试用例
   - 测试同一 userId 的不同配置限流器
   - 验证限流器独立性
   - 验证限流行为正确性

**预期效果**：

```java
// yml 全局配置：freq=100, interval=60000, capacity=150

@DoRateLimit(key = "#userId", freq = 10)   // 10次/分钟（覆盖全局配置）
public String highFrequencyAPI(String userId) {
    return "高频API";
}

@DoRateLimit(key = "#userId", freq = 1000) // 1000次/分钟（覆盖全局配置）
public String lowFrequencyAPI(String userId) {
    return "低频API";
}
```

**备注**：

- 全局配置（yml）作为兜底默认值
- 注解中的自定义配置优先级更高
- 保持向后兼容：如果注解中未指定自定义配置（使用默认值 -1），则使用全局配置

---

## 优先级：中

### 2. 创建 AutoConfiguration（Level 4）

**问题描述**：

当前 `RateLimitRegistry` 和 `RateLimitAspect` 还不是 Spring Bean，无法通过 `@Autowired` 注入。

**解决方案**：

创建 `RateLimiterAutoConfiguration` 类，统一管理所有 Bean：

```java
@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RateLimiterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RateLimitRegistry rateLimitRegistry(RateLimiterProperties properties) {
        return new RateLimitRegistry(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(RateLimitRegistry registry) {
        return new RateLimitAspect(registry);
    }
}
```

**实现步骤**：

1. 创建 `RateLimiterAutoConfiguration` 类
2. 创建 `spring.factories` 或 `AutoConfiguration.imports` 文件
3. 添加测试验证自动装配

---

## 优先级：低

### ~~3. 支持动态配置刷新~~(暂不考虑)

---

### ~~4. 支持分布式限流~~(暂不考虑)

**问题描述**：

当前限流器是单机内存实现，集群环境下每台机器独立限流，无法实现全局限流。

**解决方案**：

使用 Redis 实现分布式限流器，共享限流状态。

**实现步骤**：

1. 定义 `DistributedRateLimiter` 接口
2. 实现 `RedisRateLimiter`（基于 Redis + Lua 脚本）
3. 提供配置项切换本地/分布式限流
4. 保持 API 兼容性

**备注**：

- 本例可能暂不考虑
- 需要引入 Redis 依赖
- 性能会略低于本地限流（网络开销）
- 适合集群部署场景

---

### 5. 支持多种限流算法

**问题描述**：

当前只实现了滑动窗口日志算法，用户可能需要其他算法（如令牌桶、漏桶等）。

**解决方案**：

提供算法切换配置，支持多种限流算法；尝试利用“策略模式”，“工厂模式”等，面向开闭原则与OOP思想拓展。

**可选算法**：

- 滑动窗口日志（当前实现）
- 令牌桶算法（Token Bucket）
- 漏桶算法（Leaky Bucket）
- 固定窗口算法（Fixed Window）

**实现步骤**：

1. 定义 `RateLimiter` 接口
2. 实现多种算法
3. 在配置中指定算法类型
4. 工厂模式创建对应限流器

---

## 最后更新

- 更新时间：2026-01-16
- 当前版本：v1.0-SNAPSHOT
