# Vector Store Adapter Guide

本文档定义 agent-knowledge 的向量库适配边界。目标是让本地教学默认使用 memory provider，同时为 Milvus、pgvector 或托管向量库留下清晰替换点。

## 设计原则

- 业务链路只依赖 `VectorStoreService` 接口。
- `MemoryVectorStoreService` 只用于本地开发、演示和教学。
- 检索、聊天和文档处理流程不能硬编码具体向量库。
- 新增 provider 时先补配置、契约文档和验证脚本，再接真实中间件。
- 向量库 adapter 属于 Java 后端的基础设施边界，不属于 Python doc-parser。

## 当前 Provider

| Provider | 实现类 | 默认启用 | 适用场景 |
|----------|--------|----------|----------|
| `memory` | `MemoryVectorStoreService` | 是 | 本地演示、课程讲解、无外部依赖烟测 |

启用方式：

```env
VECTOR_STORE_PROVIDER=memory
VECTOR_STORE_COLLECTION_PREFIX=kb_
```

对应 Spring 配置：

```yaml
app:
  vector-store:
    provider: ${VECTOR_STORE_PROVIDER:memory}
    collection-prefix: ${VECTOR_STORE_COLLECTION_PREFIX:kb_}
```

## Java 后端边界

`VectorStoreService` 是唯一对业务暴露的向量库接口：

- `upsert`：写入单个 chunk 向量。
- `upsertBatch`：批量写入 chunk 向量。
- `search`：按知识库集合和 query vector 返回候选 chunk。
- `deleteByDocChunks`：删除文档对应 chunk 向量。
- `deleteByKbId`：删除知识库集合。
- `getVectorCount`：返回知识库向量数量。

调用关系：

```text
DocumentProcessingService
  -> EmbeddingService
  -> VectorStoreService

RetrievalService
  -> EmbeddingService
  -> VectorStoreService
  -> KnowledgeChunkRepository

ChatService
  -> RetrievalService
```

`RetrievalService` 负责把向量库返回的 chunk id 补齐为文档名、页码、content_type、metadata 和引用信息。向量库 adapter 不负责组装回答引用。

## Python doc-parser 边界

doc-parser 只负责文件解析，不负责向量存储：

- 接收文件或 URL。
- 返回文本、表格、图片、页码和 layout metadata。
- 不连接 Java 数据库。
- 不连接向量库。
- 不决定 chunk 策略、Embedding 模型和检索策略。

Java 后端负责把 doc-parser 的解析结果转成 chunk、embedding 和 vector upsert。

## 新增 Provider 步骤

以 `milvus` 为例：

1. 新增 `MilvusVectorStoreService implements VectorStoreService`。
2. 使用 `@ConditionalOnProperty(prefix = "app.vector-store", name = "provider", havingValue = "milvus")` 控制启用。
3. 新增 provider 独立配置，例如 `app.vector-store.milvus.host`、`collection-name`、`dimension`。
4. 更新 `backend/.env.example`。
5. 更新本文档和 `ENVIRONMENT_PROFILE_GUIDE.md`。
6. 保留 memory provider 作为 dev/test 默认值。
7. 增加 adapter 级单元测试或集成测试。
8. 运行脚手架质量门禁。

## 脚手架一致性

向量库替换也要遵守脚手架习惯：

- 配置入口放在 `@ConfigurationProperties`，不要散落读取 env。
- 默认 profile 不强依赖外部中间件。
- API 继续使用 `APIResponse<T>` 和 `PageResult<T>`。
- 前端路径继续通过 `ApiPaths`。
- 每次新增 provider 同步文档、`.env.example` 和验证命令。

## V1.5 结论

当前阶段只实现 memory provider 和 adapter 边界。这样课程可以先聚焦 RAG 主链路，再在 V2 讲生产化向量库替换，而不需要学习者重新理解底层工程体系。
