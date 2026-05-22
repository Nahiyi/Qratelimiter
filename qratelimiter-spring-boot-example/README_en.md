# QRateLimiter Spring Boot Example

`qratelimiter-spring-boot-example` is a runnable demonstration module for the
QRateLimiter Spring Boot starter. It shows how the annotation contract behaves
in a real Web application and how the same code can be verified on Spring Boot 2
and Spring Boot 3.

The module is intended for local verification, feature demonstrations, and
manual checks before releasing the starter.

Runtime management endpoints are disabled by default. To try the 1.6.0
management surface locally, enable:

```yaml
clazs:
  ratelimiter:
    management:
      enabled: true
      base-path: /qratelimiter
```

Then call `GET /qratelimiter/stats`, `GET /qratelimiter/config`,
`POST /qratelimiter/config`, `DELETE /qratelimiter/cache`, or
`DELETE /qratelimiter/cache/{key}`.

## What It Covers

The example includes endpoints for:

- Basic per-key rate limiting.
- Programmatic `RateLimiterTemplate` usage.
- SpEL keys from path variables, request parameters, request bodies, and constants.
- `METHOD` scoped limits.
- `GLOBAL` scoped limits.
- Local storage.
- Redis storage.
- Sliding window log.
- Sliding window counter.
- Token bucket.
- Leaky bucket.

The automated test matrix covers all algorithm and storage combinations:

| Algorithm | Local | Redis |
| --- | --- | --- |
| `SLIDING_WINDOW_LOG` | tested | tested |
| `SLIDING_WINDOW_COUNTER` | tested | tested |
| `TOKEN_BUCKET` | tested | tested |
| `LEAKY_BUCKET` | tested | tested |

Running the same matrix on both supported Spring Boot versions gives 16 release
verification directions:

- 8 combinations on Spring Boot 2 with JDK 8.
- 8 combinations on Spring Boot 3 with JDK 17.

## Requirements

- Maven 3.8 or newer.
- JDK 8 for the default Spring Boot 2 build.
- JDK 17 for the Spring Boot 3 profile.
- Redis on `localhost:6379` by default when using the `redis` profile or running
  the full example test matrix. Set `QRL_REDIS_HOST` / `QRL_REDIS_PORT` to use a
  different Redis instance.

Set `JAVA_HOME` to the JDK you want to use before running the commands below.
Run the install command from the repository root, then run the example from the
example module directory.

## Run With Spring Boot 2

The default build uses Spring Boot 2.7.x and Java 8 source compatibility.

```powershell
mvn -pl qratelimiter-spring-boot-starter -am install
cd qratelimiter-spring-boot-example
mvn org.springframework.boot:spring-boot-maven-plugin:2.7.18:run
```

Open:

```text
http://localhost:8080/examples/algorithms/current/demo
```

The default response uses:

```json
{
  "scenario": "algorithm-current",
  "key": "demo",
  "algorithm": "SLIDING_WINDOW_LOG",
  "storage": "LOCAL"
}
```

## Run With Spring Boot 3

The `spring-boot-3` Maven profile switches the build to Spring Boot 3.2.x and
JDK 17.

```powershell
mvn -Pspring-boot-3 -pl qratelimiter-spring-boot-starter -am install
cd qratelimiter-spring-boot-example
mvn -Pspring-boot-3 org.springframework.boot:spring-boot-maven-plugin:3.2.12:run
```

## Basic Limit Example

Endpoint:

```text
GET /examples/basic/users/{userId}
```

Annotation:

```java
@DoRateLimit(key = "#userId", freq = 2, interval = 60000L, capacity = 3)
```

Try the same user three times:

```powershell
curl http://localhost:8080/examples/basic/users/u1001
curl http://localhost:8080/examples/basic/users/u1001
curl http://localhost:8080/examples/basic/users/u1001
```

The third request returns HTTP 429 because the endpoint allows two requests per
minute for the same user key.

## Template API Example

