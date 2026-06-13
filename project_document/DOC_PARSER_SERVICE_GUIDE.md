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

### V2 Java 集成建议

- 新增解析任务表，保存 `requestId/taskId/status/progress/result/errorMsg`。
- 文档上传后异步创建解析任务，前端轮询 Java 后端，不直接轮询 Python。
- Java 后端统一处理重试、超时、失败恢复和用户可见状态。
- 将解析结果落地后再进入 chunk persistence 和 embedding pipeline。
- `document_processing_task.parserTaskId` 保存 Python `task_id`，用于轮询和故障排查。
- 解析轮询应运行在 Java 后端异步执行器中，并继续通过 `RequestContextTaskDecorator` 透传 requestId/traceId。
- 超时策略由 Java 控制：超过最大轮询次数后标记 `PARSE_FAILED`，保留最后一次 Python 状态。
- 重试策略由 Java 控制：重试会创建新的 `document_processing_task`，避免覆盖历史失败记录。
- V2 实现前必须更新 [../contracts/doc-parser-contract.json](../contracts/doc-parser-contract.json) 并补充 Java 客户端契约测试。

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
./scripts/probe-doc-parser-boundary.sh --live
```

`--contract-only` 只检查机器契约、Java `DocParserClient` 调用路径和 Python FastAPI 路由；`--live` 会额外访问 `DOC_PARSER_URL/health` 和 Java 后端 `/api/test/health`，用于现场演示 Java 只通过 HTTP 观察 Python 服务。

机器可读契约见 [../contracts/doc-parser-contract.json](../contracts/doc-parser-contract.json)。
