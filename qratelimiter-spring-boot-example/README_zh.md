# QRateLimiter Spring Boot 示例模块

`qratelimiter-spring-boot-example` 是 QRateLimiter Spring Boot starter 的可运行演示模块。它展示了注解在真实 Web 应用中的行为，以及同一套代码如何在 Spring Boot 2 和 Spring Boot 3 上进行验证。

本模块适用于本地验证、功能演示，以及在正式发布 starter 之前的上手检查。

运行时管理接口默认关闭。如需本地验证 `1.6.0` 的管理能力，可以启用：

```yaml
clazs:
  ratelimiter:
    management:
      enabled: true
      base-path: /qratelimiter
```

启用后可访问 `GET /qratelimiter/stats`、`GET /qratelimiter/config`、
`POST /qratelimiter/config`、`DELETE /qratelimiter/cache` 和
`DELETE /qratelimiter/cache/{key}`。

## 功能覆盖

示例包含以下端点：

- 基于 key 的基础限流。
- 编程式 `RateLimiterTemplate` 使用方式。
- 通过路径变量、请求参数、请求体和常量提取的 SpEL key。
- `METHOD` 作用域限流。
- `GLOBAL` 作用域限流。
- 本地存储。
- Redis 存储。
- 滑动窗口日志算法。
- 滑动窗口计数器算法。
- 令牌桶算法。
- 漏桶算法。

自动化测试矩阵覆盖了所有算法和存储的组合：

| 算法 | 本地存储 | Redis 存储 |
| --- | --- | --- |
| `SLIDING_WINDOW_LOG` | 已测试 | 已测试 |
| `SLIDING_WINDOW_COUNTER` | 已测试 | 已测试 |
| `TOKEN_BUCKET` | 已测试 | 已测试 |
| `LEAKY_BUCKET` | 已测试 | 已测试 |

在两个支持的 Spring Boot 版本上运行同一矩阵，共产出 16 条发布验证路径：

- Spring Boot 2 + JDK 8：8 种组合。
- Spring Boot 3 + JDK 17：8 种组合。

## 环境要求

- Maven 3.8 或更高版本。
- JDK 8，用于默认的 Spring Boot 2 构建。
- JDK 17，用于 Spring Boot 3 profile。
- 当使用 `redis` profile 或运行完整示例测试矩阵时，默认使用 `localhost:6379`。
  如需连接其他 Redis 实例，可以设置 `QRL_REDIS_HOST` / `QRL_REDIS_PORT`。

运行下方命令之前，请先将 `JAVA_HOME` 设置为期望使用的 JDK。
安装命令在仓库根目录执行，运行示例应用的命令在 example 模块目录执行。

## 使用 Spring Boot 2 运行

默认构建使用 Spring Boot 2.7.x 和 Java 8 源码兼容性。

```powershell
mvn -pl qratelimiter-spring-boot-starter -am install
cd qratelimiter-spring-boot-example
mvn org.springframework.boot:spring-boot-maven-plugin:2.7.18:run
```

打开浏览器访问：

```text
http://localhost:8080/examples/algorithms/current/demo
```

默认响应内容为：

```json
{
  "scenario": "algorithm-current",
  "key": "demo",
  "algorithm": "SLIDING_WINDOW_LOG",
  "storage": "LOCAL"
}
```

## 使用 Spring Boot 3 运行

`spring-boot-3` Maven profile 将构建切换为 Spring Boot 3.2.x 和 JDK 17。

```powershell
mvn -Pspring-boot-3 -pl qratelimiter-spring-boot-starter -am install
cd qratelimiter-spring-boot-example
mvn -Pspring-boot-3 org.springframework.boot:spring-boot-maven-plugin:3.2.12:run
```

## 基础限流示例

端点：

```text
GET /examples/basic/users/{userId}
```

注解：

```java
@DoRateLimit(key = "#userId", freq = 2, interval = 60000L, capacity = 3)
```

用同一用户尝试三次：

```powershell
curl http://localhost:8080/examples/basic/users/u1001
curl http://localhost:8080/examples/basic/users/u1001
curl http://localhost:8080/examples/basic/users/u1001
```

第三次请求会返回 HTTP 429，因为该端点对同一用户 key 每分钟只允许两次请求。

## Template API 示例

端点：

