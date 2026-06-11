# Service Boundary Guide

## 机器可读来源

Java 后端和前端运行路径以 [../contracts/service-boundaries.json](../contracts/service-boundaries.json) 为事实来源。

生成命令：

```bash
node scripts/generate-service-boundaries-backend.js
node scripts/generate-service-boundaries-frontend.js
```

校验命令：

```bash
node scripts/generate-service-boundaries-backend.js --check
node scripts/generate-service-boundaries-frontend.js --check
node scripts/check-service-boundaries.js
```

## 当前边界

| Boundary | Base Path | Owner | 说明 |
|----------|-----------|-------|------|
| `auth` | `/api/auth` | `infra-auth` future / current mock | 当前为本地 mock 登录，后续可替换认证中心 |
| `test` | `/api/test` | `agent-knowledge` | 本地 smoke、自检和 OpenAPI 验证 |
| `common` | `/api/common` | `infra-common` future | 文件/公共能力预留 |
| `knowledge` | `/api/knowledge` | `agent-knowledge` | 知识库、文档、Chunk 管理 |
| `retrieval` | `/api/retrieval` | `agent-knowledge` | RAG 检索和快速调试接口 |
| `chat` | `/api/chat` | `agent-knowledge` | 会话、消息、RAG 回答和引用 |

## RAG 模块边界

`knowledge`：

- 知识库 CRUD。
- 文档上传、删除、重处理、启停。
- 文档下 Chunk 查询、Chunk 详情和启停。
- 不直接生成最终回答。

`retrieval`：

- 接收 query、kbIds、topK、similarityThreshold。
- 返回候选 Chunk、score、文档来源和可组装上下文。
- 被 `chat` 调用，也允许作为调试 API 暴露。

`chat`：

- 管理 conversation 和 message。
- 根据会话关联知识库触发 retrieval。
- 组装 prompt/context，调用 LLM。
- 保存回答和 references。

`doc-parser`：

- 不放在 Java service-boundary 中。
- 作为 Python FastAPI 下游服务记录在 [../contracts/doc-parser-contract.json](../contracts/doc-parser-contract.json)。

## 后端约束

- Controller 必须使用 `ApiConstants`，不在注解里手写完整 URL。
- 业务接口返回 `APIResponse<T>`。
- 分页接口返回 `APIResponse<PageResult<T>>`。
- 请求体使用 DTO，不使用裸 `Map` 表达业务入参。
- 业务 Controller 必须带 OpenAPI `@Tag`，关键接口带 `@Operation`。

## 前端约束

- API 模块使用 `ApiPaths`，不在页面组件中手写 `/api/**`。
- `request.get<T>` 的泛型是后端 envelope 中的 `data` 类型，不再包 `BaseResult<T>`。
- 分页数据使用 `PaginatedResponse<T>`，字段为 `records/current/size/total`。
