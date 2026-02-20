# Q-RateLimiter

![QRateLimiter Logo](https://img.shields.io/badge/QRateLimiter-Rate%20Limiter-brightgreen)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18%2B-brightgreen)
![Java](https://img.shields.io/badge/Java-1.8%2B-orange)
![License](https://img.shields.io/badge/License-MIT-blue)
![Tests](https://img.shields.io/badge/Tests-100%25%20Passing-success)

**一款轻量级、高性能、可拓展、开箱即用的 Spring Boot 限流器 Starter**（默认限流算法基于环形缓冲区 + 二分查找算法实现）

[特性](#特性) • [快速开始](#快速开始) • [配置说明](#配置说明) • [核心原理](#核心原理) • [性能测试](#性能测试) • [FAQ](#)

---

## 目录

- [简介](#简介)
- [特性](#特性)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [使用示例](#使用示例)
- [核心原理](#核心原理)
- [性能测试](#性能测试)
- [架构设计](#架构设计)
- [常见问题](#常见问题)
- [TODOs](#todos)
- [许可证](#许可证)

---

## 简介

**QRateLimiter** 是一款专为 Spring Boot 应用设计的轻量级限流器，采用**滑动窗口日志算法**，基于**环形缓冲区 + 二分查找**实现。

### 核心特性

- **桥接模式架构**：算法与存储解耦，支持灵活组合扩展
- **多存储支持**：本地内存 / Redis 分布式存储
- **极致性能**：环形缓冲区 + 二分查找，时间复杂度 O(log n)
- **精准限流**：基于滑动窗口，支持毫秒级时间粒度
- **开箱即用**：引入 starter 依赖即可使用，内置全局异常处理器
- **高度可靠**：线程安全，支持时钟回拨检测
- **灵活配置**：支持全局配置 + 方法级自定义配置
- **零侵入**：基于注解和 AOP，对业务代码零侵入

---

## 特性

### 核心特性

| 特性       | 说明                               |
|----------|----------------------------------|
| **高性能**  | 环形缓冲区 + 二分查找，时间复杂度 O(log n)      |
| **低内存**  | 每个限流器仅需 `freq * 1.5` 个 long 数组元素 |
| **精准限流** | 滑动窗口算法，支持任意时间粒度（毫秒级）             |
| **自动管理** | 基于 Caffeine Cache，自动清理过期限流器实例    |
| **灵活隔离** | 支持方法级隔离（默认）和全局限流两种模式             |
| **动态配置** | 支持全局配置和方法级自定义配置覆盖                |

### 技术亮点

- **桥接模式架构**：算法与存储彻底解耦，支持灵活组合
- **多存储支持**：本地内存（单机）/ Redis（分布式）
- **线程安全**：完全并发安全的限流算法
- **轻量级占用**：基于 long 型数组作为核心数据结构
- **时钟回拨检测**：防止时钟回拨导致限流失效
- **参数校验**：启动时校验配置参数合法性
- **全局异常处理**：内置默认异常处理器，返回 HTTP 429
- **缓存优化**：Caffeine 高性能缓存，支持 LRU 淘汰
- **开箱即用**：基于 AOP 以及定义 starter 带来开箱即用的体验

---

## 快速开始

### Maven 依赖

```xml

<dependency>
    <groupId>cn.clazs</groupId>
    <artifactId>qratelimiter-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 1. 添加配置

在 `application.yml` 中添加配置：

```yaml
clazs:
  ratelimiter:
    enabled: true                    # 是否启用限流器
    freq: 100                        # 时间窗口内最大请求次数
    interval: 60000                  # 时间窗口长度（毫秒）= 1分钟
    capacity: 150                    # 队列容量（建议 freq * 1.5）
    algorithm: sliding-window-log    # 限流算法（目前仅支持 sliding-window-log）
    storage: local                   # 存储类型：local=本地内存，redis=分布式
    cache-expire-after-access-minutes: 30   # 缓存过期时间
    cache-maximum-size: 1000                 # 最大缓存数量
```

### 2. 使用注解

在需要限流的接口上添加 `@DoRateLimit` 注解：

```java
@RestController
@RequestMapping("/user")
public class UserController {

    /**
     * 根据用户ID限流：每个用户每分钟最多访问 10 次
     */
    @GetMapping("/info")
    @DoRateLimit(key = "#userId")
    public UserInfo getUserInfo(@RequestParam("userId") Long userId) {
        return userService.getUserInfo(userId);
    }

    /**
     * 高频接口：每个用户每秒最多访问 5 次（自定义配置）
     */
    @GetMapping("/high-freq")
    @DoRateLimit(key = "#userId", freq = 5, interval = 1000)
    public String highFrequencyApi(@RequestParam("userId") Long userId) {
        return "高频接口调用成功";
    }

    /**
     * 全局限流：所有用户共享限流配额
     */
    @GetMapping("/global")
    @DoRateLimit(key = "'global_api'", freq = 20, interval = 60000)
    public String globalRateLimit() {
        return "全局限流接口";
    }
}
```

### 3. 处理限流异常

> 限流逻辑本身应当为“业务逻辑”的一部分，所以无需抛出异常，打印过多无用干扰堆栈，记载限流触发日志、返回对应状态码（429）即可。

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<String> handleRateLimit(RateLimitException e) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)  // 429
                .body("请求过于频繁，请稍后再试");
    }
}
```

**使用很简单！**

---

## 配置说明

### 全局配置（application.yml）

| 配置项                                                   | 说明          | 默认值                  | 可选值                  | 建议                   |
|-------------------------------------------------------|-------------|----------------------|----------------------|----------------------|
| `clazs.ratelimiter.enabled`                           | 是否启用限流器     | `true`               | -                    | 生产环境开启               |
| `clazs.ratelimiter.freq`                              | 时间窗口内最大请求次数 | `100`                | -                    | 根据业务调整               |
| `clazs.ratelimiter.interval`                          | 时间窗口长度（毫秒）  | `60000`              | -                    | 根据业务调整               |
| `clazs.ratelimiter.capacity`                          | 队列容量        | `150`                | -                    | 建议为 freq 的 1.5 倍     |
| `clazs.ratelimiter.algorithm`                         | 限流算法        | `sliding-window-log` | `sliding-window-log` | 目前仅支持滑动窗口日志          |
| `clazs.ratelimiter.storage`                           | 存储类型        | `local`              | `local`, `redis`     | 单机用 local，分布式用 redis |
| `clazs.ratelimiter.cache-expire-after-access-minutes` | 缓存过期时间（分钟）  | `1440`               | -                    | 根据业务调整               |
| `clazs.ratelimiter.cache-maximum-size`                | 最大缓存数量      | `10000`              | -                    | 防止内存溢出               |

#### Redis 存储配置示例

```yaml
clazs:
  ratelimiter:
    storage: redis  # 使用 Redis 存储

spring:
  redis:
    host: localhost
    port: 6379
    database: 0
```

### 注解配置（@DoRateLimit）

| 参数         | 类型             | 是否必填 | 说明                  | 示例                      |
|------------|----------------|------|---------------------|-------------------------|
| `key`      | String         | 是    | 限流 Key（支持 SpEL 表达式） | `#userId`, `'constant'` |
| `freq`     | int            | 否    | 时间窗口内最大请求次数         | `100`（默认使用全局配置）         |
| `interval` | long           | 否    | 时间窗口长度（毫秒）          | `60000`（默认使用全局配置）       |
| `scope`    | RateLimitScope | 否    | 限流隔离级别              | `METHOD`（默认）/ `GLOBAL`  |

### SpEL 表达式支持

```java
// 方法参数
@DoRateLimit(key = "#userId")

// 对象属性
@DoRateLimit(key = "#request.userId")

// 常量字符串
@DoRateLimit(key = "'global_api'")

// 复杂表达式
@DoRateLimit(key = "#user.id + ':' + #apiType")
```

---

## 使用示例

### 示例 1：基础用户限流

```java
@DoRateLimit(key = "#userId")
public UserInfo getUserInfo(Long userId) {
    // 每个 userId 每分钟最多访问 100 次（全局配置）
}
```

### 示例 2：高频接口限流

```java
@DoRateLimit(key = "#userId", freq = 10, interval = 1000)
public String createOrder(Long userId) {
    // 每个 userId 每秒最多访问 10 次
}
```

### 示例 3：全局限流

```java
@DoRateLimit(key = "'SEND_SMS_API'", freq = 1000, interval = 3600000)
public void sendSms(String phone) {
    // 系统全局每小时最多发送 1000 条短信
}
```

### 示例 4：多维度限流

```java
@DoRateLimit(key = "#userId + ':' + #apiType", freq = 50, interval = 60000)
public void apiCall(Long userId, String apiType) {
    // 每个 userId + apiType 组合每分钟最多 50 次
}
```

### 示例 5：方法级隔离 vs 全局隔离

```java
// 方法级隔离（默认）：不同接口独立计数
@DoRateLimit(key = "#userId", freq = 10, interval = 60000, scope = METHOD)
public void methodA(Long userId) {}

@DoRateLimit(key = "#userId", freq = 10, interval = 60000, scope = METHOD)
public void methodB(Long userId) {}
// 同一个用户调用 methodA 和 methodB，分别计数

// 全局隔离：不同接口共享计数
@DoRateLimit(key = "#userId", freq = 10, interval = 60000, scope = GLOBAL)
public void methodC(Long userId) {}

@DoRateLimit(key = "#userId", freq = 10, interval = 60000, scope = GLOBAL)
public void methodD(Long userId) {}
// 同一个用户调用 methodC 和 methodD，共享计数
```

---

## 核心原理

### 滑动窗口日志算法

QRateLimiter 默认采用**滑动窗口日志算法**，算法维度支持拓展（developing...），支持两种存储实现：

#### 1. 本地存储实现（环形缓冲区 + 二分查找）

通过**环形缓冲区**记录请求时间戳，使用**二分查找**统计窗口内的请求数。

##### 算法流程

```
1. 记录当前请求时间戳到环形数组
2. 二分查找窗口起始位置（当前时间 - interval）
3. 统计窗口内的请求数量
4. 判断是否超过阈值 freq
```

##### 时间复杂度

| 操作   | 时间复杂度    | 说明                     |
|------|----------|------------------------|
| 限流判断 | O(log n) | 二分查找                   |
| 内存占用 | O(freq)  | 默认仅需存储 freq * 1.5 个时间戳 |

##### 核心代码伪代码

```java
// 1. 记录当前请求
timestamps[index %capacity]=System.currentTimeMillis();
index++;

// 2. 二分查找窗口边界
int left = binarySearch(timestamps, 0, size - 1, currentTimeMillis - interval);

// 3. 统计窗口内请求数
int count = size - left;

// 4. 判断是否超限
if (count > freq) {
    throw new RateLimitException(key);
}
```

#### 2. Redis 存储实现（ZSet + Lua 脚本）

使用 **Redis ZSet** 存储请求时间戳，通过 **Lua 脚本**保证原子性。

##### 数据结构

```
Key: qratelimiter:sliding_window_log:{key}
Score: 时间戳（毫秒）
Member: 时间戳:唯一ID（解决并发唯一性问题）
```

##### 优势

- **原子性**：Lua 脚本保证 Redis 操作原子性
- **分布式**：支持多实例共享限流状态
- **并发安全**：时间戳:唯一ID 解决同一毫秒并发问题

---

## 性能测试

### 测试环境

- **CPU**: Intel Core i7-13620H
- **内存**: 32GB DDR5
- **JDK**: 1.8.0_461

### 内存占用

| 限流器数量   | 平均内存占用  | 说明                        |
|---------|---------|---------------------------|
| 1,000   | ~3 MB   | 每个限流器约 3KB                |
| 10,000  | ~30 MB  | 可通过 cache-maximum-size 控制 |
| 100,000 | ~300 MB | 建议增加缓存过期时间                |

> 💡 **本地模式**：QRateLimiter 在精度和性能之间取得了较好平衡；核心亮点依然是“低占用、轻量级”与“高性能”以及“开箱即用”！

---

## 架构设计

### 设计理念：桥接模式 + 多维度解耦

**扩展性**：通过桥接模式将"限流算法"与"存储介质"彻底解耦，支持灵活组合扩展。

#### 设计模式应用

| 设计模式       | 应用场景    | 实现类                               |
|------------|---------|-----------------------------------|
| **桥接模式**   | 算法与存储解耦 | `RateLimiter` ↔ `LimiterExecutor` |
| **工厂模式**   | 动态创建执行器 | `LimiterExecutorFactory`          |
| **策略模式**   | 限流隔离策略  | `METHOD` / `GLOBAL`               |
| **单例模式**   | 注册中心管理  | `RateLimitRegistry`               |
| **AOP 切面** | 无侵入式拦截  | `RateLimitAspect`                 |

#### 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        应用层 (Controller)                       │
└────────────────────────────────┬────────────────────────────────┘
                                 │ @DoRateLimit 注解（可自定义参数）
                                 ↓
┌─────────────────────────────────────────────────────────────────┐
│        AOP 拦截层 (RateLimitAspect)                              │
│  • SpEL 解析  • Key 生成  • 调用限流器                            │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ↓
┌─────────────────────────────────────────────────────────────────┐
│         限流器注册中心 (RateLimitRegistry)                       │
│  • Caffeine Cache  • 自动创建/回收  • 线程安全                    │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ↓
┌─────────────────────────────────────────────────────────────────┐
│          门面层 (RateLimiter 接口)                               │
│  • 统一对外接口  • 桥接算法与存储  • 配置管理                      │
└─────────┬───────────────────────────────────────────────────────┘
          │
          ↓ 持有
┌─────────────────────────────────────────────────────────────────┐
│       执行器层 (LimiterExecutor 接口)                            │
│  • 算法与存储的桥接  • tryAcquire 核心方法                        │
└───┬─────────────────────────────────────────────────────────┬───┘
    │                                                         │
    ↓ 算法维度                                                 ↓ 存储维度
┌──────────────────┐                                  ┌──────────────┐
│ • 滑动窗口日志    │                                  │ • Local      │
│ • 滑动窗口计数器  │   组合出 4×2 = 8 种实现           │ • Redis      │
│ • 令牌桶         │                                  └──────────────┘
│ • 漏桶           │
└──────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│              工厂层 (LimiterExecutorFactory)                     │
│  • 根据算法+存储类型动态创建执行器  • 缓存执行器实例                │
└─────────────────────────────────────────────────────────────────┘
```

#### 核心接口说明

**LimiterExecutor（执行器接口）**

```java
public interface LimiterExecutor {
    boolean tryAcquire(String key, int freq, long interval, int capacity);
}
```

- 每个实现类 = 一种"算法+存储"组合
- 例如：`LocalSlidingWindowLogExecutor` = 本地存储 + 滑动窗口日志

**RateLimiter（限流器接口）**

```java
public interface RateLimiter {
    boolean allowRequest(String key, int freq, long interval, int capacity);

    RateLimitAlgorithm getAlgorithm();

    RateLimitStorage getStorage();
}
```

- 对外统一门面，内部委托给 `LimiterExecutor`

**LimiterExecutorFactory（工厂）**

- 根据 `RateLimitAlgorithm` × `RateLimitStorage` 创建对应执行器
- 缓存已创建的执行器实例

#### 四层架构

```
Level 1: 配置层
  └─ RateLimiterProperties
     • 读取配置
     • 参数校验

Level 2: 管理层
  └─ RateLimitRegistry
     • Caffeine Cache 管理
     • 自动创建/回收限流器实例
     • 线程安全保障

Level 3: 交互层
  ├─ @DoRateLimit 注解
  └─ RateLimitAspect AOP 切面
     • SpEL 表达式解析
     • 限流 Key 生成
     • 异常处理

Level 4: 自动配置层
  └─ RateLimiterAutoConfiguration
     • Spring Boot 自动装配
     • 条件注解支持
     • 默认异常处理器注册
```

---

## 常见问题

### 1. 如何选择限流参数？

**freq 和 interval 的选择原则：**

```yaml
# 高频 API（如：下单、支付）
freq: 10
interval: 1000  # 每秒 10 次

# 普通 API（如：查询信息）
freq: 100
interval: 60000  # 每分钟 100 次

# 低频 API（如：导出数据）
freq: 10
interval: 3600000  # 每小时 10 次
```

**capacity 的默认计算：**

```
capacity = freq * 1.5

# 例如：
freq = 100
capacity = 100 * 1.5 = 150
```

### 2. 时钟回拨会怎样？

QRateLimiter 内置**时钟回拨检测**，当检测到时钟回拨时会：

1. 记录警告日志
2. 使用旧的时间戳继续限流
3. 防止限流失效

```java
if (currentTimestamp<lastTimestamp) {
    log.warn("检测到时钟回拨：旧={}，新={}",lastTimestamp, currentTimestamp);
    currentTimestamp =lastTimestamp;  // 使用旧时间戳，流量高并发下时间依旧安全
}
```

### 3. 限流器什么时候会被清理？

基于 Caffeine Cache 的 **LRU 淘汰策略**：

- **访问过期**：`cache-expire-after-access-minutes` 分钟未访问
- **数量超标**：超过 `cache-maximum-size` 时淘汰最久未使用的

### 4. 如何实现分布式限流？

QRateLimiter **已支持分布式限流**，只需配置使用 Redis 存储介质即可：

```yaml
clazs:
  ratelimiter:
    storage: redis  # 切换到 Redis 存储

spring:
  redis:
    host: localhost
    port: 6379
```

**实现原理**：

> 以默认滑动窗口日志算法为例

- 使用 Redis ZSet 存储请求时间戳
- Lua 脚本保证操作的原子性
- 多个应用实例共享同一个限流状态

---

## TODOs

### v1.1.0 规划 - 完善剩余算法实现

#### 已实现

| 算法类型                        | Local 存储 | Redis 存储 |
|-----------------------------|----------|----------|
| 滑动窗口日志 (Sliding Window Log) | 完整实现     | 完整实现     |

#### 待实现

| 算法类型                             | Local 存储 | Redis 存储 | 优先级 |
|----------------------------------|----------|----------|-----|
| 滑动窗口计数器 (Sliding Window Counter) | TODO     | TODO     | 1   |
| 令牌桶 (Token Bucket)               | TODO     | TODO     | 2   |
| 漏桶 (Leaky Bucket)                | TODO     | TODO     | 3   |

**说明**：框架已定义好接口 `LimiterExecutor` 和工厂 `LimiterExecutorFactory`，只需实现对应执行器类即可。

---

### 未来计划

- [ ] **Spring Boot 3.x 支持**
    - 支持 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 配置方式
    - 兼容 Spring Boot 2.7.18+ / 3.x 版本
- [ ] **动态配置刷新**
    - 支持运行时修改限流参数
    - 集成 Spring Cloud Config / Nacos
    - 提供管理接口查看限流统计
- [ ] **监控与指标**
    - 限流效果可视化面板

---

## 许可证

本项目采用 **MIT License** 开源协议

---

**项目灵感来源：力扣“多段有序区间内的二分查找算法（或红蓝染色法）”，持续打磨扩展中...🤗😸**

~~更多的地方可能是为了学习的验证以及一个简易的限流器开发的兴趣、封装为starter的真实流程等等……~~

**若对您的学习、开发有帮助，请给个 ⭐️ Star 支持一下！**

Made with ❤️ by [clazs](https://github.com/Nahiyi)

