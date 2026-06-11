# Agent Knowledge

RAG 智能知识库示例：文档上传、Python 解析、切片、Embedding、检索、上下文组装、LLM 回答和答案引用。

## 结构

```text
backend/          Spring Boot 后端：知识库、文档、检索、聊天
frontend/         Vue 3 前端：知识库管理和问答界面
doc-parser/       Python FastAPI 文档解析服务
contracts/        平台契约、服务边界、doc-parser 契约
project_document/ 架构、路线图、质量规范
```

`doc-parser` 是独立 Python 服务，Java 后端只通过 HTTP 调用它，不把解析能力并入 Java。

## 本地启动

```bash
# doc-parser
cd doc-parser
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python -m uvicorn kparser.app:app --host 0.0.0.0 --port 9001
```

```bash
# backend
cd backend
cp .env.example .env
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

```bash
# frontend
cd frontend
pnpm install
pnpm dev
```

默认端口：

- Frontend: `http://localhost:10086`
- Backend: `http://localhost:10001`
- Doc Parser: `http://localhost:9001`

## 验证

```bash
./scripts/check-template.sh
./scripts/check-contracts.sh
(cd backend && mvn -q -DskipTests compile)
(cd frontend && pnpm build)
```

## 文档

- [project_document/ROADMAP.md](./project_document/ROADMAP.md)
- [project_document/SERVICE_BOUNDARY_GUIDE.md](./project_document/SERVICE_BOUNDARY_GUIDE.md)
- [project_document/DOC_PARSER_SERVICE_GUIDE.md](./project_document/DOC_PARSER_SERVICE_GUIDE.md)
- [project_document/API_CONTRACT_GUIDE.md](./project_document/API_CONTRACT_GUIDE.md)
- [contracts/doc-parser-contract.json](./contracts/doc-parser-contract.json)

## License

MIT
