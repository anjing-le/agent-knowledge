# Demo Evidence

本文档记录 agent-knowledge 演示或发布前需要保留的证据。

## 发布前证据清单

1. 后端编译通过：`(cd backend && mvn -q -DskipTests compile)`。
2. 前端构建通过：`(cd frontend && pnpm build)`。
3. 运行态 RAG demo seed 通过：`./scripts/seed-rag-demo.sh`。
4. 本地 RAG demo smoke 通过：`./scripts/smoke-rag-demo.sh`。
5. doc-parser 健康检查通过：`curl http://localhost:9001/health`。
6. 后端健康检查通过：`curl http://localhost:10001/api/test/health`。
7. 前端能打开 RAG Pipeline、知识库列表、知识库详情、切片页、检索调试和智能对话页。
8. RAG Pipeline 页面能说明脚手架地基、RAG 阶段服务、Java/Python 边界，并展示 `Seed -> Retrieval -> Chat -> Evidence` Demo Ready checklist。
9. Demo Ready 进入检索调试页后，页面自动带入 query/kbIds 并展示 chunk 命中结果。
10. Demo Ready 进入知识问答页后，页面自动带入 query/kbIds、创建会话并展示引用回答。
11. 上传一份小文档，状态进入完成，切片可查看。
12. 在聊天页选择知识库提问，回答展示引用来源。

## 建议目录

可以先用 dry-run 检查目标，再生成当天证据包：

```bash
./scripts/create-demo-evidence.sh --dry-run
./scripts/create-demo-evidence.sh --date YYYY-MM-DD
```

脚本默认不会覆盖已有 `README.md`；只有明确传入 `--force` 时才会替换已有证据包首页。

```text
docs/evidence/YYYY-MM-DD/
  README.md
  screenshots/
    rag-pipeline.png
    retrieval-auto-search.png
    chat-with-citations.png
  outputs/
    check-template.txt
    check-contracts.txt
    seed-rag-demo.txt
    smoke-rag-demo.txt
    probe-backend-dev.txt
    frontend-build.txt
  runtime/
    backend-probe.txt
    doc-parser-health.json
```

## 记录模板

关键截图路径建议使用完整相对路径，例如 `screenshots/rag-pipeline.png`、`screenshots/retrieval-auto-search.png`、`screenshots/chat-with-citations.png`。

```markdown
# Evidence YYYY-MM-DD

- Commit: `<commit>`
- Frontend: `http://localhost:20001`
- Backend: `http://localhost:10001`
- Doc Parser: `http://localhost:9001`
- Scenario: `Seed -> Retrieval -> Chat -> Evidence`
- Backend compile: passed
- Frontend build: passed
- Evidence package: `docs/evidence/YYYY-MM-DD/`
- Evidence dry-run: `./scripts/create-demo-evidence.sh --dry-run`
- RAG demo seed: `./scripts/seed-rag-demo.sh`
- RAG demo smoke: `./scripts/smoke-rag-demo.sh`
- Backend probe: `./scripts/probe-backend-dev.sh`
- RAG upload/search/chat: passed
```

## 不提交内容

- 真实 API Key、Cookie、Token。
- 本地个人路径截图。
- 上传原始文件、构建产物、后端 target、前端 dist。
