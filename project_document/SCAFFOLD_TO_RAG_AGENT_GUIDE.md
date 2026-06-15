# From Scaffold To RAG Agent

## 核心定位

agent-knowledge 不是重新发明一套工程体系，而是从 `infra-dev-scaffolding` 生长出来的高级 agent 示例。

教学时要让学习者形成一个清晰认知：

- 底层技术栈、工程习惯和最佳实践来自脚手架。
- 当前项目只新增 RAG agent 的业务设计。
- 以后做其他 agent 时，也应该复用同一套脚手架能力，只替换业务模块边界和产品体验。
- 技术栈以 [../contracts/scaffold-stack-contract.json](../contracts/scaffold-stack-contract.json) 为准：Spring Boot/Java 是平台后端，Python FastAPI 只承载 doc-parser。
- 检索生产化边界以 [../contracts/retrieval-adapter-contract.json](../contracts/retrieval-adapter-contract.json) 为准：Vector Store、Keyword Search、Rerank Provider 作为三条可替换 adapter 轴线。
- 本地同时存在 `../infra-dev-scaffolding` 时，`./scripts/check-scaffold-source.sh` 会把该契约反向校验到脚手架 README、前端 `package.json` 和后端 `pom.xml`。

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
- 前端基础习惯：统一 API 模块、HTTP response unwrap、路径注册、OpenAPI operation 调用、Element Plus 组件风格。

这些是“地基”。学习者不需要每个 agent 项目都重新学一遍地基，只需要知道如何沿用。

## agent-knowledge 新增的设计

agent-knowledge 只负责表达 RAG agent 的差异：

- 知识库：知识库配置、Embedding 模型、chunk 策略、启用状态。
- 文档 ingestion：`DocumentIngestionService` 作为 `@Facade` 应用服务，负责上传、批量上传、重新处理、任务查询和处理触发；`DocumentService` 只保留文档存储、分页、删除和状态变更。
- 处理上下文：`DocumentProcessingContextService` 负责加载 Document 和 KnowledgeBase，避免主编排服务直接依赖仓储。
- 处理进度：`DocumentProcessingProgressService` 负责维护任务阶段和文档状态映射，避免主编排服务散落状态更新。
- Python doc-parser：独立 FastAPI 服务，`DocumentParsingService` 负责选择同步/异步解析模式，`DocumentAsyncParsingService` 负责 V2 submit/poll，`DocumentParseResultMapper` 负责把传输 DTO 转成业务 DTO，所有解析能力仍通过 HTTP contract 调用。
- Chunk：`DocumentChunkingService` 负责把 doc-parser 结果或原始文本转换为 Chunk，沉淀内容、token、metadata、页码、content_type、启用状态。
- Chunk 持久化：`DocumentChunkPersistenceService` 负责保存 Chunk，并回写文档 chunk/token 统计。
- Embedding 阶段：`DocumentEmbeddingService` 负责调用模型服务、写入 `VectorStoreService`、更新 Chunk 向量化状态。
- 向量检索：`VectorStoreService` 边界、`VectorStoreProperties` 承接 provider 配置，默认 `MemoryVectorStoreService`，生产化骨架已有 `PgVectorStoreService`，未来可继续补 Milvus adapter；`KeywordSearchProvider` 负责关键词召回，`KeywordSearchProperties` 承接 provider 配置，默认 `LocalKeywordSearchProvider`，轻量 ranking 骨架已有 `Bm25KeywordSearchProvider`，生产化骨架已有 `ElasticsearchKeywordSearchProvider`；`RetrievalResultEnrichmentService` 负责把命中补全成可引用的 SearchResult；`RetrievalHybridSearchService` 负责向量/关键词候选 RRF 合并；`RetrievalRerankService` 负责 rerank 编排；`RerankProperties` 承接 provider 配置；`RerankProviderClient` 负责远程 rerank provider 接入点。
- 上下文组装：`RagPromptBuilderService` 按知识库检索结果组装 prompt context，并生成 `RagContextTrace` 记录 assemblyStrategy、prompt sections、history window、prompt/context 字符数和纳入 prompt 的 chunks；`LLMService` 只负责模型远程调用和 trace 透传。
- 问答编排：`RagChatOrchestrationService` 负责知识检索、历史消息组装和 LLM 回答生成，`ChatService` 负责会话和消息持久化。
- 会话生命周期：`ChatConversationLifecycleService` 负责会话创建、查询、删除、标题更新、消息数更新和会话 ID 生成。
- 会话配置：`ChatConversationConfigService` 负责会话 kbIds/config JSON 字段和发送消息时的知识库选择规则。
- 消息持久化：`ChatMessagePersistenceService` 负责消息 sequence、消息 ID、引用 JSON、contextTrace metadata 和消息响应映射。
- 答案引用：从 SearchResult 到 Message.references，再到前端引用展示；引用卡保留 rank、retrievalSource、hybrid/rerank 分数和 scoreExplanation，回答卡保留 contextTrace，方便解释检索结果如何进入 prompt、回答为什么可信。
- RAG 工作区：知识库列表、文档任务、切片 metadata、检索调试、知识问答；Pipeline 教学视图用 `Adapter Matrix` 展示默认 provider、轻量过渡 provider、生产 provider 和对应切换命令。

## 模块生长方式

推荐教学顺序：

