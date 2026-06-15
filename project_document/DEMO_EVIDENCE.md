# Demo Evidence

本文档记录 agent-knowledge 演示或发布前需要保留的证据。

## 发布前证据清单

1. 后端编译通过：`(cd backend && mvn -q -DskipTests compile)`。
2. 前端构建通过：`(cd frontend && pnpm build)`。
3. 运行态 RAG demo seed 通过：`./scripts/seed-rag-demo.sh`。
4. RAG demo retrieval evaluation 通过：`./scripts/evaluate-rag-retrieval.sh`。
5. RAG demo runtime probe 通过：`./scripts/probe-rag-demo-runtime.sh`。
6. 本地 RAG demo smoke 通过：`./scripts/smoke-rag-demo.sh`。
7. 一键证据收集 dry-run 通过：`./scripts/collect-demo-evidence.sh --dry-run`。
8. 运行态 Adapter 状态可获取：`curl -fsS http://localhost:10001/api/retrieval/adapters/status`。
9. 生产化 Adapter Profile dry-run 通过：`./scripts/probe-production-adapter-profile.sh --dry-run`。
10. doc-parser 健康检查通过：`curl http://localhost:9001/health`。
11. doc-parser async submit/status live smoke 通过：`./scripts/smoke-doc-parser-async.sh`。
12. 后端健康检查通过：`curl http://localhost:10001/api/test/health`。
13. 前端能打开 RAG Pipeline、知识库列表、知识库详情、切片页、检索调试和智能对话页。
14. RAG Pipeline 页面能说明脚手架地基、RAG 阶段服务、Java/Python 边界，并展示 `Seed -> Evaluate -> Retrieval -> Chat -> Evidence` Demo Ready checklist。
15. Demo Ready 进入检索调试页后，页面自动带入 query/kbIds 并展示 chunk 命中结果。
16. Demo Ready 进入知识问答页后，页面自动带入 query/kbIds、创建会话并展示引用回答。
17. Retrieval Evaluation 面板能展示 recall@K、通过用例数、suite、kbId、每个 query 的 rank/top chunk/score explanation。
18. 聊天答案引用卡能展示 rank、retrievalSource、hybrid/rerank 分数、scoreExplanation、chunk metadata 和查看切片入口。
19. 聊天答案能展示上下文组装 trace，包含 assemblyStrategy、prompt sections、history window、prompt/context 字符数和 included chunks。
20. 上传一份小文档，状态进入完成，切片可查看。
21. 在聊天页选择知识库提问，回答展示引用来源。

## 建议目录

可以先用 dry-run 检查目标，再生成当天证据包：

```bash
./scripts/create-demo-evidence.sh --dry-run
./scripts/probe-doc-parser-boundary.sh --contract-only
./scripts/check-doc-parser-lifecycle.sh
./scripts/smoke-doc-parser-async.sh
./scripts/probe-production-adapter-profile.sh --dry-run
./scripts/probe-rag-demo-runtime.sh
curl -fsS http://localhost:10001/api/retrieval/adapters/status
./scripts/collect-demo-evidence.sh --dry-run
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
    probe-doc-parser-boundary.txt
    check-doc-parser-lifecycle.txt
    probe-production-adapter-profile.txt
    smoke-doc-parser-async.txt
    seed-rag-demo.txt
    evaluate-rag-retrieval.txt
    probe-rag-demo-runtime.txt
    smoke-rag-demo.txt
    probe-backend-dev.txt
    frontend-build.txt
  runtime/
    summary.txt
    backend-probe.txt
    doc-parser-health.json
    backend-health.json
    backend-features.json
    openapi.json
    rag-demo-seed.json
    rag-retrieval-evaluation.json
    retrieval-adapter-status.json
    retrieval-adapter-status.txt
    demo-routes.txt
```

## 记录模板

关键截图路径建议使用完整相对路径，例如 `screenshots/rag-pipeline.png`、`screenshots/retrieval-auto-search.png`、`screenshots/chat-with-citations.png`。

```markdown
# Evidence YYYY-MM-DD

- Commit: `<commit>`
- Frontend: `http://localhost:20001`
- Backend: `http://localhost:10001`
- Doc Parser: `http://localhost:9001`
- Scenario: `Seed -> Evaluate -> Retrieval -> Chat -> Evidence`
- Backend compile: passed
- Frontend build: passed
- Evidence package: `docs/evidence/YYYY-MM-DD/`
- Evidence dry-run: `./scripts/create-demo-evidence.sh --dry-run`
- Evidence collect dry-run: `./scripts/collect-demo-evidence.sh --dry-run`
- Doc-parser boundary: `./scripts/probe-doc-parser-boundary.sh --contract-only`
- Doc-parser lifecycle: `./scripts/check-doc-parser-lifecycle.sh`
- Doc-parser async smoke: `./scripts/smoke-doc-parser-async.sh`
- Production adapter profile: `./scripts/probe-production-adapter-profile.sh --dry-run`
- Production adapter profile output: `outputs/probe-production-adapter-profile.txt`
- RAG demo seed: `./scripts/seed-rag-demo.sh`
- RAG retrieval evaluation: `./scripts/evaluate-rag-retrieval.sh`
- RAG runtime probe: `./scripts/probe-rag-demo-runtime.sh`
- Adapter runtime status: `curl -fsS http://localhost:10001/api/retrieval/adapters/status`
- Adapter status JSON: `runtime/retrieval-adapter-status.json`
- Adapter status summary: `runtime/retrieval-adapter-status.txt`
- RAG demo smoke: `./scripts/smoke-rag-demo.sh`
- Chat citation trace: rank/source/scoreExplanation visible
- Chat context trace: assemblyStrategy/prompt sections/included chunks visible
- Backend probe: `./scripts/probe-backend-dev.sh`
- RAG upload/search/chat: passed
```

## 不提交内容

- 真实 API Key、Cookie、Token。
- 本地个人路径截图。
- 上传原始文件、构建产物、后端 target、前端 dist。
