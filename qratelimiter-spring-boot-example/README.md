# QRateLimiter Spring Boot Example

This module is a runnable Spring Boot example for QRateLimiter. It demonstrates
the starter in a real Web application, including annotation-based rate limiting,
programmatic `RateLimiterTemplate` usage, SpEL keys, method/global scopes, local
storage, Redis storage, and all supported algorithms.

Runtime management endpoints are disabled by default. To enable the 1.6.0
management surface for local checks, set:

```yaml
clazs:
  ratelimiter:
    management:
      enabled: true
      base-path: /qratelimiter
```

Then use `GET /qratelimiter/stats`, `GET /qratelimiter/config`,
`POST /qratelimiter/config`, `DELETE /qratelimiter/cache`, or
`DELETE /qratelimiter/cache/{key}`.

Documentation:

- [English Guide](README_en.md)
- [中文指南](README_zh.md)

Release verification for this module covers:

- Spring Boot 2 with JDK 8.
- Spring Boot 3 with JDK 17.
- Local and Redis storage.
- `SLIDING_WINDOW_LOG`, `SLIDING_WINDOW_COUNTER`, `TOKEN_BUCKET`, and
  `LEAKY_BUCKET`.

Together, these form 16 verification directions across Boot version, storage,
and algorithm combinations.
