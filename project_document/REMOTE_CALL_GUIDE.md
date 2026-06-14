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

V2 异步任务接口在 `DOC_PARSER_MODE=async` 时启用。文件上传提交仍是 multipart，所以继续用 `RestTemplate`；异步 URL 提交和状态查询使用脚手架 `RemoteHttpClient` 的 `serviceId + path` 模式：

- `POST agent-doc-parser:/loader/deep_parse/async`
- `POST agent-doc-parser:/loader/status`

默认配置：

```yaml
app:
  doc-parser:
    base-url: ${DOC_PARSER_URL:http://localhost:9001}
    mode: ${DOC_PARSER_MODE:sync}
    timeout: 300000
    async:
      max-poll-attempts: ${DOC_PARSER_ASYNC_MAX_POLL_ATTEMPTS:30}
      poll-interval-ms: ${DOC_PARSER_ASYNC_POLL_INTERVAL_MS:1000}
      submit-only-enabled: ${DOC_PARSER_ASYNC_SUBMIT_ONLY_ENABLED:false}
      recovery-enabled: ${DOC_PARSER_ASYNC_RECOVERY_ENABLED:false}
      recovery-fixed-delay-ms: ${DOC_PARSER_ASYNC_RECOVERY_FIXED_DELAY_MS:15000}
      recovery-batch-size: ${DOC_PARSER_ASYNC_RECOVERY_BATCH_SIZE:20}
```

这些配置统一绑定到 `DocParserProperties`。`DocumentParsingService` 只负责选择 sync/async 模式，`DocumentAsyncParsingService` 负责 submit/poll 和状态落点。这样 Java 保持编排层职责，Python 继续拥有解析 runtime。

解析结果进入 RAG 主链路前会通过 `DocumentParseResultMapper.fromClientResult` 转成业务侧 `DocumentParseResult`。`DocumentProcessingService.continueAfterParsing` 是统一续跑入口，避免恢复轮询或 callback 另写一套切片/Embedding 逻辑。

`DocumentParserRecoveryPollingService` 复用 Spring `@Scheduled`，默认关闭。生产场景启用后，它会扫描可恢复 parser task 并继续调用 `RemoteHttpClient` 管理的 `/loader/status` 边界。

`DOC_PARSER_ASYNC_SUBMIT_ONLY_ENABLED=true` 时，Java 提交 parser task 后不会阻塞等待结果；它只保存 parser task 快照，后续由恢复轮询器调用 `/loader/status` 并通过 `continueAfterParsing` 续跑 RAG 链路。

异步解析状态会同时落到 Java 生命周期字段和 parser 原始快照字段：

- Java 生命周期：`status`、`phase`、`progress`、`message`。
- parser 快照：`parserTaskId`、`parserStatus`、`parserProgress`、`parserMessage`、`parserErrorMessage`、`parserStatusUpdateCount`、`parserLastPolledAt`。

可用边界探针复核 Java/Python 分工：

```bash
./scripts/probe-doc-parser-boundary.sh --contract-only
./scripts/check-doc-parser-lifecycle.sh
```

如果后端和 doc-parser 都已启动，可以执行 `./scripts/probe-doc-parser-boundary.sh --live`，同时验证 Python `/health` 与 Java `/api/test/health` 中的 `downstreams.docParser`。

`check-doc-parser-lifecycle.sh` 进一步校验 `PENDING/RUNNING/SUCCEEDED/FAILED/CANCELED` 如何从 Python 异步任务映射到 Java `document_processing_task` 和 `DocumentStatus`。

## 模型服务调用

EmbeddingService 和 LLMService 调用 OpenAI-compatible 第三方模型 API。它们不是 agent-knowledge 内部服务边界，因此使用 `RemoteHttpClient` 的 absolute URL 模式：

- `EmbeddingService` -> `targetService=embedding-provider`
- `LLMService` -> `targetService=llm-provider`

这样可以继续复用脚手架的超时、重试、请求上下文、调用观测和错误归一化，同时保留 `app.embedding.api-url`、`app.llm.api-url` 这类模型 provider 配置。

`dev/test` 默认使用 `local-demo` provider，不访问外部模型服务；切到 `EMBEDDING_PROVIDER=remote` 或 `LLM_PROVIDER=remote` 后才通过 `RemoteHttpClient` 调用真实 provider。

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
