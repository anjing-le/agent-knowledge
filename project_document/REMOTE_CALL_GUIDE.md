# Remote Call Guide

## 当前远程调用

agent-knowledge 当前有三类出站调用：

1. Java 后端调用 Python doc-parser。
2. Java 后端调用 Embedding API。
3. Java 后端调用 LLM Chat API。

## doc-parser 调用

V1 使用 `DocParserClient` + `RestTemplate` 调用：

- `GET ${DOC_PARSER_URL}/health`
- `POST ${DOC_PARSER_URL}/parse`
- `POST ${DOC_PARSER_URL}/parse_url`

默认配置：

```yaml
app:
  doc-parser:
    base-url: ${DOC_PARSER_URL:http://localhost:9001}
    timeout: 300000
```

由于 `/parse` 是 multipart 文件上传，当前保留 `RestTemplate`。V2 接异步 JSON 任务接口时，可进一步迁移到 `RemoteHttpClient` 的 `serviceId + path` 模式。

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
