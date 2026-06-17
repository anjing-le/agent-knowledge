# V1 Teaching Baseline Release

本文件用于标记 agent-knowledge 的 V1 teaching baseline：它是基于 `infra-dev-scaffolding` 生长出来的 RAG agent 教学样板，而不是生产级 RAG 平台。

## Release Scope

V1 已收敛到以下目标：

- 脚手架继承关系清楚：Vue/Vite、Spring Boot/Java 17、统一响应、分页、OpenAPI、请求上下文、质量门禁和服务边界沿用 `infra-dev-scaffolding`。
- RAG 主链路清楚：文档上传、Python doc-parser、切片、Embedding、混合检索、上下文组装、LLM 回答和答案引用。
- Java/Python 边界清楚：Java 后端只通过 HTTP contract 调用独立 Python FastAPI doc-parser。
- 教学页面清楚：Pipeline、Knowledge、Retrieval、Chat 能串起 `upload/seed -> chunk -> retrieval score -> context trace -> citation`。
- 证据包清楚：`docs/evidence/2026-06-17/` 已包含运行输出、runtime JSON、citation evidence 和三张前端截图。
- CI 门禁清楚：`.github/workflows/quality-gate.yml` 已在 `main/master` push 和 PR 上执行同一套 `./scripts/quality-gate.sh`。

## Verification

封版前已通过的关键入口：

```bash
./scripts/check-teaching-handoff.sh
./scripts/quality-gate.sh
./scripts/collect-demo-evidence.sh --date 2026-06-17 --force --include-doc-parser-live
node scripts/check-final-readiness.js
node scripts/check-scaffold-alignment.js
gh run list --limit 2 --json headSha,status,conclusion,workflowName,url
```

证据落点：

```text
docs/evidence/2026-06-17/
  outputs/
  runtime/
  screenshots/rag-pipeline.png
  screenshots/retrieval-auto-search.png
  screenshots/chat-with-citations.png
```

## Release Tag

建议标签：

```bash
git tag -a v1-teaching-baseline -m "V1 teaching baseline"
git push origin v1-teaching-baseline
```

## Not In V1

以下能力属于 V2/V3 扩展，不阻塞 V1 封版：

- Milvus、生产队列、网关、多租户、计费和复杂权限。
- 真实外部 Embedding/LLM/Rerank provider 的线上压测。
- 页码、表格、图片、坐标级引用定位。
- 多知识库路由、query rewrite 和 answer quality panel。
- GitHub Actions 证据归档和 release artifact 自动上传。

## V1.1 Teaching Handoff

V1.1 在 V1 baseline 上补齐课堂交付体验：

- Pipeline 第一屏提供 `Runbook`、`Quality Gate`、`Evidence` 和 `Baseline` 快捷入口。
- `Classroom Command Pack` 可复制课前检查、三服务启动、seed/evaluate、边界探针和证据包命令。
- `./scripts/check-teaching-handoff.sh` 可在课前一次确认作者、远端分支、CI、证据包、baseline tag 和课堂命令。
- `V1.1 Readiness` 按 96% 展示：核心链路、CI、证据包和课堂 Runbook 已闭环，剩余主要是现场排练。
- 最近一次证据包刷新落在 `docs/evidence/2026-06-17/`，证明本地 demo、doc-parser live boundary、adapter status、引用证据和前端构建可复现。

## Teaching Narrative

讲解时按这条线走：

```text
infra-dev-scaffolding 工程底座
-> agent-knowledge RAG 业务设计
-> doc-parser 独立服务边界
-> Retrieval adapter 替换轴
-> Evidence Chain 页面
-> quality-gate 和 evidence package
```

这样学习者只需要关注 RAG agent 的业务设计，底层技术栈、工程习惯和最佳实践都来自脚手架。
