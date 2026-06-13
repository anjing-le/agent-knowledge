# Local Startup Guide

## 端口

| 服务 | 默认端口 |
|------|----------|
| Java backend | `10001` |
| Python doc-parser | `9001` |
| Vue frontend | `20001` |

## doc-parser

```bash
cd doc-parser
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python -m uvicorn kparser.app:app --host 0.0.0.0 --port 9001
```

验证：

```bash
curl -fsS http://localhost:9001/health
```

## backend

默认 profile 是 `dev`，使用 H2 in-memory、memory cache、local lock 和 `local-demo` Embedding/LLM provider，不要求本机 MySQL/Redis 或模型 API Key。

```bash
cd backend
mvn spring-boot:run
```

验证：

```bash
curl -fsS http://localhost:10001/api/test/health
curl -fsS http://localhost:10001/api/test/features
curl -fsS http://localhost:10001/v3/api-docs
```

需要真实 MySQL 和模型 Key 联调时，复制 `backend/src/main/resources/application-local.yml.example` 为 ignored 的 `application-local.yml`，再显式使用 `SPRING_PROFILES_ACTIVE=local`。

## frontend

```bash
cd frontend
pnpm install
pnpm dev
```

开发环境 `/api` 通过 `frontend/.env.development` 中的 `VITE_API_PROXY_URL=http://localhost:10001` 转发。

## 最小构建验证

```bash
(cd backend && mvn -q -DskipTests compile)
(cd frontend && pnpm build)
```
