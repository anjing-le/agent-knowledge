# Remote Call Guide

## 当前远程调用

agent-knowledge 当前有三类出站调用：

1. Java 后端调用 Python doc-parser。
2. Java 后端调用 Embedding API。
3. Java 后端调用 LLM Chat API。

## doc-parser 调用

V1 同步解析使用 `DocParserClient` + `RestTemplate` 调用：

- `GET ${DOC_PARSER_URL}/health`
- `POST ${DOC_PARSER_URL}/parse`
- `POST ${DOC_PARSER_URL}/parse_url`

V2 异步 JSON 任务接口使用脚手架 `RemoteHttpClient` 的 `serviceId + path` 模式调用：

- `POST agent-doc-parser:/loader/deep_parse/async`
- `POST agent-doc-parser:/loader/status`

默认配置：

```yaml
app:
  doc-parser:
    base-url: ${DOC_PARSER_URL:http://localhost:9001}
    timeout: 300000
```

由于 `/parse` 和异步文件上传是 multipart 文件上传，当前保留 `RestTemplate`。异步 URL 提交和状态查询已经迁移到 `RemoteHttpClient`，用于复用脚手架的服务发现、上下文透传、重试策略和调用观测。

## 模型服务调用

EmbeddingService 和 LLMService 调用 OpenAI-compatible 第三方模型 API。它们不是 agent-knowledge 内部服务边界，因此使用 `RemoteHttpClient` 的 absolute URL 模式：

- `EmbeddingService` -> `targetService=embedding-provider`
- `LLMService` -> `targetService=llm-provider`

这样可以继续复用脚手架的超时、重试、请求上下文、调用观测和错误归一化，同时保留 `app.embedding.api-url`、`app.llm.api-url` 这类模型 provider 配置。

## RemoteHttpClient 基线

脚手架能力已迁入：

```yaml
app:
  remote-http:
    default-caller-id: ${APP_REMOTE_CALLER_ID:agent-knowledge}
    connect-timeout-ms: ${REMOTE_HTTP_CONNECT_TIMEOUT_MS:3000}
    read-timeout-ms: ${REMOTE_HTTP_READ_TIMEOUT_MS:300000}
    service-base-urls:
      agent-knowledge: ${AGENT_KNOWLEDGE_BASE_URL:http://localhost:10001}
      agent-doc-parser: ${DOC_PARSER_URL:http://localhost:9001}
```

适合后续：

- doc-parser 异步任务接口。
- 内部 LLM Gateway。
- 认证中心、文件中心、向量服务等拆分服务。

## 上下文透传

服务间调用应透传：

- `X-Request-Id`
- `X-Trace-Id`
- `X-Tenant-Id`
- `X-User-Id`
- `X-User-Name`
- `X-User-Roles`
- `X-Caller-Id`
- `X-Time-Zone`
- `Accept-Language`

不允许把 API Key、Authorization 明文写入日志描述。
