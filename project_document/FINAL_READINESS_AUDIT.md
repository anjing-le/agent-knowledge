# Final Readiness Audit

本文定义 agent-knowledge 的最终版验收口径：它不是生产级 RAGFlow，而是基于 `infra-dev-scaffolding` 生长出来的 V1 teaching baseline。学习者只需要关注 RAG agent 设计，工程技术栈、质量门禁、API 习惯和服务边界都来自脚手架。

## Scope

V1 teaching baseline 必须证明：

- RAG 主链路清楚：文档上传、Python doc-parser、切片、Embedding、检索、上下文组装、LLM 回答和答案引用。
- Java 后端和 Python doc-parser 边界清楚：Java 后端只通过 HTTP contract 调用独立 Python doc-parser 服务。
- 脚手架继承关系清楚：Vue/TypeScript/Vite、Spring Boot/Java 17、统一响应、分页、OpenAPI、请求上下文、质量脚本和 service-boundary manifest 都沿用脚手架习惯。
- 教学证据清楚：RAG demo、retrieval evaluation、citation evidence、adapter status 和 ingestion runtime 都有脚本化证据入口。
- V2/V3 extension 和 V1 teaching baseline 分开：生产化 adapter、外部向量库、真实 rerank、权限审计和多知识库路由属于继续扩展，不影响 V1 教学基线成立。

## Completion Bar

当前目标收敛为“能教学、能本地跑通、能解释业务设计从脚手架生长出来”的 V1 RAG agent，而不是继续堆生产级平台能力。

V1 可以认为通过的标准：

- `README.md` 能在 5 分钟内说明项目定位、脚手架继承关系、RAG 主链路和验证入口。
- `./scripts/quality-gate.sh` 通过，证明脚手架契约、OpenAPI、服务边界、后端测试、前端构建和运行态探针没有断。
- `./scripts/probe-rag-demo-runtime.sh` 和 `./scripts/probe-rag-ingestion-runtime.sh` 能分别证明 seed 问答链路和真实上传解析链路。
- Pipeline、Knowledge、Retrieval、Chat 页面能串起 `upload/seed -> chunk -> retrieval score -> context trace -> citation`。
- `docs/evidence/YYYY-MM-DD/` 能在演示日收集运行证据；截图属于教学发布材料，不作为代码功能继续膨胀的理由。

V1 明确不继续追的内容：

- 不为了“像生产平台”而强行引入 Milvus、队列、网关、多租户、计费或复杂权限。
- 不把 Python doc-parser 塞进 Java 后端；Java 继续只通过 HTTP contract 编排。
- 不把 adapter skeleton 伪装成生产已落地能力；它们只表示从教学默认实现切换到生产实现的边界。

## Current Evidence

| Requirement | Evidence | Status |
| --- | --- | --- |
| Scaffold inheritance | `contracts/scaffold-stack-contract.json`, `scripts/check-scaffold-source.sh`, `scripts/check-scaffold-alignment.js`, `scripts/quality-gate.sh` | Ready |
| API and shared contracts | `contracts/platform-contract.json`, `contracts/service-boundaries.json`, `frontend/src/contracts/openapi`, `project_document/API_CONTRACT_GUIDE.md` | Ready |
| Java backend modules | `DocumentIngestionService`, `DocumentProcessingService`, `RetrievalService`, `RagChatOrchestrationService`, `RagEvidenceReportService` | Ready |
| Python doc-parser boundary | `contracts/doc-parser-contract.json`, `project_document/DOC_PARSER_SERVICE_GUIDE.md`, `scripts/probe-doc-parser-boundary.sh --contract-only` | Ready |
| RAG demo runtime | `scripts/probe-rag-demo-runtime.sh`, `scripts/smoke-rag-demo.sh`, `scripts/seed-rag-demo.sh`, `scripts/evaluate-rag-retrieval.sh` | Ready |
| RAG ingestion runtime | `scripts/probe-rag-ingestion-runtime.sh` starts Python doc-parser and Java backend, uploads a file, waits for parsing, chunking, embedding and retrieval | Ready |
| Citation evidence | `scripts/collect-demo-evidence.sh`, `runtime/rag-citation-evidence.json`, `runtime/rag-citation-evidence.md`, `RagEvidenceReportService` | Ready |
| Retrieval adapter teaching path | `contracts/retrieval-adapter-contract.json`, `project_document/RETRIEVAL_ADAPTER_GUIDE.md`, `project_document/RETRIEVAL_ADAPTER_SWITCH_GUIDE.md`, `scripts/probe-retrieval-adapters.sh` | Ready |
| Production profile path | `backend/.env.prod-adapters.example`, `backend/src/main/resources/application-prod-adapters.yml`, `scripts/probe-production-adapter-profile.sh --dry-run` | Ready as adapter skeleton |
| Frontend teaching surface | `frontend/src/views/pipeline/index.vue`, `frontend/src/views/knowledge`, `frontend/src/views/retrieval`, `frontend/src/views/chat` | Ready |
| Evidence package flow | `docs/evidence/YYYY-MM-DD/`, `docs/evidence/TEMPLATE.md`, `./scripts/collect-demo-evidence.sh --date YYYY-MM-DD --force` | Ready to collect per demo date |
| GitHub quality gate | `.github/workflows/quality-gate.yml` runs `./scripts/quality-gate.sh` on `main/master` push and PR | Ready |

## Final Runbook

在标记一次演示或发布为通过前，按顺序执行：

```bash
./scripts/quality-gate.sh
./scripts/collect-demo-evidence.sh --date YYYY-MM-DD --force
```

可选现场补充：

```bash
./scripts/smoke-doc-parser-async.sh
./scripts/probe-rag-ingestion-runtime.sh
```

证据包落点：

```text
docs/evidence/YYYY-MM-DD/
  README.md
  outputs/
  runtime/
  screenshots/
```

截图不是默认门禁的一部分，但教学发布时建议补齐：

- `screenshots/rag-pipeline.png`
- `screenshots/retrieval-auto-search.png`
- `screenshots/chat-with-citations.png`

## No-Go

以下情况不能认为最终版通过：

- `./scripts/quality-gate.sh` 失败。
- Java 后端绕过 HTTP contract 直接内嵌 Python doc-parser 逻辑。
- 新增运行接口没有进入 `ApiConstants`、`ApiPaths`、OpenAPI 或 service-boundary manifest。
- 前端页面绕过 `openApiRequest`、`ApiPaths` 或生成的 OpenAPI 类型直接散落 `/api/**`。
- 证据包包含 API keys、cookies、tokens、个人路径或私有上传文件。
- README 不能解释项目如何从 `infra-dev-scaffolding` 生长成 RAG agent。

## V2/V3 Extension

以下能力属于后续增强，不阻塞 V1 teaching baseline：

- Milvus adapter 或托管向量库 adapter。
- 真实外部 Embedding/LLM/Rerank provider 的线上压测。
- 多知识库路由、query rewrite 和 answer quality panel。
- 页码、表格、图片、坐标级引用定位。
- 多租户权限、审计、用量计费和网关集成。
