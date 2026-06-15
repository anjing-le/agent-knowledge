# Retrieval Adapter Guide

本文档定义 agent-knowledge 的检索生产化 adapter 边界。对应机器契约是 [../contracts/retrieval-adapter-contract.json](../contracts/retrieval-adapter-contract.json)。

从默认教学栈切到生产化检索栈的 env 和验证步骤见 [RETRIEVAL_ADAPTER_SWITCH_GUIDE.md](./RETRIEVAL_ADAPTER_SWITCH_GUIDE.md)，无外部依赖探针是 `./scripts/probe-retrieval-adapters.sh --dry-run` 和 `./scripts/probe-production-adapter-profile.sh --dry-run`。

目标不是立刻接入所有中间件，而是让 RAG 检索能力继续从 `infra-dev-scaffolding` 生长出来：默认教学路径保持轻启动，生产替换点清晰可控。

## Adapter 轴

### Vector Store

- 业务接口：`VectorStoreService`。
- 配置入口：`VectorStoreProperties`。
- 默认实现：`MemoryVectorStoreService`。
- 默认 provider：`VECTOR_STORE_PROVIDER=memory`。
- 生产化 provider 骨架：`PgVectorStoreService`。
- 未来 provider：Milvus、托管向量库。

`DocumentEmbeddingService` 负责写入向量，`RetrievalService` 负责检索向量，两者都只依赖 `VectorStoreService`。具体 provider 通过 `app.vector-store.provider` 和 `@ConditionalOnProperty` 切换。

向量库 adapter 只负责向量写入、查询和删除，不负责 chunk metadata 补全、答案引用和 prompt 组装。

`PgVectorStoreService` 使用 `JdbcTemplate` 接入 PostgreSQL + pgvector，默认不启用 schema 初始化，保持 `memory` 教学路径无外部依赖。

### Runtime Status

- API：`GET /api/retrieval/adapters/status`。
- Controller：`RetrievalController.adapterStatus`。
- Service：`RetrievalAdapterStatusService`。
- 前端：`RetrievalService.adapterStatus`，Pipeline `Adapter Matrix` 展示当前运行态 provider。

这个接口只读取 `VectorStoreProperties`、`KeywordSearchProperties`、`RerankProperties` 和 `DocParserProperties`，用于教学说明当前进程实际跑在哪个 provider 上。它不负责切换 provider，也不绕过 `retrieval-adapter-contract.json` 和 `doc-parser-contract.json`。

## Production Adapter Profile

生产化 adapter 的最小可切换预设放在 `backend/src/main/resources/application-prod-adapters.yml`，示例环境变量放在 `backend/.env.prod-adapters.example`。

```bash
SPRING_PROFILES_ACTIVE=prod,prod-adapters
VECTOR_STORE_PROVIDER=pgvector
KEYWORD_SEARCH_PROVIDER=bm25
RERANK_PROVIDER=remote
DOC_PARSER_MODE=async
DOC_PARSER_ASYNC_RECOVERY_ENABLED=true
```

这套 profile 的默认 keyword provider 选择 `bm25`，让课程可以先完成 PostgreSQL + pgvector、remote rerank 和 async doc-parser 的生产化切换，再把 `KEYWORD_SEARCH_PROVIDER=elasticsearch` 作为下一步升级。`./scripts/probe-production-adapter-profile.sh --dry-run` 会检查 profile、env 样例、契约和 dry-run 启动形态。

默认后端未启动时，Pipeline 仍展示设计态矩阵；后端启动后，页面会补充当前 provider、实现类、配置 key 和切换命令。

### Keyword Search

- 业务接口：`KeywordSearchProvider`。
- 配置入口：`KeywordSearchProperties`。
- 默认实现：`LocalKeywordSearchProvider`。
- 默认 provider：`KEYWORD_SEARCH_PROVIDER=local`。
- 轻量 ranking provider：`Bm25KeywordSearchProvider`。
- 生产化 provider 骨架：`ElasticsearchKeywordSearchProvider`。

`KeywordSearchProvider` 只返回关键词召回候选；`RetrievalHybridSearchService` 继续负责向量候选、关键词候选的 RRF 合并、分数归一和 `retrievalSource` 标注。

`Bm25KeywordSearchProvider` 使用本地 ChunkRepository 做 deterministic BM25 ranking，适合教学说明从简单 lexical 到 BM25 的演进。`ElasticsearchKeywordSearchProvider` 使用脚手架 `RemoteHttpClient` 调用 Elasticsearch `_search`，调用观测目标是 `keyword-search-provider`。这样替换 BM25 或 Elasticsearch 时，不需要重写 `RetrievalService`、聊天引用或前端展示。

### Rerank Provider

- 编排服务：`RetrievalRerankService`。
- 远程客户端：`RerankProviderClient`。
- 配置入口：`RerankProperties`。
- 默认 provider：`RERANK_PROVIDER=local-demo`。
- 本地解释 provider：`local-lexical`。
- 远程 provider：`remote` 或具体模型供应商。

