# Agent Knowledge

基于 `infra-dev-scaffolding` 生长出来的 RAG 智能知识库示例。

主链路：

```text
文档上传 -> Python 解析 -> 切片 -> Embedding -> 向量检索 -> 上下文组装 -> LLM 回答 -> 答案引用
```

学习重点是 RAG agent 的设计。统一响应、分页、路径契约、请求上下文、OpenAPI、质量脚本和前端 API 习惯都继承自脚手架。

## 脚手架约束

agent-knowledge 不重新设计工程底座，默认遵守 `infra-dev-scaffolding` 的技术栈和治理入口：

- [项目约束](./project_document/PROJECT_CONSTRAINTS.md)
- [新增模块指南](./project_document/NEW_MODULE_GUIDE.md)
- [接入提示词](./project_document/SCAFFOLD_ADOPTION_PROMPT.md)
- [UI 设计约束](./project_document/UI_DESIGN_GUIDE.md)
- [演示证据](./project_document/DEMO_EVIDENCE.md)
- `./scripts/quality-gate.sh`

## 技术栈

- Frontend: Vue 3.5 + TypeScript + Vite 7
- Backend: Spring Boot 3.4.5 + Java 17
- Doc parser: Python FastAPI 独立服务
- Dev provider: 本地 `local-demo` Embedding/LLM，真实模型通过环境变量切换
- 机器契约：[contracts/scaffold-stack-contract.json](./contracts/scaffold-stack-contract.json)

## 结构

```text
backend/          Spring Boot 后端：知识库、文档、检索、聊天
frontend/         Vue 3 前端：知识库管理和问答界面
doc-parser/       Python FastAPI 文档解析服务
contracts/        平台契约、服务边界和 doc-parser 契约
project_document/ 设计、边界、路线图和验证记录
```

`doc-parser` 是独立 Python 服务，Java 后端只通过 HTTP 调用它，不把解析能力并入 Java。

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

## 验证

```bash
./scripts/check-template.sh
./scripts/check-contracts.sh
./scripts/smoke-rag-demo.sh
(cd backend && mvn -q -DskipTests compile)
(cd frontend && pnpm build)
```

## 文档

- [设计与进度索引](./project_document/README.md)
- [当前状态](./project_document/STATUS.md)
- [从脚手架到 RAG Agent](./project_document/SCAFFOLD_TO_RAG_AGENT_GUIDE.md)
- [本地启动指南](./project_document/LOCAL_STARTUP_GUIDE.md)
- [doc-parser 契约](./contracts/doc-parser-contract.json)

质量门禁：

```bash
./scripts/quality-gate.sh
```

## License

MIT
