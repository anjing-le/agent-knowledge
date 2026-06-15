# Retrieval Adapter Switch Guide

本文档说明如何从默认教学检索栈切换到生产化检索 adapter。对应无外部依赖探针是：

```bash
./scripts/probe-retrieval-adapters.sh --dry-run
./scripts/probe-production-adapter-profile.sh --dry-run
```

## 默认教学栈

默认配置保持轻启动，不依赖 PostgreSQL、Elasticsearch 或远程 rerank provider：

```env
VECTOR_STORE_PROVIDER=memory
KEYWORD_SEARCH_PROVIDER=local
RERANK_PROVIDER=local-demo
```

这条路径用于课程演示、H2 smoke、CI contract check 和 `scripts/smoke-rag-demo.sh`。

## 生产化检索栈

生产化检索切换为三条 adapter 轴：

| 轴线 | 默认 provider | 生产化 provider | 代码边界 |
|------|---------------|-----------------|----------|
| Vector Store | `memory` | `pgvector` | `VectorStoreService` / `PgVectorStoreService` |
| Keyword Search | `local` | `bm25` / `elasticsearch` | `KeywordSearchProvider` / `Bm25KeywordSearchProvider` / `ElasticsearchKeywordSearchProvider` |
| Rerank | `local-demo` | `remote` | `RetrievalRerankService` / `RerankProviderClient` |

生产化 env 示例：

```env
SPRING_PROFILES_ACTIVE=prod,prod-adapters
DB_URL=jdbc:postgresql://localhost:5432/agent_knowledge
DB_DRIVER=org.postgresql.Driver
DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect

DOC_PARSER_MODE=async
DOC_PARSER_ASYNC_RECOVERY_ENABLED=true

VECTOR_STORE_PROVIDER=pgvector
VECTOR_STORE_PGVECTOR_TABLE_NAME=rag_vectors
VECTOR_STORE_PGVECTOR_SCHEMA_INITIALIZATION_ENABLED=false

KEYWORD_SEARCH_PROVIDER=bm25
KEYWORD_SEARCH_BM25_K1=1.2
KEYWORD_SEARCH_BM25_B=0.75
KEYWORD_SEARCH_BM25_MINIMUM_SCORE=0.0

# 可选升级到外部搜索引擎
# KEYWORD_SEARCH_PROVIDER=elasticsearch
KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL=http://localhost:9200
KEYWORD_SEARCH_ELASTICSEARCH_INDEX_PREFIX=kb_
KEYWORD_SEARCH_ELASTICSEARCH_API_KEY=

RERANK_PROVIDER=remote
RERANK_API_URL=https://api.cohere.com/v2/rerank
RERANK_API_KEY=
RERANK_MODEL=rerank-v3.5
```

`backend/.env.prod-adapters.example` 是这套预设的可复制模板，`backend/src/main/resources/application-prod-adapters.yml` 是 Spring Boot profile 覆盖文件。

## 依赖服务

- PostgreSQL 需要安装 pgvector extension。
- Elasticsearch 或 OpenSearch 需要准备与知识库对应的 index，例如 `kb_<kbId>`。
- Rerank provider 需要提供 OpenAI/Cohere-like rerank API URL、API key 和 model。
- Java 后端仍保持 Spring Boot/Java 脚手架技术栈。
- Python doc-parser 仍是独立 FastAPI 服务，不连接向量库或搜索引擎。

## 切换顺序

1. 先在默认教学栈运行 `./scripts/check-contracts.sh`。
2. 执行 `./scripts/probe-retrieval-adapters.sh --dry-run` 和 `./scripts/probe-production-adapter-profile.sh --dry-run`，确认配置、契约、profile 和切换命令齐全。
3. 准备 PostgreSQL + pgvector。
4. 切换 `VECTOR_STORE_PROVIDER=pgvector`，必要时开启 `VECTOR_STORE_PGVECTOR_SCHEMA_INITIALIZATION_ENABLED=true` 初始化表。
5. 可先切换 `KEYWORD_SEARCH_PROVIDER=bm25`，用本地 ChunkRepository 验证 BM25 ranking。
6. 准备 Elasticsearch/OpenSearch index，并写入 chunk 文本字段。
7. 切换 `KEYWORD_SEARCH_PROVIDER=elasticsearch`。
8. 配置远程 rerank provider，切换 `RERANK_PROVIDER=remote`。
9. 运行 adapter 级测试和 RAG demo smoke。

## 验证命令

无外部依赖验证：

```bash
./scripts/probe-retrieval-adapters.sh --dry-run
./scripts/probe-production-adapter-profile.sh --dry-run
node scripts/check-retrieval-adapter-contract.js
./scripts/check-contracts.sh
```

运行态 provider 快照：

```bash
curl -fsS http://localhost:10001/api/retrieval/adapters/status
```

adapter 单测：

```bash
(cd backend && mvn -q -Dtest=PgVectorStoreServiceTest,Bm25KeywordSearchProviderTest,ElasticsearchKeywordSearchProviderTest,RerankProviderClientTest test)
```

默认 RAG demo smoke：

```bash
./scripts/smoke-rag-demo.sh
```

## 边界约束

- `RetrievalService` 不能直接依赖 pgvector SQL、Elasticsearch query DSL 或 rerank HTTP response。
- `PgVectorStoreService` 只实现向量写入、查询、删除和计数。
- `Bm25KeywordSearchProvider` 只实现本地 BM25 ranking，不负责 RRF 合并。
- `ElasticsearchKeywordSearchProvider` 只实现 `_search` request/response adapter。
- `RerankProviderClient` 只实现远程 rerank request/response adapter。
- `RetrievalHybridSearchService` 继续负责 RRF 合并。
- `RetrievalResultEnrichmentService` 继续负责 chunk/document/kb metadata 补全。
- `RagPromptBuilderService` 继续负责 context assembly trace。

## 教学讲法

讲课时可以先运行默认栈，再展示这三步切换：

```text
memory -> pgvector
local keyword -> bm25 -> elasticsearch
local-demo rerank -> remote rerank
sync doc-parser -> async recovery doc-parser
profile -> SPRING_PROFILES_ACTIVE=prod,prod-adapters
runtime status -> /api/retrieval/adapters/status
```

学习者需要关注的是 RAG 模块设计和 provider 边界，而不是重新学习响应、分页、路径、OpenAPI、请求上下文、远程调用和质量门禁。这些底层习惯继续来自 `infra-dev-scaffolding`。
