# Project Constraints

## 总原则

agent-knowledge 是 RAG 智能知识库和高级 agent 示例。工程结构继承脚手架契约，但领域能力必须围绕 RAG 主链路，不把无关平台能力塞进业务代码。

## 必须遵守

- doc-parser 保持 Python FastAPI 独立服务。
- Java 后端不引入 Python 解析库，不复制 doc-parser 内部实现。
- API 路径先更新 `contracts/service-boundaries.json`，再生成常量。
- 新业务接口返回 `APIResponse<T>`。
- 分页接口返回 `PageResult<T>`。
- 前端业务 API 使用 `ApiPaths`。
- 前端页面不直接手写 `/api/**`。
- 本地密钥只放 ignored 文件或环境变量，不提交真实 API Key。
- 文档中的端口、命令和路径必须可验证。

## RAG 代码边界

- `knowledge` 包：知识库、文档、Chunk、Embedding、检索前置数据。
- `retrieval` API：检索入参、候选结果、score 和来源。
- `chat` 包：会话、消息、LLM 调用、引用回传。
- `doc-parser`：解析和 OCR，不管理知识库业务状态。

## 质量基线

每轮重构至少运行：

```bash
(cd backend && mvn -q -DskipTests compile)
(cd frontend && pnpm build)
node scripts/generate-platform-contract-backend.js --check
node scripts/generate-platform-contract-frontend.js --check
node scripts/generate-service-boundaries-backend.js --check
node scripts/generate-service-boundaries-frontend.js --check
node scripts/check-backend-controller-contracts.js
node scripts/check-backend-time-contract.js
```

## 不做

- 不把 Python doc-parser 合并进 Java 后端。
- 不在 V1 强行引入消息队列、分布式向量库或网关依赖。
- 不让模板 mock 路径污染 RAG 运行 API。
- 不把教学材料中的演示口径当成生产架构承诺。
