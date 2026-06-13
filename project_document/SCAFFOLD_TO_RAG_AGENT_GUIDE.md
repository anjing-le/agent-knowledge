# From Scaffold To RAG Agent

## 核心定位

agent-knowledge 不是重新发明一套工程体系，而是从 `infra-dev-scaffolding` 生长出来的高级 agent 示例。

教学时要让学习者形成一个清晰认知：

- 底层技术栈、工程习惯和最佳实践来自脚手架。
- 当前项目只新增 RAG agent 的业务设计。
- 以后做其他 agent 时，也应该复用同一套脚手架能力，只替换业务模块边界和产品体验。

## 脚手架继承的部分

这些内容不要在 agent-knowledge 里重新设计：

- 统一响应：`APIResponse<T>`。
- 标准分页：`PageResult<T>`。
- API 路径：`ApiConstants`、`ApiPaths`、`contracts/service-boundaries.json`。
- 平台契约：`contracts/platform-contract.json`、请求头、时区、语言。
- 请求上下文：`RequestContextFilter`、`GlobalRequestContextHolder`、MDC 日志。
- 远程调用：`RemoteHttpClient`、调用方身份、服务地址解析、Header 透传。
- OpenAPI：后端注解、前端 contract 生成、校验脚本。
- 质量门禁：`scripts/check-*.js`、`check-template.sh`、`check-contracts.sh`。
- 前端基础习惯：统一 API 模块、HTTP response unwrap、路径注册、Element Plus 组件风格。

这些是“地基”。学习者不需要每个 agent 项目都重新学一遍地基，只需要知道如何沿用。

## agent-knowledge 新增的设计

agent-knowledge 只负责表达 RAG agent 的差异：

- 知识库：知识库配置、Embedding 模型、chunk 策略、启用状态。
- 文档 ingestion：`DocumentIngestionService` 作为 `@Facade` 应用服务，负责上传、批量上传、重新处理、任务查询和处理触发；`DocumentService` 只保留文档存储、分页、删除和状态变更。
- 处理上下文：`DocumentProcessingContextService` 负责加载 Document 和 KnowledgeBase，避免主编排服务直接依赖仓储。
- 处理进度：`DocumentProcessingProgressService` 负责维护任务阶段和文档状态映射，避免主编排服务散落状态更新。
- Python doc-parser：独立 FastAPI 服务，`DocumentParsingService` 负责查文件路径、检查健康状态、映射 doc type 并通过 HTTP 调用解析。
- Chunk：`DocumentChunkingService` 负责把 doc-parser 结果或原始文本转换为 Chunk，沉淀内容、token、metadata、页码、content_type、启用状态。
- Chunk 持久化：`DocumentChunkPersistenceService` 负责保存 Chunk，并回写文档 chunk/token 统计。
- Embedding 阶段：`DocumentEmbeddingService` 负责调用模型服务、写入 `VectorStoreService`、更新 Chunk 向量化状态。
- 向量检索：`VectorStoreService` 边界、memory 实现、未来 Milvus/pgvector adapter；`RetrievalResultEnrichmentService` 负责把向量命中补全成可引用的 SearchResult。
- 上下文组装：`RagPromptBuilderService` 按知识库检索结果组装 prompt context，`LLMService` 只负责模型远程调用。
- 问答编排：`RagChatOrchestrationService` 负责知识检索、历史消息组装和 LLM 回答生成，`ChatService` 负责会话和消息持久化。
- 消息持久化：`ChatMessagePersistenceService` 负责消息 sequence、消息 ID、引用 JSON 和消息响应映射。
- 答案引用：从 SearchResult 到 Message.references，再到前端引用展示。
- RAG 工作区：知识库列表、文档任务、切片 metadata、知识问答。

## 模块生长方式

推荐教学顺序：

1. 从脚手架看平台契约和 service-boundary。
2. 在 `contracts/service-boundaries.json` 里声明 RAG API。
3. 生成后端 `ServiceBoundaryConstants` 和前端 `SERVICE_BOUNDARY_ROUTE_PATHS`。
4. 按边界实现 Controller，不在 Controller 里堆业务逻辑。
5. 在应用服务层承接用户动作，例如 `DocumentIngestionService` 负责 ingestion 入口。
6. 在领域服务层拆出阶段服务，例如 `DocumentProcessingService` 负责编排，`DocumentProcessingContextService` 负责加载处理上下文，`DocumentProcessingProgressService` 负责阶段状态推进，`DocumentParsingService` 负责解析调用，`DocumentChunkingService` 负责切片生成，`DocumentChunkPersistenceService` 负责切片落库和统计，`DocumentEmbeddingService` 负责向量化和向量写入，`RetrievalResultEnrichmentService` 负责检索引用补全，`RagPromptBuilderService` 负责 RAG prompt 组装，`RagChatOrchestrationService` 负责问答链路编排，`ChatMessagePersistenceService` 负责消息落库和引用落库。
7. 将 Python doc-parser 保持为外部服务，通过 HTTP 契约调用。
8. 把向量库、Embedding、LLM 都设计为可替换 adapter。
9. 前端只通过 `ApiPaths` 和 API service 调用后端。
10. 每次新增能力后运行脚手架质量门禁。

## 当前项目演示主线

一条完整演示应该按这个顺序展开：

1. 打开 RAG 工作区，看知识库规模。
2. 创建知识库，配置 chunk size、overlap、Embedding 模型。
3. 上传文档，文档进入处理任务。
4. 查看文档任务：PENDING -> PARSING -> CHUNKING -> EMBEDDING -> COMPLETED。
5. 查看切片页，观察页码、content_type、字符范围等 metadata。
6. 进入知识问答，选择知识库提问。
7. 查看回答引用，说明引用来自检索结果和 chunk metadata。
8. 回到代码，说明这些业务能力如何复用脚手架的响应、路径、上下文和校验。

## 不应该做的事

- 不要把 doc-parser 塞进 Java 后端。
- 不要在前端手写散落 URL。
- 不要绕过 `APIResponse<T>` 和 `PageResult<T>`。
- 不要让 Controller 直接承载 RAG 编排。
- 不要把 memory vector store 写死成唯一方案。
- 不要为了单个 agent 项目修改脚手架级习惯。

## 最终教学目标

学习者最后应该明白：

写一个 agent 项目时，真正需要思考的是业务设计、模块边界和用户体验。

认证、响应、分页、路径、上下文、日志、OpenAPI、脚本门禁这些工程底座，应该由脚手架统一提供。agent-knowledge 的价值，就是展示一个复杂 RAG agent 如何在这套底座上自然生长出来。
