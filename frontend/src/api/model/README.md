# API Model 说明

agent-knowledge 的前端 API 以脚手架契约为准：运行接口优先使用 OpenAPI 生成类型和 `openApiRequest`，尚未接入 OpenAPI 的少量接口才补充手写 Model。

## 当前文件

| 文件 | 用途 |
| --- | --- |
| `authModel.ts` | 登录页和用户态的手写兼容类型 |

知识库、文档、切片、检索、聊天等 RAG 运行接口的请求和响应类型，优先来自：

```text
frontend/src/contracts/openapi/schemas.ts
frontend/src/contracts/openapi/operations.ts
```

## 编写原则

1. API 路径不得在页面或 API 模块里手写 `"/api/..."`。
2. 已进入 OpenAPI 的接口使用 `openApiRequest(operationId)`。
3. 暂未接入 OpenAPI 的接口使用 `ApiPaths`，路径来源为 `contracts/service-boundaries.json`。
4. 手写 Model 只描述前端视图状态、兼容字段或组合类型，不复制后端 OpenAPI schema。
5. 新增运行接口时，先更新 service boundary / OpenAPI 契约，再生成前端类型。

## 示例

```typescript
import { openApiRequest } from '@/api/openapiClient'
import type { SearchRequest } from '@/contracts/openapi/schemas'

export function searchKnowledge(data: SearchRequest) {
  return openApiRequest('search', { body: data })
}
```

如确实需要补充前端专属类型：

```typescript
/** 检索页本地筛选状态，不属于后端契约 */
export interface RetrievalViewFilter {
  keyword: string
  enabledOnly: boolean
}
```

## 校验

修改 API 或 Model 后至少运行：

```bash
node scripts/check-frontend-api-boundaries.js
node scripts/check-service-boundaries.js
(cd frontend && pnpm build)
```
