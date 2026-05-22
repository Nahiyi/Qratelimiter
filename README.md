# Q-RateLimiter

![QRateLimiter Logo](https://img.shields.io/badge/QRateLimiter-Rate%20Limiter-brightgreen)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18%20%7C%203.2.x-brightgreen)
![Java](https://img.shields.io/badge/Java-8%20%7C%2017-orange)
![License](https://img.shields.io/badge/License-MIT-blue)
![Tests](https://img.shields.io/badge/Tests-100%25%20Passing-success)

**一款轻量级、高性能、可拓展的限流器**。既支持普通 Java / Maven 项目通过 core API 编程式使用，也支持 Spring Boot 2 / 3 通过 starter 注解式开箱即用。

[特性](#特性) • [快速开始](#快速开始) • [配置说明](#配置说明) • [示例模块](#示例模块) • [测试模块](#测试模块) • [核心原理](#核心原理) • [FAQ](#)

---

## 目录

- [简介](#简介)
- [特性](#特性)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [运行时管理](#运行时管理)
- [使用示例](#使用示例)
- [核心原理](#核心原理)
- [性能测试](#性能测试)
- [示例模块](#示例模块)
- [测试模块](#测试模块)
- [架构设计](#架构设计)
- [常见问题](#常见问题)
- [TODOs](#todos)
- [许可证](#许可证)

---

## 简介

**QRateLimiter** 是一款轻量级限流器，核心能力已经拆分为不依赖 Spring Boot 的 `qratelimiter-core`，Spring Boot 用户仍然可以通过 starter 获得注解式开箱即用体验。

当前 release 版本：`1.6.0`

当前版本已验证：

- Spring Boot `2.7.18` + JDK 8
- Spring Boot `3.2.x` + JDK 17
- 普通 Java / Maven 项目的 core API 编程式使用
- Local / Redis 两种存储
- 4 种算法 × 2 种存储的完整组合

当前版本已经支持以下算法组合：

- **滑动窗口日志（Sliding Window Log）**
- **滑动窗口计数器（Sliding Window Counter）**
- **令牌桶（Token Bucket）**
- **漏桶（Leaky Bucket）**

默认算法仍为**滑动窗口日志算法**，其本地实现基于**环形缓冲区 + 二分查找**。

### 核心特性

- **桥接模式架构**：算法与存储解耦，支持灵活组合扩展
- **独立 core 模块**：核心限流 API 与本地算法不依赖 Spring Boot
- **编程式 Template API**：通过 `RateLimiterTemplate` 显式执行限流判断
- **多算法支持**：滑动窗口日志 / 滑动窗口计数器 / 令牌桶 / 漏桶
- **多存储支持**：本地内存 / Redis 分布式存储
- **Spring Boot 2 / 3 兼容**：同时支持 `spring.factories` 与 `AutoConfiguration.imports`
- **高性能默认实现**：默认滑动窗口日志基于环形缓冲区 + 二分查找，时间复杂度 O(log n)
- **多模型限流**：同时支持精确滑动窗口、近似滑动窗口、令牌桶与漏桶语义
- **开箱即用**：引入 starter 依赖即可使用，内置全局异常处理器
- **高度可靠**：线程安全，支持时钟回拨检测
- **灵活配置**：支持全局配置 + 方法级自定义配置
- **运行时管理基础**：可选启用统计查询、配置刷新和缓存清理接口
- **Micrometer 指标**：可选暴露低基数运行指标，便于接入现有监控体系
- **SpEL 字符串字面量**：支持 `@DoRateLimit(key = "'constant_key'")`
- **零侵入**：基于注解和 AOP，对业务代码零侵入

---

## 特性

### 核心特性

| 特性       | 说明                               |
|----------|----------------------------------|
| **高性能**  | 默认滑动窗口日志使用环形缓冲区 + 二分查找，时间复杂度 O(log n) |
| **低内存**  | 本地状态按 key 缓存，滑动窗口日志仅保存固定容量时间戳数组 |
| **多模型限流** | 支持精确滑动窗口、近似滑动窗口、令牌桶与漏桶             |
| **自动管理** | 基于 Caffeine Cache，自动清理过期限流器实例    |
| **灵活隔离** | 支持方法级隔离（默认）和全局限流两种模式             |
| **配置覆盖** | 支持全局配置和方法级自定义配置覆盖                |

### 技术亮点

- **桥接模式架构**：算法与存储彻底解耦，支持灵活组合
- **多算法支持**：4 类限流算法统一接入，切换算法无需改注解结构
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

### 当前推荐使用方式

由于项目**暂时还没有发布到 Maven 中央仓库**，目前推荐先拉取源码并安装到本地 Maven 仓库：

```bash
git clone https://github.com/Nahiyi/Qratelimiter.git
cd Qratelimiter
mvn install
```

安装完成后，再在你的业务项目中引入：

```xml
<dependency>
    <groupId>cn.clazs</groupId>
    <artifactId>qratelimiter-spring-boot-starter</artifactId>
    <version>1.6.0</version>
</dependency>
```

用户侧仍然只需要引入 `qratelimiter-spring-boot-starter`，无需直接依赖 `autoconfigure` 模块。

如果不是 Spring Boot 项目，也可以只引入 core 模块并使用编程式 API：

```xml
<dependency>
    <groupId>cn.clazs</groupId>
    <artifactId>qratelimiter-core</artifactId>
    <version>1.6.0</version>
</dependency>
```

### 项目模块结构

当前仓库内部已拆分为标准多模块结构：

- `qratelimiter-spring-boot-autoconfigure`
- `qratelimiter-spring-boot-starter`
- `qratelimiter-spring-boot-example`
- `qratelimiter-core`
- `qratelimiter-test`

其中：

- `starter` 作为对外推荐引入的入口依赖
- `core` 承载不依赖 Spring Boot 的核心 API、注册中心和本地算法实现
- `autoconfigure` 承载自动装配、注解 AOP、异常处理、Redis 执行器和 Lua 脚本
- `example` 提供可运行的 Spring Boot 示例应用，用于演示配置、注解、算法与存储组合
- `test` 承担独立兼容性验证，覆盖 core-only、Spring Boot 2 / 3、Local / Redis 与可选 stress profile

### 1. 添加配置

在 `application.yml` 中添加配置：

```yaml
clazs:
  ratelimiter:
    enabled: true                    # 是否启用限流器
    freq: 100                        # 主限流额度参数
    interval: 60000                  # 时间基准（毫秒）= 1分钟
    capacity: 150                    # 容量/精度参数
    algorithm: sliding-window-log    # 限流算法：sliding-window-log / sliding-window-counter / token-bucket / leaky-bucket
    storage: local                   # 存储类型：local=本地内存，redis=分布式
    cache-expire-after-access-minutes: 1440  # 缓存过期时间
    cache-maximum-size: 1000                 # 最大缓存数量
    management:
      enabled: false                 # 是否启用运行时管理接口
      base-path: /qratelimiter       # 管理接口基础路径
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

### 2.1 使用 Template API

普通 Java / Maven 项目可以直接使用 `RateLimiterTemplate`：

```java
RateLimiterTemplate template = RateLimiterTemplate.localDefaults();

if (!template.tryAcquire("user:" + userId, 10, 60000L, 15)) {
    throw new IllegalStateException("too many requests");
}
```

Spring Boot 项目中也会自动注册 `RateLimiterTemplate` Bean，复杂场景可以注入后显式调用：

```java
@RestController
public class UserController {

    private final RateLimiterTemplate rateLimiterTemplate;

    public UserController(RateLimiterTemplate rateLimiterTemplate) {
        this.rateLimiterTemplate = rateLimiterTemplate;
    }

    @GetMapping("/manual")
    public String manualLimit(String userId) {
        if (!rateLimiterTemplate.tryAcquire("manual:" + userId, 10, 60000L, 15)) {
            throw new RateLimitException("manual:" + userId);
        }
        return "ok";
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

默认异常响应会返回 HTTP 429，并包含触发限流的完整 key，便于排查具体是哪一个业务维度被限流：

```json
{
  "status": 429,
  "error": "TOO_MANY_REQUESTS",
  "message": "请求过于频繁，请稍后再试",
  "limitKey": "cn.example.UserController.getUserInfo:1001"
}
```

---

## 配置说明

### 全局配置（application.yml）

| 配置项                                                   | 说明          | 默认值                  | 可选值                  | 建议                   |
|-------------------------------------------------------|-------------|----------------------|----------------------|----------------------|
| `clazs.ratelimiter.enabled`                           | 是否启用限流器     | `true`               | -                    | 生产环境开启               |
| `clazs.ratelimiter.freq`                              | 主限流额度参数    | `100`                | -                    | 根据算法和业务调整            |
| `clazs.ratelimiter.interval`                          | 时间基准（毫秒）   | `60000`              | -                    | 根据算法和业务调整            |
| `clazs.ratelimiter.capacity`                          | 容量/精度参数     | `150`                | -                    | 根据算法和业务调整            |
| `clazs.ratelimiter.algorithm`                         | 限流算法        | `sliding-window-log` | `sliding-window-log`, `sliding-window-counter`, `token-bucket`, `leaky-bucket` | 默认推荐 `sliding-window-log` |
| `clazs.ratelimiter.storage`                           | 存储类型        | `local`              | `local`, `redis`     | 单机用 local，分布式用 redis |
| `clazs.ratelimiter.cache-expire-after-access-minutes` | 缓存过期时间（分钟）  | `1440`               | -                    | 根据业务调整               |
| `clazs.ratelimiter.cache-maximum-size`                | 最大缓存数量      | `10000`              | -                    | 防止内存溢出               |
| `clazs.ratelimiter.management.enabled`                | 是否启用运行时管理接口 | `false`              | `true`, `false`      | 生产环境建议配合鉴权或内网暴露 |
| `clazs.ratelimiter.management.base-path`              | 管理接口基础路径   | `/qratelimiter`      | -                    | 可按应用管理路径规范调整       |

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

### 运行时管理

从 `1.6.0` 开始，QRateLimiter 提供默认关闭的运行时管理基础能力。它用于本地排查、运维接入和后续配置中心集成前的基础能力沉淀。

启用方式：

```yaml
clazs:
  ratelimiter:
    management:
      enabled: true
      base-path: /qratelimiter
```

启用后会注册以下端点：

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/qratelimiter/stats` | 查看注册中心、缓存和请求统计快照 |
| `GET` | `/qratelimiter/config` | 查看当前全局默认限流配置 |
| `POST` | `/qratelimiter/config` | 刷新全局默认限流配置 |
| `DELETE` | `/qratelimiter/cache` | 清空全部限流器缓存和状态 |
| `DELETE` | `/qratelimiter/cache/{key}` | 清理指定业务 key 的限流器缓存和状态 |

`POST /qratelimiter/config` 支持两种刷新策略：

| 策略 | 说明 |
|------|------|
| `APPLY_TO_NEW_LIMITERS_ONLY` | 只影响之后新创建的限流器，已有限流器继续使用创建时的执行器和状态 |
| `CLEAR_CACHE_AND_APPLY` | 刷新默认配置并清空当前缓存，让后续请求按新配置重新创建限流器 |

示例请求：

```json
{
  "freq": 200,
  "interval": 60000,
  "capacity": 300,
  "algorithm": "sliding-window-log",
  "storage": "local",
  "strategy": "CLEAR_CACHE_AND_APPLY"
}
```

> 管理端点默认关闭。生产环境启用时，建议通过网关、内网、Spring Security 或其他统一鉴权方式限制访问范围。

### Micrometer 指标

当应用引入 Micrometer 时，`1.6.0` 会自动注册低基数指标：

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `qratelimiter.limiters.cache.size` | Gauge | 当前缓存中的限流器数量 |
| `qratelimiter.limiters.created.total` | Gauge | 注册中心累计创建的限流器数量 |
| `qratelimiter.requests.allowed` | FunctionCounter | 累计放行请求数 |
| `qratelimiter.requests.rejected` | FunctionCounter | 累计拒绝请求数 |

默认不会按业务 key 添加 tag，避免高基数指标拖垮监控系统。可视化面板、Nacos / Spring Cloud Config 动态接入仍属于后续版本范围。

### 注解配置（@DoRateLimit）

| 参数         | 类型             | 是否必填 | 说明                  | 示例                      |
|------------|----------------|------|---------------------|-------------------------|
| `key`      | String         | 是    | 限流 Key（支持 SpEL 表达式） | `#userId`, `'constant'` |
| `freq`     | int            | 否    | 主限流额度参数             | `100`（默认使用全局配置）         |
| `interval` | long           | 否    | 时间基准（毫秒）            | `60000`（默认使用全局配置）       |
| `capacity` | int            | 否    | 容量/精度参数             | `150`（默认使用全局配置或自动计算）    |
| `scope`    | RateLimitScope | 否    | 限流隔离级别              | `METHOD`（默认）/ `GLOBAL`  |

### 统一参数语义

为了让使用者只理解一套注解参数，QRateLimiter 在不同算法下统一沿用 `freq / interval / capacity`：

| 算法 | `freq` | `interval` | `capacity` |
|------|--------|------------|------------|
| 滑动窗口日志 | 窗口内最大请求数 | 滑动窗口长度 | 环形缓冲区容量 |
| 滑动窗口计数器 | 窗口内最大请求数 | 完整统计窗口长度 | 时间分片数量 |
| 令牌桶 | 每个周期补充的令牌数 | 补充周期 | 桶容量 |
| 漏桶 | 每个周期泄放的请求数 | 泄放周期 | 最大积压容量 |

### SpEL 表达式支持

```java
// 方法参数
@DoRateLimit(key = "#userId")

// 对象属性
@DoRateLimit(key = "#request.userId")

// 常量字符串
@DoRateLimit(key = "'global_api'")

// 普通常量字符串（不走 SpEL）
@DoRateLimit(key = "global_api")

// 复杂表达式
@DoRateLimit(key = "#user.id + ':' + #apiType")
```

> 从 `1.3.0` 开始，SpEL 字符串字面量会被正确解析为去掉外层引号后的业务 key，例如 `"'global_api'"` 会解析为 `global_api`，不会再把单引号带入最终限流 key。

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

### 限流算法体系

QRateLimiter 当前已完整支持以下 4 类算法，且每类算法都提供 `Local` 与 `Redis` 两种存储实现：

| 算法 | Local | Redis | 特点 |
|------|-------|-------|------|
| 滑动窗口日志 | 完整实现 | 完整实现 | 精准、直观、默认推荐 |
| 滑动窗口计数器 | 完整实现 | 完整实现 | 内存更轻，采用近似估算 |
| 令牌桶 | 完整实现 | 完整实现 | 适合允许突发流量的场景 |
| 漏桶 | 完整实现 | 完整实现 | 适合平滑输出和削峰 |

#### 1. 滑动窗口日志算法

默认算法仍为**滑动窗口日志算法**，支持本地与 Redis 两种实现：

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

#### 3. 滑动窗口计数器算法

滑动窗口计数器通过“当前窗口 + 上一窗口加权”的方式近似计算滑动窗口内的请求量。

- **优点**：状态更轻、实现简单、适合高频计数场景
- **代价**：相较滑动窗口日志属于近似算法，不是逐请求精确记录
- **Local**：基于时间分片计数
- **Redis**：基于双窗口计数 + Lua 脚本 + Redis 服务器时间

#### 4. 令牌桶算法

令牌桶以固定速率补充令牌，请求到来时消耗令牌。

- **优点**：允许一定程度的突发流量
- **适合**：网关入口、接口峰值削峰但允许短时 burst 的场景
- **Local / Redis**：都基于“补充令牌 -> 尝试消费”的流程

#### 5. 漏桶算法

漏桶以固定速率泄放桶内积压的请求，请求到来时先尝试入桶。

- **优点**：输出速率更稳定，天然适合平滑下游压力
- **适合**：对下游系统敏感、需要严格平滑吞吐的场景
- **Local / Redis**：都基于“先泄放 -> 再尝试入桶”的流程

---

## 性能测试

以下数据是本地模式的参考结果，用于说明默认滑动窗口日志实现的内存量级；不同 JDK、机器、算法、缓存配置和 key 分布下会有差异。

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

## 示例模块

`qratelimiter-spring-boot-example` 是一个可运行的 Spring Boot Web 示例模块，用来演示 starter 在真实应用中的使用方式。

它覆盖：

- 基础限流
- SpEL 参数 key、对象字段 key、字符串字面量 key
- `METHOD` 与 `GLOBAL` 两种作用域
- Local 与 Redis 两种存储
- 滑动窗口日志、滑动窗口计数器、令牌桶、漏桶四种算法
- Spring Boot 2 + JDK 8 与 Spring Boot 3 + JDK 17 两种运行方式

示例文档：

- [`qratelimiter-spring-boot-example/README.md`](qratelimiter-spring-boot-example/README.md)
- [`qratelimiter-spring-boot-example/README_zh.md`](qratelimiter-spring-boot-example/README_zh.md)
- [`qratelimiter-spring-boot-example/README_en.md`](qratelimiter-spring-boot-example/README_en.md)

需要说明的是，example 模块的定位是“演示 starter 如何被真实项目依赖和使用”。它可以辅助本地手工验证，但不替代 `qratelimiter-test` 独立测试模块承担的兼容性与压力验证职责。

---

## 测试模块

`qratelimiter-test` 是从 `1.5.0` 开始新增的独立验证模块。它不是对外运行时依赖，也不是演示应用，而是专门用于验证项目真实使用路径和组合矩阵。

它覆盖：

- 普通 Java / Maven 项目仅引入 `qratelimiter-core` 的编程式用法
- Spring Boot starter 的注解式用法、SpEL key、作用域、默认异常响应和 `RateLimiterTemplate` Bean
- Spring Boot 2 默认构建与 Spring Boot 3 profile 构建
- 4 种算法 × Local / Redis 两种存储的 starter 矩阵
- 可选并发 stress profile，覆盖 core-only 4 算法与 starter 4 算法 × Local / Redis

默认测试：

```bash
mvn -B -pl qratelimiter-test -am test --file pom.xml
```

Spring Boot 3 兼容性测试：

```bash
mvn -B -Pspring-boot-3 -pl qratelimiter-test -am test --file pom.xml
```

Redis 真实路径验证：

```bash
QRL_REDIS_HOST=localhost QRL_REDIS_PORT=6379 \
mvn -B -pl qratelimiter-test -am test --file pom.xml
```

Windows PowerShell 示例：

```powershell
$env:QRL_REDIS_HOST='localhost'
$env:QRL_REDIS_PORT='6379'
mvn -B -pl qratelimiter-test -am test --file pom.xml
```

可选 stress profile：

```bash
mvn -B -Pstress -pl qratelimiter-test -am test --file pom.xml
```

Spring Boot 3 stress profile：

```bash
mvn -B -Pstress,spring-boot-3 -pl qratelimiter-test -am test --file pom.xml
```

Windows PowerShell 下需要给 profile 参数加引号：

```powershell
mvn -B '-Pstress,spring-boot-3' -pl qratelimiter-test -am test --file pom.xml
```

默认构建不会运行 stress 测试；CI 会在 Boot 2 / JDK 8 与 Boot 3 / JDK 17 两条路径上单独验证该 profile，并启动 Redis 覆盖真实 Redis 存储组合。

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

**capacity 的选择原则：**

```
滑动窗口日志：建议 capacity = freq * 1.5
滑动窗口计数器：capacity = 时间分片数，常见可取 10 / 20 / 60
令牌桶：capacity = 允许的最大突发量
漏桶：capacity = 允许的最大积压量
```

### 2. 时钟回拨会怎样？

QRateLimiter 内置**时钟回拨检测**，当检测到时钟回拨时会：

1. 使用旧的时间戳或服务端时间继续限流
2. 防止本地时钟回拨导致窗口或速率计算异常
3. Redis 实现统一使用 Redis 服务器时间，避免多实例本地时钟不一致

```java
if (currentTimestamp < lastTimestamp) {
    currentTimestamp = lastTimestamp;  // 使用安全时间戳，避免回拨导致限流失效
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
  data:
    redis:
      host: localhost
      port: 6379
```

> Spring Boot 2 使用 `spring.redis.*`，Spring Boot 3 推荐使用 `spring.data.redis.*`。为了兼容两代版本，可以同时写入两组配置。

**实现原理**：

- 滑动窗口日志：Redis ZSet + Lua 脚本
- 滑动窗口计数器：双窗口计数 + Lua 脚本
- 令牌桶：Hash 状态 + Lua 脚本
- 漏桶：Hash 状态 + Lua 脚本

所有 Redis 算法实现都以 **Lua 脚本 + Redis 服务器时间** 为核心，保证：

- 状态更新原子化
- 多实例共享限流状态
- 避免不同应用节点时钟不一致带来的窗口或速率偏差

---

## TODOs

### 当前实现状态

| 算法类型 | Local 存储 | Redis 存储 |
|----------|----------|----------|
| 滑动窗口日志 (Sliding Window Log) | 完整实现 | 完整实现 |
| 滑动窗口计数器 (Sliding Window Counter) | 完整实现 | 完整实现 |
| 令牌桶 (Token Bucket) | 完整实现 | 完整实现 |
| 漏桶 (Leaky Bucket) | 完整实现 | 完整实现 |

---

### 未来计划

- [x] **Spring Boot 3.x 支持**
    - 已支持 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 配置方式
    - 已验证 Spring Boot 2.7.18 + JDK 8 与 Spring Boot 3.2.x + JDK 17
- [x] **Example 示例模块**
    - 已提供 `qratelimiter-spring-boot-example`
    - 可演示 4 种算法、2 种存储、SpEL key、作用域与 Redis 配置
- [x] **模块边界进一步标准化**
    - 已拆分 `qratelimiter-core`，让核心限流能力脱离 Spring Boot 也可复用
    - `spring-boot-autoconfigure` 专注自动配置、AOP、Properties、Redis 集成与 Web 异常处理
    - `starter` 作为更薄的依赖聚合入口
- [x] **独立测试模块**
    - 已新增 `qratelimiter-test`，系统覆盖 core-only、Local、Redis、Boot2、Boot3 与组合矩阵
    - example 保持为演示模块，测试模块承担质量兜底职责
    - 已提供可选 `-Pstress` 并发压力验证入口，覆盖 core-only 与 starter 矩阵
- [x] **运行时管理基础**
    - 已支持运行时查看限流统计快照
    - 已支持刷新全局默认限流配置
    - 已支持按 key 或全量清理限流器缓存与状态
- [x] **监控与指标基础**
    - 已支持 Micrometer 低基数指标
    - 默认不按业务 key 添加 tag，避免高基数风险
- [ ] **配置中心集成**
    - 集成 Spring Cloud Config / Nacos
    - 在配置变更事件中复用现有刷新策略
- [ ] **可视化面板**
    - 限流效果可视化面板

---

## 许可证

本项目采用 **MIT License** 开源协议

---

**项目灵感来源：力扣“多段有序区间内的二分查找算法（或红蓝染色法）”，持续打磨扩展中...🤗😸**

**若对您的学习、开发有帮助，请给个 ⭐️ Star 支持一下！**

Made with ❤️ by [clazs](https://github.com/Nahiyi)

