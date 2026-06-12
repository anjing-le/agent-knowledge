# Roadmap

## 定位

agent-knowledge 是 Anjing Agent 系列中的高级 RAG 示例。它不是简单 CRUD 知识库，而是把 RAG 全链路拆清楚：

文档上传 -> Python 解析 -> 切片 -> Embedding -> 向量检索 -> 上下文组装 -> LLM 回答 -> 引用回传。

项目同时承担两个目标：

1. 对齐工程脚手架的结构、契约和质量门禁。
2. 沉淀一个可讲解、可演示、可继续扩展的 RAG agent 样板。

## V1: 可运行的 RAG 主链路

目标：三服务本地可启动，用户可以上传文档并完成带引用的问答。

范围：

- 前端：知识库列表、知识库详情、文档上传、切片查看、聊天页面。
- Java 后端：知识库 CRUD、文档状态机、doc-parser 客户端、Chunk 持久化、Embedding、检索、聊天。
- Python doc-parser：同步 `/parse` 和 `/parse_url` 支撑 Java 文档解析。
- 契约：统一 `APIResponse<T>`、`PageResult<T>`、`ApiConstants`、`ApiPaths`、OpenAPI 和 service-boundary manifest。
- 可观测性：日志统一输出 requestId、traceId、用户、租户、路径、耗时和错误码。

验收：

- `(cd backend && mvn -q -DskipTests compile)` 通过。
- `(cd frontend && pnpm build)` 通过。
- `curl http://localhost:9001/health` 返回 doc-parser 健康状态。
- `/api/knowledge/bases`、`/api/knowledge/bases/{kbId}/documents`、`/api/chat/conversations` 路径稳定。

## V2: 生产化 ingestion

目标：把文档处理从同步演示链路升级为可观测、可重试、可恢复的任务链路。

V1.5 先完成以下铺垫：

- 前端主菜单收敛为 RAG 工作区，隐藏模板系统页入口。
- 新增 `document_processing_task`，记录每次文档处理尝试。
- 抽象 `VectorStoreService`，保留内存实现并预留 Milvus/pgvector adapter。
- 检索和聊天引用透出 chunk metadata，支持页码和内容类型展示。

计划：

- Java 接入 doc-parser `/loader/deep_parse/async` 和 `/loader/status`。
- 异步接口契约先行：`contracts/doc-parser-contract.json` 固化请求/响应和 Java 状态映射。
- 文档状态拆出 `UPLOADED/PARSING/PARSED/CHUNKING/EMBEDDING/COMPLETED/FAILED`。
- 增加解析任务表、失败原因、重试次数和用户可见进度。
- Embedding 批处理、限流、重试和失败恢复。
- Chunk metadata 标准化，支持页码、图片、表格和坐标引用。
- 检索增加 hybrid search、rerank 和 score 解释。
- 异步任务接入前必须持续通过 `scripts/check-async-context-contract.js`，确保后台线程继承请求上下文和 MDC。

## V3: 高级 agent 能力

目标：把知识库从“问答工具”升级为可复用的高级 agent 示例。

计划：

- 多知识库路由和查询改写。
- 引用可视化定位到页码、表格、图片和原文区域。
- 长文档增量更新和删除后的向量一致性。
- 多租户、权限隔离和审计。
- 解析/Embedding/LLM provider 可插拔。
- 评测集、召回率/准确率指标和回答质量面板。

## 旧代码策略

可复用：

- 现有知识库、文档、Chunk、会话、消息实体和基础页面。
- Python doc-parser 的解析核心和同步接口。
- 现有教学材料与 RAG 讲解文档。
- 从脚手架迁入的响应、分页、请求上下文、OpenAPI、service-boundary、远程调用基础能力。

应重写或收敛：

- 旧的 `/api/knowledge-base`、`/api/documents`、`/api/chunks` 路径。
- Controller 中裸 `Map` 分页和内联 request body。
- 前端 API 模块中的硬编码 URL 和二次 envelope 类型。
- doc-parser 异步任务与 Java 文档状态的集成方式。
