# Demo Evidence

本文档记录 agent-knowledge 演示或发布前需要保留的证据。

## 发布前证据清单

1. 后端编译通过：`(cd backend && mvn -q -DskipTests compile)`。
2. 前端构建通过：`(cd frontend && pnpm build)`。
3. doc-parser 健康检查通过：`curl http://localhost:9001/health`。
4. 后端健康检查通过：`curl http://localhost:10001/api/test/health`。
5. 前端能打开知识库列表、知识库详情、切片页和智能对话页。
6. 上传一份小文档，状态进入完成，切片可查看。
7. 在聊天页选择知识库提问，回答展示引用来源。

## 建议目录

```text
docs/evidence/YYYY-MM-DD/
  README.md
  login-desktop.png
  knowledge-list.png
  document-detail.png
  chunks.png
  chat-with-citations.png
  backend-compile.txt
  frontend-build.txt
  backend-probe.txt
  doc-parser-health.json
```

## 记录模板

```markdown
# Evidence YYYY-MM-DD

- Commit: `<commit>`
- Frontend: `http://localhost:20001`
- Backend: `http://localhost:10001`
- Doc Parser: `http://localhost:9001`
- Backend compile: passed
- Frontend build: passed
- Backend probe: `./scripts/probe-backend-dev.sh`
- RAG upload/search/chat: passed
```

## 不提交内容

- 真实 API Key、Cookie、Token。
- 本地个人路径截图。
- 上传原始文件、构建产物、后端 target、前端 dist。