1. 从脚手架看平台契约和 service-boundary。
2. 在 `contracts/service-boundaries.json` 里声明 RAG API。
3. 生成后端 `ServiceBoundaryConstants` 和前端 `SERVICE_BOUNDARY_ROUTE_PATHS`。
4. 按边界实现 Controller，不在 Controller 里堆业务逻辑。
5. 在应用服务层承接用户动作，例如 `DocumentIngestionService` 负责 ingestion 入口。
6. 在领域服务层拆出阶段服务，例如 `DocumentProcessingService` 负责编排和 `continueAfterParsing` 续跑，`DocumentProcessingContextService` 负责加载处理上下文，`DocumentProcessingProgressService` 负责阶段状态推进，`DocumentParsingService` 负责解析模式选择，`DocumentAsyncParsingService` 负责异步 submit/poll，`DocumentParseResultMapper` 负责解析 DTO 转换，`DocumentChunkingService` 负责切片生成，`DocumentChunkPersistenceService` 负责切片落库和统计，`DocumentEmbeddingService` 负责向量化和向量写入，`PgVectorStoreService` 负责 pgvector adapter，`KeywordSearchProvider` 负责关键词召回，`Bm25KeywordSearchProvider` 负责本地 BM25 ranking，`ElasticsearchKeywordSearchProvider` 负责远程搜索引擎 adapter，`RetrievalResultEnrichmentService` 负责检索引用补全，`RetrievalHybridSearchService` 负责向量/关键词候选合并，`RetrievalRerankService` 负责召回候选重排，`RerankProviderClient` 负责远程 rerank provider，`RagPromptBuilderService` 负责 RAG prompt 组装，`RagChatOrchestrationService` 负责问答链路编排，`ChatConversationLifecycleService` 负责会话生命周期，`ChatConversationConfigService` 负责会话配置解析，`ChatMessagePersistenceService` 负责消息落库和引用落库。
7. 将 Python doc-parser 保持为外部服务，通过 HTTP 契约调用。
8. 把向量库、关键词召回、rerank、Embedding、LLM 都设计为可替换 adapter。
9. 前端优先通过 `openApiRequest(operationId)` 和生成类型调用后端；暂未进入 OpenAPI 或需要浏览器 `File + FormData` 的接口才使用 `ApiPaths` fallback。
10. 每次新增能力后运行脚手架质量门禁。

## 当前项目演示主线

一条完整演示应该按这个顺序展开：

1. 启动后端后执行 `./scripts/seed-rag-demo.sh`，或在 RAG Pipeline 教学视图中使用 Demo Ready 操作区，先灌入一套运行态教学数据。
2. 打开 RAG Pipeline 教学视图，说明脚手架地基、Adapter Matrix、RAG 阶段服务、Java/Python 边界，以及 Demo Ready 如何用 `Seed -> Evaluate -> Retrieval -> Chat -> Evidence` 串起运行态证据。
3. 进入 RAG 工作区，看知识库规模。
4. 创建知识库，配置 chunk size、overlap、Embedding 模型。
5. 上传文档，文档进入处理任务。
6. 查看文档任务：PENDING -> PARSING -> CHUNKING -> EMBEDDING -> COMPLETED。
7. 查看切片页，观察页码、content_type、字符范围等 metadata。
8. 在 Demo Ready 的 Retrieval Evaluation 面板运行检索评估，观察 recall@K、通过用例数、rank、top chunk 和 scoreExplanation。
9. 从 Demo Ready 进入检索调试，页面自动带入 query/kbIds/hybrid 并执行一次检索，观察命中的 chunk、score、retrievalSource 和 metadata。
10. 从 Demo Ready 或检索调试进入知识问答，Demo 路由会自动创建会话并发送 seed 问题，生成带引用的回答。
11. 查看上下文组装 trace，说明检索结果如何被 `RagPromptBuilderService` 变成 system prompt 的 sections、history window 和 included chunks。
12. 查看回答引用，说明引用来自检索结果和 chunk metadata，并顺着 rank、retrievalSource、scoreExplanation 讲清楚 `query -> retrieval result -> context assembly -> answer reference -> chunk/source` 证据链。
13. 执行 `./scripts/probe-doc-parser-boundary.sh --contract-only`，说明 Python doc-parser 是独立 FastAPI 服务，Java 只通过 HTTP contract 调用它。
14. 执行 `./scripts/check-doc-parser-lifecycle.sh`，说明 Python 异步解析状态如何映射为 Java 文档任务生命周期。
15. doc-parser 启动后执行 `./scripts/smoke-doc-parser-async.sh`，说明 async submit/status 能返回真实 RAG-shaped chunks。
16. 执行 `./scripts/check-scaffold-source.sh`，说明 Spring Boot/Java、Vue/Vite/TypeScript 来自脚手架真实源码声明。
17. 执行 `./scripts/evaluate-rag-retrieval.sh`，说明检索评测如何用固定 query/expected chunk 形成 recall@K、rank 和 scoreExplanation 证据。
18. 执行 `./scripts/probe-retrieval-adapters.sh --dry-run`，再回到 Pipeline 的 `Adapter Matrix`，说明检索 adapter 可以从 `memory/local/local-demo` 切到 `pgvector/bm25/elasticsearch/remote rerank`，doc-parser 可以从 `sync` 切到 `async/recovery`，但默认教学路径仍保持无外部依赖。
19. 执行 `./scripts/create-demo-evidence.sh --dry-run` 和 `./scripts/collect-demo-evidence.sh --dry-run`，说明证据包会落到 `docs/evidence/YYYY-MM-DD/`，并按 `docs/evidence/TEMPLATE.md` 记录命令输出、运行态 JSON 和截图。
20. 回到代码，说明这些业务能力如何复用脚手架的响应、路径、上下文和校验。

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
