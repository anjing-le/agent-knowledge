# API Contract Guide

## 响应 envelope

后端统一返回：

```json
{
  "code": "0",
  "message": "操作成功",
  "data": {},
  "timestamp": 1710000000000,
  "requestId": "..."
}
```

字段清单：`code`、`message`、`data`、`timestamp`、`requestId`。

约束：

- 成功码固定为字符串 `"0"`。
- 前端 HTTP 层负责拆 envelope，业务 API 泛型写 `data` 类型。
- 旧 mock 的 `msg` 只在 HTTP helper 内兼容，不进入新业务模块。

## 分页

后端分页 payload：

```json
{
  "records": [],
  "current": 1,
  "size": 20,
  "total": 100
}
```

Java 使用 `PageResult<T>`，前端使用 `PaginatedResponse<T>`。

字段清单："records"、"current"、"size"、"total"。

## API path

- 后端：`ApiConstants`。
- 前端：`ApiPaths`。
- 机器来源：`contracts/service-boundaries.json`。

新增 RAG API 时先更新 service-boundary manifest，再生成前后端常量。

## OpenAPI

后端通过 `springdoc-openapi-starter-webmvc-api` 暴露 `/v3/api-docs`。

dev/test 默认开启，生产环境可通过：

```bash
OPENAPI_API_DOCS_ENABLED=false
```

关闭。

OpenAPI 生成前端类型的脚本保留在 `scripts/generate-openapi-frontend-types.js`。当前 V1 以手写业务 API + contract-generated paths 为主，V2 再扩大 generated client 使用范围。

## 请求上下文

前端统一发送：

- `X-Request-Id`
- `X-Trace-Id`
- `X-Time-Zone`
- `Accept-Language`

后端 `RequestContextFilter` 归一化并写入 MDC，`APIResponse` 回填 requestId。
