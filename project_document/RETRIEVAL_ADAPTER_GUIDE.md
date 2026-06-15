# Retrieval Adapter Guide

本文档定义 agent-knowledge 的检索生产化 adapter 边界。对应机器契约是 [../contracts/retrieval-adapter-contract.json](../contracts/retrieval-adapter-contract.json)。

目标不是立刻接入所有中间件，而是让 RAG 检索能力继续从 `infra-dev-scaffolding` 生长出来：默认教学路径保持轻启动，生产替换点清晰可控。

## Adapter 轴

### Vector Store

- 业务接口：`VectorStoreService`。
- 默认实现：`MemoryVectorStoreService`。
- 默认 provider：`VECTOR_STORE_PROVIDER=memory`。
- 未来 provider：Milvus、pgvector、托管向量库。

`DocumentEmbeddingService` 负责写入向量，`RetrievalService` 负责检索向量，两者都只依赖 `VectorStoreService`。具体 provider 通过 `app.vector-store.provider` 和 `@ConditionalOnProperty` 切换。

向量库 adapter 只负责向量写入、查询和删除，不负责 chunk metadata 补全、答案引用和 prompt 组装。

### Keyword Search

- 业务接口：`KeywordSearchProvider`。
- 配置入口：`KeywordSearchProperties`。
- 默认实现：`LocalKeywordSearchProvider`。
- 默认 provider：`KEYWORD_SEARCH_PROVIDER=local`。
- 生产化 provider 骨架：`ElasticsearchKeywordSearchProvider`。
- 未来 provider：BM25。

`KeywordSearchProvider` 只返回关键词召回候选；`RetrievalHybridSearchService` 继续负责向量候选、关键词候选的 RRF 合并、分数归一和 `retrievalSource` 标注。

`ElasticsearchKeywordSearchProvider` 使用脚手架 `RemoteHttpClient` 调用 Elasticsearch `_search`，调用观测目标是 `keyword-search-provider`。这样替换 Elasticsearch 或后续补 BM25 时，不需要重写 `RetrievalService`、聊天引用或前端展示。

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
VECTOR_STORE_PROVIDER=milvus
KEYWORD_SEARCH_PROVIDER=elasticsearch
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
- `ElasticsearchKeywordSearchProvider` 只负责 `_search` request/response adapter，不负责 RRF 合并。
- `RetrievalRerankService` 决定本地 fallback 与远程 rerank。
- `RerankProviderClient` 只适配远程 HTTP request/response。
- `RagPromptBuilderService` 负责上下文进入 prompt 的 trace，不依赖具体检索 provider。
- 前端只消费 `rank`、`scoreExplanation`、`retrievalSource`、`keywordScore`、`hybridScore`、`rerankScore`、`rerankProvider` 等解释字段，不感知后端 provider 细节。

## 教学顺序

1. 从 `retrieval-adapter-contract.json` 讲三条可替换轴：Vector Store、Keyword Search、Rerank Provider。
2. 打开 `VectorStoreService`，说明业务只依赖接口。
3. 打开 `KeywordSearchProperties` 和 `LocalKeywordSearchProvider`，说明本地 provider 为什么适合默认教学路径。
4. 打开 `ElasticsearchKeywordSearchProvider`，说明生产化搜索引擎如何通过 `RemoteHttpClient` 接入。
5. 打开 `RetrievalHybridSearchService`，说明 RRF 合并不属于 Elasticsearch 或 BM25 provider。
6. 打开 `RerankProperties` 和 `RerankProviderClient`，说明 provider 配置、远程调用和脚手架 `RemoteHttpClient` 的关系。
7. 打开检索调试页和 Chat 引用卡，说明 provider 变化不会破坏前端证据链展示。
8. 运行 `node scripts/check-retrieval-adapter-contract.js` 和 `./scripts/check-contracts.sh`，说明设计边界会被门禁守住。

## V2 结论

当前阶段完成的是生产化 adapter 的边界沉淀和 Elasticsearch provider 骨架，而不是要求默认环境引入外部中间件。这样课程可以先稳定演示 RAG 全链路，再逐步把 `memory/local/local-demo` 替换为 Milvus、pgvector、Elasticsearch、BM25 或远程 rerank provider。
