# Doc Parser Service Guide

## 定位

doc-parser 是独立 Python FastAPI 服务，负责文档解析、OCR、版面分析、表格/图片处理和解析结果 metadata 生成。

它不属于 Java 后端模块，也不应该被粗暴塞进 Spring Boot。Java 后端只通过 HTTP 调用它。

## 服务边界

Python doc-parser 负责：

- PDF、Word、PPT、Excel、图片、TXT 等格式解析。
- OCR、layout、table vision、image vision 等 Python 生态能力。
- 返回纯文本、chunks、页码、content_type 等 metadata。
- V2/V3 的长任务解析、进度查询、多实例处理。

Java 后端负责：

- 知识库、文档、Chunk、会话和消息等业务模型。
- 上传文件保存、文档状态机和失败原因。
- 调用 doc-parser 并消费解析结果。
- Embedding、向量检索、上下文组装和 LLM 回答。
- 引用来源和前端 API。

## V1 同步接口

Java `DocParserClient` 当前使用：

| 方法 | 路径 | 用途 |
|------|------|------|
| `GET` | `/health` | 健康检查 |
| `POST` | `/parse` | 上传本地文件并同步解析 |
| `POST` | `/parse_url` | 通过 URL 下载并同步解析 |

同步响应核心字段：

```json
{
  "content": "全文内容",
  "chunks": [
    {
      "content": "切片内容",
      "index": 0,
      "length": 120,
      "tokenCount": 60,
      "metadata": {
        "page_idx": [1],
        "content_type": "TEXT"
      }
    }
  ],
  "metadata": {
    "filename": "demo.pdf",
    "doc_type": "DOCUMENT_BASIC",
    "parser_id": "general",
    "total_chunks": 1
  }
}
```

失败响应使用 `success:false` 和 `error` 字段，Java 客户端必须识别并转成解析失败。

## V2 异步接口

doc-parser 已提供任务式接口，适合长文档、OCR 和多实例：

| 方法 | 路径 | 用途 |
|------|------|------|
| `POST` | `/loader/deep_parse/async` | 创建解析任务 |
| `POST` | `/loader/status` | 查询解析结果 |
| `POST` | `/loader/kill_task` | 终止任务 |
| `GET` | `/loader/doc_types` | 查询支持类型（multi-instance app） |

### V2 请求/响应草案

创建任务：

```http
POST /loader/deep_parse/async
```

请求可以是 `multipart/form-data` 文件上传，也可以是 multi-instance app 的 JSON 文件引用。Java 侧必须带上可追踪 metadata：

```json
{
  "file_url": "https://example.com/demo.pdf",
  "doc_type": "DOCUMENT_BASIC",
  "metadata": {
    "docId": "doc_001",
    "kbId": "kb_001",
    "requestId": "req_001"
  }
}
```

Python `kparser.app` 会把 `file/doc_type/metadata` 的 multipart 请求和 `file_url/doc_type/metadata` 的 JSON 请求统一归一成 `task_id`。旧 OSS 入口仍可使用 `request_id/original_url`，但返回给 Java 的 submit response 统一保持 `success/task_id/status/message`。

响应：

```json
{
  "success": true,
  "task_id": "parser_task_001",
  "status": "PENDING",
  "message": "task accepted"
}
```

查询任务：

```http
POST /loader/status
```

```json
{
  "task_id": "parser_task_001"
}
```

`/loader/status` 同时接受 `task_id`、`taskId`、`request_id`、`requestId`，内部统一按 `task_id` 查询，方便旧调用方平滑迁移。

成功响应的 `result` 与 V1 `/parse` 保持同形：

```json
{
  "success": true,
  "task_id": "parser_task_001",
  "status": "SUCCEEDED",
  "progress": 1.0,
  "result": {
    "content": "全文内容",
    "chunks": [],
    "metadata": {}
  }
}
```

### Java 状态映射

前端只轮询 Java 后端，不直接访问 Python doc-parser。Java 将 Python 任务状态映射到 `document` 和 `document_processing_task`：

| doc-parser 状态 | Java document.status | task.status | task.phase | 说明 |
|-----------------|----------------------|-------------|------------|------|
| `PENDING` | `PARSING` | `PENDING` | `PARSING` | 任务已创建，等待 Python worker |
| `RUNNING` | `PARSING` | `RUNNING` | `PARSING` | Python 正在解析/OCR/layout |
| `SUCCEEDED` | `CHUNKING` | `RUNNING` | `CHUNKING` | Java 已拿到解析结果，开始本地切片/Embedding |
| `FAILED` | `PARSE_FAILED` | `FAILED` | `PARSING` | 解析失败，保存 Python 错误信息 |
| `CANCELED` | `PARSE_FAILED` | `FAILED` | `PARSING` | 用户或系统终止任务 |

### V2 Java 集成现状

Java 侧已经落地 `DocumentAsyncParsingService`，并通过配置保持渐进启用：

```yaml
app:
  doc-parser:
    mode: ${DOC_PARSER_MODE:sync}
    async:
      max-poll-attempts: ${DOC_PARSER_ASYNC_MAX_POLL_ATTEMPTS:30}
      poll-interval-ms: ${DOC_PARSER_ASYNC_POLL_INTERVAL_MS:1000}
      submit-only-enabled: ${DOC_PARSER_ASYNC_SUBMIT_ONLY_ENABLED:false}
      recovery-enabled: ${DOC_PARSER_ASYNC_RECOVERY_ENABLED:false}
      recovery-fixed-delay-ms: ${DOC_PARSER_ASYNC_RECOVERY_FIXED_DELAY_MS:15000}
      recovery-batch-size: ${DOC_PARSER_ASYNC_RECOVERY_BATCH_SIZE:20}
```

