# Status

更新时间：2026-06-12

## 当前阶段

agent-knowledge 正在从旧项目结构迁移到工程脚手架契约，同时保留 RAG 业务主链路和 Python doc-parser 独立服务。

## 已完成

- 新增 `contracts/platform-contract.json`、`contracts/service-boundaries.json`、`contracts/doc-parser-contract.json`。
- 后端迁入统一响应 `APIResponse<T>`、分页 `PageResult<T>`、请求上下文、OpenAPI 配置、远程 HTTP 基础能力。
- 后端 API 路径收敛到：
  - `/api/auth`
  - `/api/test`
  - `/api/knowledge`
  - `/api/retrieval`
  - `/api/chat`
- RAG Controller 已使用 `ApiConstants`、`APIResponse<T>`、`PageResult<T>` 和 OpenAPI `@Tag/@Operation`。
- 前端迁入 `ApiPaths`、平台契约、service-boundary 生成物、请求上下文头、统一响应解析和时间/语言工具。
- 前端知识库和聊天 API 已从旧硬编码路径迁到 `ApiPaths`。
- 登录 API 已对齐后端 `username` / `accessToken` 契约，并保留页面侧 `token` 兼容字段。
- doc-parser 保持 Python FastAPI 独立服务，Java 通过 `DocParserClient` HTTP 调用 `/parse`、`/parse_url`、`/health`。
- V1.5 已开始收敛为 RAG 工作区：前端主菜单只保留知识库和知识问答。
- 新增文档处理任务模型 `document_processing_task`，为上传、重试、解析、切片、Embedding 和失败恢复保留可追踪记录。
- 向量存储已抽象为 `VectorStoreService` 接口，当前默认实现为 `MemoryVectorStoreService`，后续可替换 Milvus 或 pgvector。
- 向量库 provider 已新增 `app.vector-store` 配置入口，默认 `VECTOR_STORE_PROVIDER=memory`，并补齐 adapter 边界文档。
- 检索结果和聊天引用已透出 chunk metadata，可用于页码、content_type 等引用增强。
- 根 README 已精简为项目入口，详细设计收敛到 `project_document`。
- 前端顶部快速入口已从模板功能收敛为知识库、知识问答、架构说明和启动指南。
- 前端动态组件加载范围已收敛到 RAG 工作区，避免未暴露模板页进入懒加载运行面。
- 登录页已移除注册/忘记密码模板入口，对外运行面聚焦 RAG 工作区登录和游客体验。
- 前端已删除未引用的 dashboard/system/result/theme/register/forget-password 模板页面和路由模块。

## 验证证据

已通过：

```bash
(cd backend && mvn -q -DskipTests compile)
(cd frontend && pnpm build)
./scripts/check-template.sh
./scripts/check-contracts.sh
node scripts/check-frontend-api-boundaries.js
node scripts/generate-service-boundaries-backend.js --check
node scripts/generate-service-boundaries-frontend.js --check
node scripts/check-api-path-parity.js
node scripts/check-backend-controller-contracts.js
```

待继续验证：

```bash
node scripts/check-backend-context-contract.js
node scripts/check-async-context-contract.js
mvn -q -Dtest=RequestContextTaskDecoratorTest test
```

## 当前风险

- doc-parser V1 同步解析适合演示，长文档和 OCR 应在 V2 接入异步任务接口。
- Java 后端服务层和实体生命周期回调已收敛到 `DateUtils`，避免业务代码散落 `LocalDateTime.now()`。
- 前端仍保留少量脚手架共享组件和旧用户 API 兼容代码，运行入口已聚焦 RAG 工作区。
- 生产数据库、对象存储、向量库目前以本地/教学配置为主，V2 需要明确生产部署方案。