```text
GET /examples/template/users/{userId}
```

这个端点演示编程式 API。控制器不使用 `@DoRateLimit`，而是注入
`RateLimiterTemplate` 并显式调用：

```java
boolean allowed = rateLimiterTemplate.tryAcquire("template:" + userId, 2, 60000L, 3);
```

这也是普通 Java / Maven 项目在不依赖 Spring Boot 注解生态时可以使用的核心 API。

## SpEL Key 示例

请求体字段：

```powershell
curl -X POST http://localhost:8080/examples/spel/object `
  -H "Content-Type: application/json" `
  -d '{"userId":"u1001","apiType":"search"}'
```

请求参数：

```powershell
curl "http://localhost:8080/examples/spel/combined?userId=u1001&apiType=search"
```

常量 key：

```powershell
curl http://localhost:8080/examples/spel/constant
```

这些示例说明 `key` 属性可以是普通的 SpEL 表达式，而不仅仅是单一的方法参数引用。

## 作用域示例

`METHOD` 作用域将相同的 key 按方法隔离：

```powershell
curl http://localhost:8080/examples/scope/method-a/u1001
curl http://localhost:8080/examples/scope/method-a/u1001
curl http://localhost:8080/examples/scope/method-b/u1001
```

`GLOBAL` 作用域在同一 key 下跨方法共享：

```powershell
curl http://localhost:8080/examples/scope/global-a/u1001
curl http://localhost:8080/examples/scope/global-b/u1001
```

## 算法 Profile

当前激活的算法由 `clazs.ratelimiter.algorithm` 指定。

默认滑动窗口日志：

```powershell
mvn org.springframework.boot:spring-boot-maven-plugin:2.7.18:run
```

滑动窗口计数器：

```powershell
mvn org.springframework.boot:spring-boot-maven-plugin:2.7.18:run `
  -Dspring-boot.run.profiles=sliding-window-counter
```

令牌桶：

```powershell
mvn org.springframework.boot:spring-boot-maven-plugin:2.7.18:run `
  -Dspring-boot.run.profiles=token-bucket
```

漏桶：

```powershell
mvn org.springframework.boot:spring-boot-maven-plugin:2.7.18:run `
  -Dspring-boot.run.profiles=leaky-bucket
```

查看当前激活的算法：

```powershell
curl http://localhost:8080/examples/algorithms/current/demo
```

## Redis 存储

启动 Redis（默认端口 `6379`），或通过 `QRL_REDIS_HOST` / `QRL_REDIS_PORT`
指定其他地址，然后运行：

```powershell
mvn org.springframework.boot:spring-boot-maven-plugin:2.7.18:run `
  -Dspring-boot.run.profiles=redis
```

查看当前存储模式：

```powershell
curl http://localhost:8080/examples/redis/users/u1001
```

Redis 可与算法 profile 组合使用：

```powershell
mvn org.springframework.boot:spring-boot-maven-plugin:2.7.18:run `
  -Dspring-boot.run.profiles=redis,token-bucket
```

相同的模式适用于：

- `redis`
- `redis,sliding-window-counter`
- `redis,token-bucket`
- `redis,leaky-bucket`

## 测试命令

Spring Boot 2 + JDK 8：

```powershell
mvn -pl qratelimiter-spring-boot-example -am clean test
```

Spring Boot 3 + JDK 17：

```powershell
mvn -Pspring-boot-3 -pl qratelimiter-spring-boot-example -am clean test
```

示例模块包含两组测试：

- `QRateLimiterExampleApplicationTest`：检查主要 Web 使用场景。
- `QRateLimiterExampleMatrixTest`：检查 4 算法 × 2 存储矩阵。

## 响应格式

成功响应包含当前激活的算法和存储：

```json
{
  "scenario": "basic",
  "key": "u1001",
  "message": "Two requests per minute per user",
  "algorithm": "SLIDING_WINDOW_LOG",
  "storage": "LOCAL",
  "timestamp": 1710000000000
}
```

触发限流时的响应使用 HTTP 429，并包含解析后的限流 key：

```json
{
  "status": 429,
  "error": "TOO_MANY_REQUESTS",
  "message": "basic demo rate limited",
  "limitKey": "cn.clazs.qratelimiter.example.controller.BasicLimitController.basicLimit:u1001"
}
```
