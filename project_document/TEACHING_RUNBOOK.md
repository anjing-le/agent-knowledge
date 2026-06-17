# Teaching Runbook

本文用于 V1.1 教学交付：把 agent-knowledge 讲成一个从 `infra-dev-scaffolding` 生长出来的 RAG agent，而不是一个孤立的知识库项目。

## 交付目标

一节课结束时，学习者应该记住三件事：

- 工程底座来自脚手架：技术栈、响应、分页、路径、OpenAPI、请求上下文、远程调用和质量门禁不用重新设计。
- agent-knowledge 只新增 RAG 业务设计：文档 ingestion、Python doc-parser、切片、Embedding、检索、上下文组装和引用回答。
- 未来做其他 agent，只需要复用脚手架地基，再替换业务模块边界、页面体验和证据链。

## 课前检查

每次演示前先确认仓库和 CI：

```bash
git status --short --branch
git log -1 --pretty=fuller --date=iso
git config --get user.name
git config --get user.email
gh run list --limit 2 --json headSha,status,conclusion,workflowName,url
```

必须满足：

- 当前分支已推送到 `origin/main`，必要时同步 `origin/master`。
- 最近提交作者是 `安静 <245548353+anjing-le@users.noreply.github.com>`。
- GitHub Actions `Quality Gate` 对最新提交是 success。
- 本地只有明确不处理的 `reference/` 或其他已知用户资料未跟踪。

## 本地启动

三服务启动顺序保持稳定：

```bash
# Python doc-parser
(cd doc-parser && python -m uvicorn kparser.app:app --host 0.0.0.0 --port 9001)
```

```bash
# Java backend
(cd backend && mvn spring-boot:run)
```

```bash
# Vue frontend
(cd frontend && pnpm install && pnpm dev)
```

最小验证：

```bash
curl -fsS http://localhost:9001/health
curl -fsS http://localhost:10001/api/test/health
curl -fsS http://localhost:10001/api/test/features
```

## 课堂主线

按这个顺序讲，不要一开始钻实现细节。

1. 先打开 `README.md`。
   讲项目定位：这是基于脚手架生长出来的 RAG 智能知识库，高级 agent 示例只关注业务设计。

2. 打开 `contracts/scaffold-stack-contract.json`。
   讲技术栈边界：Vue/TypeScript/Vite、Spring Boot/Java 17、Python FastAPI doc-parser。

3. 打开 `.github/workflows/quality-gate.yml`。
   讲质量入口：本地和 CI 都执行 `./scripts/quality-gate.sh`，脚手架约束不是口号。

4. 打开前端 RAG Pipeline 页面。
   讲第一屏：Demo Ready、Adapter Matrix、Teaching Runbook、V1 Teaching Baseline。

5. 执行 seed。
   使用页面按钮或命令：

   ```bash
   ./scripts/seed-rag-demo.sh
   ```

6. 进入 Retrieval 页面。
   讲 query、kbIds、rank、retrievalSource、hybrid/rerank score 和 scoreExplanation。

7. 进入 Chat 页面。
   讲 contextTrace：prompt sections、history window、included chunks、answer references。

8. 进入 Knowledge 详情页。
   讲文档处理任务：upload -> parsing -> chunking -> embedding -> completed。

9. 展示 Python doc-parser 边界。
   Java 只通过 HTTP contract 调用 Python：

   ```bash
   ./scripts/probe-doc-parser-boundary.sh --contract-only
   ./scripts/check-doc-parser-lifecycle.sh
   ```

10. 展示 retrieval adapter 边界。
    讲默认教学实现和生产替换轴：

    ```bash
    ./scripts/probe-retrieval-adapters.sh --dry-run
    ./scripts/probe-production-adapter-profile.sh --dry-run
    ```

11. 展示证据包。
    讲一次演示如何留下可复现材料：

    ```bash
    ./scripts/collect-demo-evidence.sh --dry-run
    ```

## 必讲边界

Java 后端和 Python doc-parser：

- Java 后端负责账号、知识库、文档状态、RAG 编排、检索、聊天和引用。
- Python doc-parser 负责解析 PDF/Word/Excel/TXT 等文档内容。
- Java 不能内嵌 Python 解析逻辑，只能通过 HTTP contract 调用。

默认教学实现和生产替换：

- Vector Store 默认是 memory，生产可切到 pgvector 或后续 Milvus。
- Keyword Search 默认是 local，过渡可用 BM25，生产可切 Elasticsearch。
- Rerank 默认是 local-demo，生产可切 remote rerank provider。
- doc-parser 默认 sync，长文档生产化可切 async/recovery。

前端页面边界：

- Pipeline 负责讲全链路。
- Knowledge 负责文档和处理任务。
- Retrieval 负责解释召回和排序。
- Chat 负责解释上下文组装和答案引用。

## 现场验收

一场演示至少要证明：

- GitHub Actions 最新 `Quality Gate` 是 success。
- `./scripts/quality-gate.sh` 本地可通过或最近一次证据包已记录通过。
- Seed demo 能创建知识库、文档、Chunk、Embedding 和检索数据。
- Retrieval 能展示命中的 chunk、rank、source 和 scoreExplanation。
- Chat 能生成带引用回答，并展示 contextTrace。
- doc-parser 边界脚本能说明 Python 服务独立存在。
- evidence dry-run 能说明证据包落点和收集命令。

## 讲解话术

可以用这几句话收束：

- “这个项目不是重新造脚手架，而是在脚手架上生长 RAG 业务。”
- “学习者要关注的是 RAG 设计，不是每次重学响应、分页、OpenAPI 和质量门禁。”
- “Java 负责编排，Python 负责文档解析，两边用 HTTP contract 保持边界。”
- “默认实现为了教学轻启动，adapter 边界告诉你怎么走向生产。”
- “Evidence Chain 不是 UI 装饰，而是把 query 到 answer citation 的可信路径讲清楚。”

## 课后动作

课后只做三件事：

```bash
./scripts/quality-gate.sh
./scripts/collect-demo-evidence.sh --date YYYY-MM-DD --force
git status --short --branch
```

如果需要继续迭代，从 V2/V3 扩展里选一个小点做，不要破坏 V1 teaching baseline：

- 多知识库路由或 query rewrite。
- 页码、表格、图片和坐标级引用。
- 真实 pgvector/BM25/remote rerank 联调。
- answer quality panel。
- GitHub Actions 证据归档。
