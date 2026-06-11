# Scaffold Adoption Notes

agent-knowledge 已基于工程脚手架迁入工程契约。本文记录后续如果继续从脚手架同步能力，应如何处理。

## 可继续继承

- `contracts/platform-contract.json` 的响应、分页、请求头、时间和语言契约。
- `contracts/service-boundaries.json` 的生成链路。
- 后端 `RequestContextFilter`、`APIResponse<T>`、`PageResult<T>`、OpenAPI 配置、远程调用基础能力。
- 前端 `ApiPaths`、HTTP 响应解析、请求上下文头、时间和语言工具。
- 质量脚本的 check/generate 模式。

## 不应直接覆盖

- `backend/src/main/java/com/anjing/knowledge/**`
- `backend/src/main/java/com/anjing/chat/**`
- `doc-parser/**`
- `frontend/src/views/knowledge/**`
- `frontend/src/views/chat/**`
- `docs/teaching/**`

这些目录承载 agent-knowledge 的 RAG 业务和教学资产，同步脚手架时只能按差异迁移，不能整目录覆盖。

## 每次同步后验证

```bash
(cd backend && mvn -q -DskipTests compile)
(cd frontend && pnpm build)
node scripts/generate-platform-contract-backend.js --check
node scripts/generate-platform-contract-frontend.js --check
node scripts/generate-service-boundaries-backend.js --check
node scripts/generate-service-boundaries-frontend.js --check
```
