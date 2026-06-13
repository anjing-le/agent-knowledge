# Status

更新时间：2026-06-14

## 当前阶段

agent-knowledge 正在从旧项目结构迁移到工程脚手架契约，同时保留 RAG 业务主链路和 Python doc-parser 独立服务。

2026-06-13：V1.5 RAG 工作区 checkpoint 已通过契约、后端关键链路和前端构建总验证。

2026-06-13：V2 生产化 ingestion 已开始契约设计，先明确 doc-parser 异步任务接口和 Java 状态映射。

2026-06-14：模型服务调用开始按脚手架 `RemoteHttpClient` 统一收敛。

2026-06-14：Chunk API 边界开始从 JPA Entity 收敛到 Response VO。

2026-06-14：文档 ingestion 入口开始收敛到 `DocumentIngestionService` 应用服务。

2026-06-14：文档切片生成开始收敛到 `DocumentChunkingService` 阶段服务。

2026-06-14：文档文件 ID 生成收敛到 `DateUtils + counter`，移除业务服务里的随机毫秒拼接。

2026-06-14：文档 Embedding 阶段开始收敛到 `DocumentEmbeddingService` 阶段服务。

2026-06-14：文档解析阶段开始收敛到 `DocumentParsingService` 阶段服务。

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
- 前端已移除旧系统菜单 API、旧用户 API 和双因子手机验证组件，运行 API 继续通过 `ApiPaths` 和 service-boundary 管理。
- 知识库详情页已接入文档处理任务抽屉，并在文档处于 PENDING/解析/切片/向量化/RAPTOR 状态时自动轮询刷新。
- 文档处理任务服务已补充生命周期单测，覆盖待处理、运行、成功、失败和前端时间线响应映射。
- 后端健康检查已暴露 doc-parser 下游状态，保持 Java 后端自身可用状态与 Python 解析服务依赖状态分离。
- 前端通知面板已接入后端健康检查，打开通知时展示 doc-parser 就绪/未就绪状态。
- 知识库详情页和上传弹窗已展示 doc-parser 健康状态，上传前可见 Python 解析服务是否就绪。
- 文档处理任务抽屉已增加 RAG Pipeline 阶段视图，展示上传、解析、切片、Embedding、向量写入和完成状态。
- 聊天答案引用已增强证据展示，包含知识库、Chunk、相似度、metadata 标签和查看切片入口。
- 后端已补充引用证据契约测试，覆盖 MessageResponse 引用解析和 RetrievalService metadata 回传。
- Java 到 Python doc-parser 的 HTTP 客户端已补充契约测试，覆盖健康检查、同步解析、URL 解析、失败响应和 chunks/metadata 映射。
- 文档处理主链路已补充服务层测试，覆盖 doc-parser 解析、切片、Embedding、向量写入、完成状态和向量化失败分支。
- V2 doc-parser 异步任务契约已补充请求/响应草案和 Java document/task 状态映射。
- Java `DocParserClient` 已补充 V2 异步提交和状态查询方法，异步 URL/状态接口走脚手架 `RemoteHttpClient` 的 `agent-doc-parser` 服务边界，主文档处理链路仍保持 V1 同步解析不变。
- Java 后端已新增 doc-parser 异步状态映射层，将 `PENDING/RUNNING/SUCCEEDED/FAILED/CANCELED` 统一转换为文档状态、任务状态、任务阶段和进度。
- 新增脚手架技术栈对齐检查 `scripts/check-scaffold-alignment.js`，守住 Vue/Vite/TypeScript、Spring Boot/Java、三服务边界、契约和质量脚本入口。
- 前端富文本上传地址已改为 `resolveApiPath(ApiPaths.common.uploadWangEditor)`，运行时代码硬编码 `/api/**` 已纳入 `scripts/check-frontend-api-boundaries.js`。
- 后端 Controller 契约检查已改为递归覆盖所有业务 Controller，并新增后端时间契约检查，防止业务代码绕过 `DateUtils` 直接取当前时间。
- EmbeddingService 和 LLMService 已从直接 `RestTemplate` 调用迁移到脚手架 `RemoteHttpClient` absolute URL 模式，并以 `embedding-provider` / `llm-provider` 作为调用观测目标。
- ChunkController 已下沉仓储访问到 ChunkService，后端 Controller 契约检查已禁止非示例 Controller 直接依赖 repository 包。
- ChunkController 已改为返回 `ChunkResponse`，不再在 API 边界暴露 JPA `Chunk` 实体；后端 Controller 契约检查已禁止非示例 Controller import entity 包。
- DocumentController 已改为通过 `DocumentIngestionService` 承接上传、批量上传、重新处理和任务查询；`DocumentService` 回到文档存储、分页、删除和状态变更职责，并修正新文件上传时引用计数不应重复递增的问题。
- DocumentProcessingService 已将 Chunk 生成、metadata 序列化、token 估算和 fallback 固定长度切片下沉到 `DocumentChunkingService`，主处理服务继续负责解析、切片、Embedding、向量写入的阶段编排。
- DocumentService 的文件 ID 已从 `System.currentTimeMillis + Math.random` 改为 `DateUtils + FILE_COUNTER`，并纳入脚手架对齐检查。
- DocumentProcessingService 已将 Embedding 批处理、向量库写入和 Chunk 向量化状态更新下沉到 `DocumentEmbeddingService`，provider 异常和向量数量不匹配都会把当前批次标为 `FAILED`。
- DocumentProcessingService 已将文件路径查询、doc-parser 健康检查、doc type 映射和 Python 解析调用下沉到 `DocumentParsingService`，主处理服务不再直接依赖 `DocParserClient`。

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
node scripts/check-service-boundaries.js
node scripts/check-backend-controller-contracts.js
mvn -q -Dtest=MemoryVectorStoreServiceTest test
mvn -q -Dtest=RetrievalServiceTest test
node scripts/check-backend-context-contract.js
node scripts/check-async-context-contract.js
mvn -q -Dtest=RequestContextTaskDecoratorTest test
mvn -q -Dtest=ConversationResponseTest test
mvn -q -Dtest=DocumentProcessingTaskServiceTest test
mvn -q -Dtest=TestControllerTest test
mvn -q -Dtest=MessageResponseTest,RetrievalServiceTest test
mvn -q -Dtest=DocParserClientTest test
mvn -q -Dtest=DocumentProcessingServiceTest test
mvn -q -Dtest=DocParserStatusMapperTest,DocParserClientTest test
node scripts/check-scaffold-alignment.js
node scripts/check-scaffold-governance.js
node scripts/check-frontend-api-boundaries.js
node scripts/check-backend-controller-contracts.js
node scripts/check-backend-time-contract.js
node scripts/check-remote-http-contract.js
mvn -q -Dtest=EmbeddingServiceTest,LLMServiceTest,DocumentProcessingServiceTest test
mvn -q -Dtest=ChunkServiceTest test
node scripts/check-backend-controller-contracts.js
mvn -q -Dtest=DocumentIngestionServiceTest,DocumentServiceTest test
mvn -q -Dtest=DocumentChunkingServiceTest,DocumentProcessingServiceTest test
mvn -q -Dtest=DocumentServiceTest test
mvn -q -Dtest=DocumentEmbeddingServiceTest,DocumentProcessingServiceTest test
mvn -q -Dtest=DocumentParsingServiceTest,DocumentProcessingServiceTest test
(cd backend && mvn -q -Dtest=DocParserClientTest,DocumentProcessingServiceTest,MessageResponseTest,RetrievalServiceTest,DocumentProcessingTaskServiceTest,MemoryVectorStoreServiceTest,ConversationResponseTest,TestControllerTest,RequestContextTaskDecoratorTest test)
(cd frontend && pnpm build)
node scripts/check-service-boundaries.js
```

## 当前风险

- doc-parser V1 同步解析适合演示，长文档和 OCR 应在 V2 接入异步任务接口。
- Java 后端服务层和实体生命周期回调已收敛到 `DateUtils`，避免业务代码散落 `LocalDateTime.now()`。
- 前端仍保留少量脚手架共享组件，运行入口和 API 已聚焦 RAG 工作区。
- 生产数据库、对象存储、向量库目前以本地/教学配置为主，V2 需要明确生产部署方案。