Endpoint:

```text
GET /examples/template/users/{userId}
```

This endpoint demonstrates the programmatic API. Instead of using
`@DoRateLimit`, the controller injects `RateLimiterTemplate` and calls:

```java
boolean allowed = rateLimiterTemplate.tryAcquire("template:" + userId, 2, 60000L, 3);
```

This is the same core API that can be used outside Spring Boot.

## SpEL Key Examples

Request body fields:

```powershell
curl -X POST http://localhost:8080/examples/spel/object `
  -H "Content-Type: application/json" `
  -d '{"userId":"u1001","apiType":"search"}'
```

Request parameters:

```powershell
curl "http://localhost:8080/examples/spel/combined?userId=u1001&apiType=search"
```

Constant key:

```powershell
curl http://localhost:8080/examples/spel/constant
```

These examples demonstrate that the `key` attribute can be a normal SpEL
expression, not only a single argument reference.

## Scope Examples

`METHOD` scope keeps identical keys isolated by method:

```powershell
curl http://localhost:8080/examples/scope/method-a/u1001
curl http://localhost:8080/examples/scope/method-a/u1001
curl http://localhost:8080/examples/scope/method-b/u1001
```

`GLOBAL` scope shares the same key across methods:

```powershell
curl http://localhost:8080/examples/scope/global-a/u1001
curl http://localhost:8080/examples/scope/global-b/u1001
```

## Algorithm Profiles

The active algorithm is selected by `clazs.ratelimiter.algorithm`.

Default sliding window log:

```powershell
mvn org.springframework.boot:spring-boot-maven-plugin:2.7.18:run
```

Sliding window counter:

```powershell
mvn org.springframework.boot:spring-boot-maven-plugin:2.7.18:run `
  -Dspring-boot.run.profiles=sliding-window-counter
```

Token bucket:

```powershell
mvn org.springframework.boot:spring-boot-maven-plugin:2.7.18:run `
  -Dspring-boot.run.profiles=token-bucket
```

Leaky bucket:

```powershell
mvn org.springframework.boot:spring-boot-maven-plugin:2.7.18:run `
  -Dspring-boot.run.profiles=leaky-bucket
```

Check the active algorithm:

```powershell
curl http://localhost:8080/examples/algorithms/current/demo
```

## Redis Storage

Start Redis on port `6379`, or set `QRL_REDIS_HOST` / `QRL_REDIS_PORT` for a
different address, then run:

```powershell
mvn org.springframework.boot:spring-boot-maven-plugin:2.7.18:run `
  -Dspring-boot.run.profiles=redis
```

Check the storage mode:

```powershell
curl http://localhost:8080/examples/redis/users/u1001
```

Combine Redis with an algorithm profile:

```powershell
mvn org.springframework.boot:spring-boot-maven-plugin:2.7.18:run `
  -Dspring-boot.run.profiles=redis,token-bucket
```

The same pattern works with:

- `redis`
- `redis,sliding-window-counter`
- `redis,token-bucket`
- `redis,leaky-bucket`

## Test Commands

Spring Boot 2 and JDK 8:

```powershell
mvn -pl qratelimiter-spring-boot-example -am clean test
```

Spring Boot 3 and JDK 17:

```powershell
mvn -Pspring-boot-3 -pl qratelimiter-spring-boot-example -am clean test
```

The example module contains two groups of tests:

- `QRateLimiterExampleApplicationTest` checks the main Web usage scenarios.
- `QRateLimiterExampleMatrixTest` checks the 4 algorithm x 2 storage matrix.

## Response Shape

Successful responses include the active algorithm and storage:

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

Rate-limited responses use HTTP 429 and include the resolved limit key:

```json
{
  "status": 429,
  "error": "TOO_MANY_REQUESTS",
  "message": "basic demo rate limited",
  "limitKey": "cn.clazs.qratelimiter.example.controller.BasicLimitController.basicLimit:u1001"
}
```
