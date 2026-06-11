# Local Startup Guide

## 端口

| 服务 | 默认端口 |
|------|----------|
| Java backend | `10001` |
| Python doc-parser | `9001` |
| Vue frontend | `20001` / Vite 实际输出端口 |

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

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

验证：

```bash
curl -fsS http://localhost:10001/api/test/health
curl -fsS http://localhost:10001/api/test/features
curl -fsS http://localhost:10001/v3/api-docs
```

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
