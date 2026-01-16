# Q-RateLimiter

![QRateLimiter Logo](https://img.shields.io/badge/QRateLimiter-Rate%20Limiter-brightgreen)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18%2B-brightgreen)
![Java](https://img.shields.io/badge/Java-1.8%2B-orange)
![License](https://img.shields.io/badge/License-MIT-blue)
![Tests](https://img.shields.io/badge/Tests-100%25%20Passing-success)

**一款轻量级、高性能、开箱即用的 Spring Boot 限流器 Starter**（基于环形缓冲区 + 二分查找算法实现）

[特性](#特性) • [快速开始](#快速开始) • [配置说明](#配置说明) • [核心原理](#核心原理) • [性能测试](#性能测试) • [FAQ](#faq)

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
- [TODO](#todo)
- [许可证](#许可证)

---

## 简介

**QRateLimiter** 是一款专为 Spring Boot 应用设计的轻量级限流器，采用**滑动窗口日志算法**，基于**环形缓冲区 + 二分查找**实现，具有以下优势：

- **极致性能**：核心数据结构基于基本数据类型，极速响应及 GC 友好
- **精准限流**：基于滑动窗口，支持任意时间粒度(毫秒级别)
- **开箱即用**：引入 starter 依赖即可使用，无需复杂配置
- **高度可靠**：线程安全，支持时钟回拨检测
- **灵活配置**：支持全局配置 + 方法级自定义配置
- **零侵入**：基于注解和 AOP，对业务代码零侵入

---

## 特性

### 核心特性

| 特性 | 说明 |
|------|------|
| **高性能** | 环形缓冲区 + 二分查找，时间复杂度 O(log n) |
| **低内存** | 每个限流器仅需 `freq * 1.5` 个 long 数组元素 |
| **精准限流** | 滑动窗口算法，支持任意时间粒度（毫秒级） |
| **自动管理** | 基于 Caffeine Cache，自动清理过期限流器实例 |
| **灵活隔离** | 支持方法级隔离（默认）和全局限流两种模式 |
| **动态配置** | 支持全局配置和方法级自定义配置覆盖 |

### 技术亮点

- **线程安全**：完全并发安全的限流算法
- **轻量级占用**：基于 long 型数组作为核心数据结构
- **时钟回拨检测**：防止时钟回拨导致限流失效
- **参数校验**：启动时校验配置参数合法性
- **异常友好**：限流触发时抛出明确的异常信息
- **缓存优化**：Caffeine 高性能缓存，支持 LRU 淘汰
- **开箱即用**：基于 AOP 以及定义 starter 带来开箱即用的体验

---

## 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>cn.clazs</groupId>
    <artifactId>qratelimiter-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
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

| 配置项 | 说明 | 默认值 | 建议 |
|--------|------|--------|------|
| `clazs.ratelimiter.enabled` | 是否启用限流器 | `true` | 生产环境开启 |
| `clazs.ratelimiter.freq` | 时间窗口内最大请求次数 | `100` | 根据业务调整 |
| `clazs.ratelimiter.interval` | 时间窗口长度（毫秒） | `60000` | 根据业务调整 |
| `clazs.ratelimiter.capacity` | 队列容量 | `150` | 建议为 freq 的 1.5 倍 |
| `clazs.ratelimiter.cache-expire-after-access-minutes` | 缓存过期时间（分钟） | `1440` | 根据业务调整 |
| `clazs.ratelimiter.cache-maximum-size` | 最大缓存数量 | `10000` | 防止内存溢出 |

### 注解配置（@DoRateLimit）

| 参数 | 类型 | 是否必填 | 说明 | 示例 |
|------|------|------|------|------|
| `key` | String | 是 | 限流 Key（支持 SpEL 表达式） | `#userId`, `'constant'` |
| `freq` | int | 否 | 时间窗口内最大请求次数 | `100`（默认使用全局配置） |
| `interval` | long | 否 | 时间窗口长度（毫秒） | `60000`（默认使用全局配置） |
| `scope` | RateLimitScope | 否 | 限流隔离级别 | `METHOD`（默认）/ `GLOBAL` |

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
public void methodA(Long userId) { }

@DoRateLimit(key = "#userId", freq = 10, interval = 60000, scope = METHOD)
public void methodB(Long userId) { }
// 同一个用户调用 methodA 和 methodB，分别计数

// 全局隔离：不同接口共享计数
@DoRateLimit(key = "#userId", freq = 10, interval = 60000, scope = GLOBAL)
public void methodC(Long userId) { }

@DoRateLimit(key = "#userId", freq = 10, interval = 60000, scope = GLOBAL)
public void methodD(Long userId) { }
// 同一个用户调用 methodC 和 methodD，共享计数
```

---

## 核心原理

### 滑动窗口日志算法

QRateLimiter 采用**滑动窗口日志算法**，通过**环形缓冲区**记录请求时间戳，使用**二分查找**统计窗口内的请求数。

#### 算法流程

```
1. 记录当前请求时间戳到环形数组
2. 二分查找窗口起始位置（当前时间 - interval）
3. 统计窗口内的请求数量
4. 判断是否超过阈值 freq
```

#### 时间复杂度

| 操作 | 时间复杂度 | 说明 |
|------|------------|------|
| 限流判断 | O(log n) | 二分查找 |
| 内存占用 | O(freq) | 默认仅需存储 freq * 1.5 个时间戳 |

#### 核心代码伪代码

```java
// 1. 记录当前请求
timestamps[index % capacity] = System.currentTimeMillis();
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

### 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                   应用层 (Controller)                    │
└───────────────────────┬─────────────────────────────────┘
                        │ @DoRateLimit
                        ↓
┌─────────────────────────────────────────────────────────┐
│              AOP 拦截层 (RateLimitAspect)                │
│  • 解析注解配置  • 生成限流 Key  • 调用限流器              │
└───────────────────────┬─────────────────────────────────┘
                        │
                        ↓
┌─────────────────────────────────────────────────────────┐
│           限流器注册中心 (RateLimitRegistry)             │
│  • Caffeine Cache 管理  • 自动创建/回收限流器             │
└───────────────────────┬─────────────────────────────────┘
                        │
                        ↓
┌─────────────────────────────────────────────────────────┐
│              核心限流器 (QRatelimiter)                   │
│  • 滑动窗口算法  • 环形缓冲区  • 二分查找                  │
└─────────────────────────────────────────────────────────┘
```

---

## 性能测试

### 测试环境

- **CPU**: Intel Core i7-13620H
- **内存**: 32GB DDR5
- **JDK**: 1.8.0_461

### 内存占用

| 限流器数量 | 平均内存占用 | 说明 |
|-----------|-------------|------|
| 1,000 | ~3 MB | 每个限流器约 3KB |
| 10,000 | ~30 MB | 可通过 cache-maximum-size 控制 |
| 100,000 | ~300 MB | 建议增加缓存过期时间 |

> 💡 **结论**：QRateLimiter 在精度和性能之间取得了较好平衡；核心亮点依然是“低占用、轻量级”与“高性能”以及“开箱即用”！

---

## 架构设计

### 四层架构

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
```

### 设计模式

- **单例模式**：RateLimitRegistry 全局唯一
- **工厂模式**：自动创建不同配置的限流器
  - 未来进一步封装注册中心的实例化逻辑为工厂获取
- **策略模式**：METHOD / GLOBAL 限流隔离策略
- **AOP 切面**：无侵入式限流拦截
- **模板方法**：限流判断的固定流程
  - 未来拓展限流器接口，从而把固定逻辑写在父类，将具体实现延迟到子类

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
if (currentTimestamp < lastTimestamp) {
    log.warn("检测到时钟回拨：旧={}，新={}", lastTimestamp, currentTimestamp);
    currentTimestamp = lastTimestamp;  // 使用旧时间戳，流量高并发下时间依旧安全
}
```

### 3. 限流器什么时候会被清理？

基于 Caffeine Cache 的 **LRU 淘汰策略**：

- **访问过期**：`cache-expire-after-access-minutes` 分钟未访问
- **数量超标**：超过 `cache-maximum-size` 时淘汰最久未使用的

### 4. 如何实现分布式限流？

QRateLimiter 目前是**单机限流器**，如需分布式限流，可以：

1. **使用 Nginx/Iptables 在网关层限流**
2. **使用 Sentinel + Redis 实现分布式限流**
3. **在微服务网关（如 Spring Cloud Gateway）统一限流**

### 5. 限流失败后如何处理？

推荐方式：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<String> handleRateLimit(RateLimitException e) {
        // 方式1：返回 429 状态码
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body("访问过于频繁，请稍后再试");

        // 方式2：返回自定义 JSON
        // Map<String, Object> result = new HashMap<>();
        // result.put("code", 429);
        // result.put("message", "访问过于频繁");
        // return ResponseEntity.ok(result);
    }
}
```

---

## TODOs

### 未来计划

- [ ] **Spring Boot 3.x 支持**
  - 支持 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 配置方式
  - 兼容 Spring Boot 2.7.18+ / 3.x 版本
- [ ] **扩展限流器类型**
  - 基于 OOP 继承结构扩展限流器
  - 支持令牌桶（Token Bucket）算法
  - 支持漏桶（Leaky Bucket）算法
  - 支持滑动窗口计数（Sliding Window Counter）算法
- [ ] **分布式限流支持**
  - 集成 Redis 实现分布式限流
  - 支持 Redis Cluster 模式
  - 提供 Redis + 本地限流混合模式
- [ ] **动态配置刷新**
  - 支持运行时修改限流参数
  - 集成 Spring Cloud Config / Nacos
  - 提供管理接口查看限流统计

---

## 许可证

本项目采用 **MIT License** 开源协议

---

**项目可能不尽完善，甚至些许地方稍有粗糙，但主要是基于“多段有序区间内的二分查找算法（或红蓝染色法）”的灵感，快速上手实践得来；**

**更多的地方可能是为了学习的验证以及一个简易的限流器开发的兴趣、封装为starter的真实流程等等……**

**若对您的学习、开发有帮助，请给个 ⭐️ Star 支持一下！**


Made with ❤️ by [clazs](https://github.com/Nahiyi)

