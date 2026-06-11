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

V2 Java 集成建议：

- 新增解析任务表，保存 `requestId/taskId/status/progress/result/errorMsg`。
- 文档上传后异步创建解析任务，前端轮询 Java 后端，不直接轮询 Python。
- Java 后端统一处理重试、超时、失败恢复和用户可见状态。
- 将解析结果落地后再进入 chunk persistence 和 embedding pipeline。

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

机器可读契约见 [../contracts/doc-parser-contract.json](../contracts/doc-parser-contract.json)。
