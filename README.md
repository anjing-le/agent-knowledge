# Agent Knowledge

基于 `infra-dev-scaffolding` 生长出来的 RAG 智能知识库，也是一个高级 agent 教学示例。

学习这个项目时，只需要重点看 RAG 设计；工程底座、技术栈、API 习惯、质量门禁和前后端约定都沿用脚手架。

```text
文档上传 -> Python doc-parser -> 切片 -> Embedding -> 混合检索 -> 上下文组装 -> LLM 回答 -> 答案引用
```

## V1 完成口径

V1 不是生产级 RAG 平台，而是一个能教学、能本地跑通、能展示完整业务背景的 RAG agent：

- 底座来自 `infra-dev-scaffolding`，不重新发明技术栈和工程习惯。
- 本地 demo 能跑通 seed、上传解析、切片、检索、上下文组装和带引用回答。
- 前端能从 Pipeline、Knowledge、Retrieval、Chat 四个页面讲清楚证据链。
- pgvector、BM25、Elasticsearch、remote rerank、async doc-parser 是后续替换边界，不阻塞 V1。

## 你会看到什么

- 知识库、文档上传、解析任务、切片、Embedding 和向量检索。
- Retrieval adapter：本地演示实现，以及 pgvector / BM25 / Elasticsearch / remote rerank 的生产替换边界。
- Chat RAG：上下文组装、prompt trace、引用证据和 scoreExplanation。
- Python `doc-parser` 独立服务：Java 后端只通过 HTTP 调用它，不把解析能力塞进后端。
- 证据包脚本：把 seed、evaluate、retrieval、chat、citation evidence 沉淀成可教学材料。

## 从脚手架继承什么

agent-knowledge 不重新设计工程底座，默认继承 `infra-dev-scaffolding` 的约束：

- 技术栈：Vue 3.5 + TypeScript + Vite 7，Spring Boot 3.4.5 + Java 17，Python FastAPI doc-parser。
- API 习惯：`APIResponse<T>`、`PageResult<T>`、`ApiConstants`、OpenAPI 生成类型、统一请求上下文。
- 治理入口：`./scripts/quality-gate.sh`、契约检查、复制 smoke、运行态探针和 evidence dry-run。
- CI 入口：`.github/workflows/quality-gate.yml` 在 `main/master` push 和 PR 上执行同一套脚手架质量门禁。
- 设计约束：[project_document/PROJECT_CONSTRAINTS.md](./project_document/PROJECT_CONSTRAINTS.md)、[project_document/NEW_MODULE_GUIDE.md](./project_document/NEW_MODULE_GUIDE.md)、[project_document/UI_DESIGN_GUIDE.md](./project_document/UI_DESIGN_GUIDE.md)。
- 接入提示词：[project_document/SCAFFOLD_ADOPTION_PROMPT.md](./project_document/SCAFFOLD_ADOPTION_PROMPT.md)。
- 演示证据：[project_document/DEMO_EVIDENCE.md](./project_document/DEMO_EVIDENCE.md)。

## 项目结构

```text
backend/           Spring Boot 后端：知识库、文档、检索、聊天和 RAG orchestration
frontend/          Vue 前端：知识库、文档、检索、聊天和 pipeline 教学页
doc-parser/        Python FastAPI 文档解析服务
contracts/         平台契约、服务边界、doc-parser 和 retrieval adapter 契约
project_document/  设计、边界、路线图、状态和验证记录
```

## 本地启动

完整说明见 [project_document/LOCAL_STARTUP_GUIDE.md](./project_document/LOCAL_STARTUP_GUIDE.md)。

```bash
# 1. doc-parser: http://localhost:9001
(cd doc-parser && python -m uvicorn kparser.app:app --host 0.0.0.0 --port 9001)
```

```bash
# 2. backend: http://localhost:10001
(cd backend && mvn spring-boot:run)
```

```bash
# 3. frontend: http://localhost:20001
(cd frontend && pnpm install && pnpm dev)
```

## 关键 API

- `/api/knowledge`：知识库、文档、上传解析和处理进度。
- `/api/retrieval`：检索、adapter 状态、上下文证据。
- `/api/chat`：会话、RAG 问答、答案引用。
- `/api/test/rag-demo/*`：教学 seed、evaluate 和 evidence report。

## 验证入口

```bash
# 工程底座：脚手架契约、代码边界、生成物一致性、后端运行态探针
./scripts/quality-gate.sh

# CI 会在 main/master push 和 PR 上执行同一入口
.github/workflows/quality-gate.yml

# RAG demo：seed -> evaluate -> retrieval -> chat -> references
./scripts/probe-rag-demo-runtime.sh

# RAG ingestion：doc-parser + upload -> parse -> chunk -> embedding -> retrieval
./scripts/probe-rag-ingestion-runtime.sh
```

常用拆分命令：

```bash
./scripts/check-template.sh
./scripts/check-contracts.sh
./scripts/smoke-rag-demo.sh
./scripts/seed-rag-demo.sh
(cd frontend && pnpm build)
```

## 继续阅读

- 设计与进度索引：[project_document/README.md](./project_document/README.md)
- 当前状态：[project_document/STATUS.md](./project_document/STATUS.md)
- 最终验收口径：[project_document/FINAL_READINESS_AUDIT.md](./project_document/FINAL_READINESS_AUDIT.md)
- V1 封版说明：[project_document/V1_RELEASE_NOTES.md](./project_document/V1_RELEASE_NOTES.md)
- 从脚手架到 RAG Agent：[project_document/SCAFFOLD_TO_RAG_AGENT_GUIDE.md](./project_document/SCAFFOLD_TO_RAG_AGENT_GUIDE.md)
- doc-parser 契约：[contracts/doc-parser-contract.json](./contracts/doc-parser-contract.json)
- 技术栈机器契约：[contracts/scaffold-stack-contract.json](./contracts/scaffold-stack-contract.json)

## License

MIT