这些配置统一绑定到脚手架式 `DocParserProperties`，业务服务通过构造注入读取配置，不再散落 `@Value`。默认 `sync` 继续走 V1 `/parse`，用于教学和轻量 demo。切到 `DOC_PARSER_MODE=async` 后，Java 会向 `/loader/deep_parse/async` 提交任务，再通过 `/loader/status` 轮询，并把 Python 状态写入 Java 文档任务生命周期。

`DocParserClient` 返回的是传输层 DTO，进入 RAG 处理编排前会通过 `DocumentParseResultMapper` 转成业务侧 `DocumentParseResult`。`DocumentProcessingService.continueAfterParsing` 是解析完成后的统一续跑入口，后续无论阻塞轮询、定时恢复还是 callback，拿到 `DocumentParseResult` 后都应复用同一条切片、Embedding、向量写入链路。

`DocumentParserRecoveryPollingService` 是默认关闭的恢复轮询协调器。只有同时满足 `DOC_PARSER_MODE=async` 和 `DOC_PARSER_ASYNC_RECOVERY_ENABLED=true` 时，它才会按 `recovery-fixed-delay-ms` 扫描 `document_processing_task` 中 `parserTaskId` 非空且仍处于 `PARSING` 阶段的任务，查询 `/loader/status`，成功时调用 `DocumentProcessingService.continueAfterParsing` 续跑。

`DOC_PARSER_ASYNC_SUBMIT_ONLY_ENABLED=true` 会把 async 解析切成真正非阻塞模式：Java 提交 Python parser 任务并返回 `DocumentParseResult.deferred`，当前处理线程停在解析阶段，后续由恢复轮询器继续推进。生产启用 submit-only 时应同时开启 `DOC_PARSER_ASYNC_RECOVERY_ENABLED=true`。

Python 侧已在 `doc-parser/kparser/app.py` 中对齐 V2 contract：

- `_async_submit_success`：统一 submit response。
- `_async_status_response`：统一 status response，并把内部 `complete/failed/killed/in_progress` 映射为 `SUCCEEDED/FAILED/CANCELED/PENDING/RUNNING`。
- `_run_uploaded_file_parse_task`：承接 Java multipart 文件异步解析。
- `_run_url_file_parse_task`：承接 JSON `file_url` 异步解析。

### V2 Java 集成原则

- `document_processing_task.parserTaskId` 保存 Python `task_id`，用于轮询和故障排查。
- `document_processing_task` 同步保存 parser 原始快照：`parserStatus`、`parserProgress`、`parserMessage`、`parserErrorMessage`、`parserStatusUpdateCount`、`parserLastPolledAt`，避免只剩 Java 映射后的阶段状态。
- parser result 进入 `DocumentProcessingService.continueAfterParsing` 前必须先转成 `DocumentParseResult`，主处理编排不直接依赖 `DocParserClient`。
- submit-only 模式下 `DocumentProcessingService` 只负责提交 parser task 和保持解析中状态，不占用后台线程等待 Python 完成。
- 恢复轮询默认关闭，避免教学/demo 环境出现双重轮询；生产启用时通过 `DOC_PARSER_ASYNC_RECOVERY_ENABLED=true` 显式打开。
- 前端轮询 Java 后端，不直接轮询 Python。
- Java 后端统一处理重试、超时、失败恢复和用户可见状态。
- 将解析结果落地后再进入 chunk persistence 和 embedding pipeline。
- 解析轮询应运行在 Java 后端异步执行器中，并继续通过 `RequestContextTaskDecorator` 透传 requestId/traceId。
- 超时策略由 Java 控制：超过最大轮询次数后标记 `PARSE_FAILED`，保留最后一次 Python 状态。
- 重试策略由 Java 控制：重试会创建新的 `document_processing_task`，避免覆盖历史失败记录。
- 每次调整 V2 语义必须更新 [../contracts/doc-parser-contract.json](../contracts/doc-parser-contract.json) 并补充 Java 客户端/生命周期契约测试。

## 启动

```bash
cd doc-parser
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python -m uvicorn kparser.app:app --host 0.0.0.0 --port 9001
```

健康检查：

```bash
curl http://localhost:9001/health
```

边界探针：

```bash
./scripts/probe-doc-parser-boundary.sh --contract-only
./scripts/check-doc-parser-lifecycle.sh
./scripts/probe-doc-parser-boundary.sh --live
```

`--contract-only` 只检查机器契约、Java `DocParserClient` 调用路径和 Python FastAPI 路由；`--live` 会额外访问 `DOC_PARSER_URL/health` 和 Java 后端 `/api/test/health`，用于现场演示 Java 只通过 HTTP 观察 Python 服务。

`check-doc-parser-lifecycle.sh` 会校验 `contracts/doc-parser-contract.json` 的 `javaStatusMapping` 与 `DocParserStatusMapper`、`DocumentProcessingProgressService`、`DocumentProcessingTaskService` 和对应测试一致，避免 V2 异步解析接入时状态语义漂移。

机器可读契约见 [../contracts/doc-parser-contract.json](../contracts/doc-parser-contract.json)。