本地演示和单测默认走确定性 lexical rerank。切换远程 provider 后，`RerankProviderClient` 必须复用脚手架的 `RemoteHttpClient`，并使用 `targetService("rerank-provider")`，这样超时、错误归一、请求上下文和调用观测都延续脚手架习惯。

## Java 和 Python 边界

Java 后端负责 RAG 检索链路：

- query embedding。
- vector search。
- keyword search。
- hybrid merge。
- rerank。
- result enrichment。
- context assembly。
- answer citation。

Python doc-parser 只负责文档解析：

- 文件或 URL 解析。
- 文本、表格、图片、页码和 layout metadata 输出。
- async submit/status。

Python doc-parser 不连接向量库，不做检索，不组装 prompt，也不拥有 `VectorStoreService`、`KeywordSearchProvider` 或 `RerankProperties`。

## 配置示例

默认教学配置：

```env
VECTOR_STORE_PROVIDER=memory
KEYWORD_SEARCH_PROVIDER=local
RERANK_PROVIDER=local-demo
```

生产化方向示例：

```env
VECTOR_STORE_PROVIDER=pgvector
VECTOR_STORE_PGVECTOR_TABLE_NAME=rag_vectors
KEYWORD_SEARCH_PROVIDER=elasticsearch
KEYWORD_SEARCH_BM25_K1=1.2
KEYWORD_SEARCH_BM25_B=0.75
KEYWORD_SEARCH_BM25_MINIMUM_SCORE=0.0
KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL=http://localhost:9200
KEYWORD_SEARCH_ELASTICSEARCH_INDEX_PREFIX=kb_
RERANK_PROVIDER=remote
RERANK_API_URL=https://api.cohere.com/v2/rerank
RERANK_MODEL=rerank-v3.5
```

实现真实 provider 前，先补：

- provider 配置类或 properties 字段。
- adapter 实现和 `@ConditionalOnProperty`。
- `.env.example`。
- 契约文档。
- adapter 级测试。
- `scripts/check-retrieval-adapter-contract.js` 中的守护项。

## 模块边界规则

- `RetrievalService` 只做检索主流程编排，不写具体中间件查询语法。
- `RetrievalResultEnrichmentService` 负责补全文档名、知识库名、metadata 和 citation 字段。
- `RetrievalHybridSearchService` 负责合并候选，不直接依赖 `ChunkRepository`。
- `KeywordSearchProvider` 实现可以依赖本地仓储或外部搜索引擎。
- `Bm25KeywordSearchProvider` 只负责本地 BM25 ranking，不负责 RRF 合并。
- `ElasticsearchKeywordSearchProvider` 只负责 `_search` request/response adapter，不负责 RRF 合并。
- `RetrievalRerankService` 决定本地 fallback 与远程 rerank。
- `RerankProviderClient` 只适配远程 HTTP request/response。
- `RagPromptBuilderService` 负责上下文进入 prompt 的 trace，不依赖具体检索 provider。
- 前端只消费 `rank`、`scoreExplanation`、`retrievalSource`、`keywordScore`、`hybridScore`、`rerankScore`、`rerankProvider` 等解释字段，不感知后端 provider 细节。

## 教学顺序

1. 从 `retrieval-adapter-contract.json` 讲三条可替换轴：Vector Store、Keyword Search、Rerank Provider。
2. 打开 `VectorStoreProperties`、`VectorStoreService` 和 `PgVectorStoreService`，说明业务只依赖接口，生产向量库通过 adapter 接入。
3. 打开 `KeywordSearchProperties` 和 `LocalKeywordSearchProvider`，说明本地 provider 为什么适合默认教学路径。
4. 打开 `Bm25KeywordSearchProvider`，说明本地 ranking 如何从 lexical 演进到 BM25。
5. 打开 `ElasticsearchKeywordSearchProvider`，说明生产化搜索引擎如何通过 `RemoteHttpClient` 接入。
6. 打开 `RetrievalHybridSearchService`，说明 RRF 合并不属于 Elasticsearch 或 BM25 provider。
7. 打开 `RerankProperties` 和 `RerankProviderClient`，说明 provider 配置、远程调用和脚手架 `RemoteHttpClient` 的关系。
8. 打开 Pipeline 的 Adapter Matrix，说明 `RetrievalService.adapterStatus` 能显示当前运行态 provider 和实现类。
9. 打开检索调试页和 Chat 引用卡，说明 provider 变化不会破坏前端证据链展示。
10. 运行 `./scripts/probe-retrieval-adapters.sh --dry-run`、`./scripts/probe-production-adapter-profile.sh --dry-run`、`node scripts/check-retrieval-adapter-contract.js` 和 `./scripts/check-contracts.sh`，说明设计边界会被门禁守住。

## V2 结论

当前阶段完成的是生产化 adapter 的边界沉淀、pgvector provider 骨架、BM25 ranking provider 和 Elasticsearch provider 骨架，而不是要求默认环境引入外部中间件。这样课程可以先稳定演示 RAG 全链路，再逐步把 `memory/local/local-demo` 替换为 Milvus、pgvector、BM25、Elasticsearch 或远程 rerank provider。
