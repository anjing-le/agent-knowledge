# agent-knowledge RAG Demo Evidence

## Scaffold Stack
- Frontend: Vue 3 + TypeScript + Vite + Element Plus
- Backend: Spring Boot + Java 17 + OpenAPI contract
- Doc Parser: Python service over HTTP
- Contract: APIResponse / PageResult / ApiConstants / ApiPaths

## Demo Run
- KB: RAG Demo Teaching KB
- Document: scaffold-rag-demo.md
- Vectors: 3
- Hits: 3
- Retrieval Query: 脚手架如何生长成 RAG agent，并保留工程最佳实践？

## Retrieval Evaluation
- Suite: rag-demo-retrieval-v1
- Recall@K: 100%
- Cases: 3/3
- Passed: yes

## Runtime Adapter Status
Runtime provider snapshot for scaffold-grown RAG adapters.
- vectorStore: memory / default
- keywordSearch: local / default
- rerank: local-demo / default
- docParser: sync / default

## Ingestion Boundary
- Upload API: POST /api/knowledge/bases/{kbId}/documents
- Java: DocumentProcessingTask / DocumentProcessingProgressService / DocParserClient
- Python: agent-doc-parser Python service /parse
- Parser Contract: contracts/doc-parser-contract.json
- Probe: ./scripts/probe-rag-ingestion-runtime.sh

## Citation Inspector
- Chat Question: 脚手架到 RAG agent 的教学主线是什么？
- Answer Preview: 本地演示回答：围绕「脚手架到 RAG agent 的教学主线是什么？」，可以先根据当前检索结果这样理解。 1. infra-dev-scaffolding 提供 Spring Boot、统一响应 APIResponse、分页 PageResult、OpenAPI、请求上下文、RemoteHttpClient 和质量门禁。agent-knowledge 只在这...
- Strategy: retrieval-context-to-system-prompt
- Context Policy: topK retrieval results, latest chat history window
- References: 3
- Included Chunks: 3
- Prompt Chars: 1028
- Chat Route: /kb/chat?q=%E8%84%9A%E6%89%8B%E6%9E%B6%E5%88%B0+RAG+agent+%E7%9A%84%E6%95%99%E5%AD%A6%E4%B8%BB%E7%BA%BF%E6%98%AF%E4%BB%80%E4%B9%88%EF%BC%9F&kbIds=kb_rag_demo_teaching&source=retrieval&autoSend=1

### Prompt Sections
- core_principles
- answer_format
- knowledge_context
- citation_reminder

### Context Chunks
- #1 scaffold-rag-demo.md / chunk=chunk_rag_demo_teaching_001 / source=vector / final=0.6708
  - score: rank=1 final=0.6708 similarity=0.6708 keyword=disabled hybrid=disabled rerank=disabled threshold=0.3000
- #2 scaffold-rag-demo.md / chunk=chunk_rag_demo_teaching_002 / source=vector / final=0.5778
  - score: rank=2 final=0.5778 similarity=0.5778 keyword=disabled hybrid=disabled rerank=disabled threshold=0.3000
- #3 scaffold-rag-demo.md / chunk=chunk_rag_demo_teaching_003 / source=vector / final=0.5743
  - score: rank=3 final=0.5743 similarity=0.5743 keyword=disabled hybrid=disabled rerank=disabled threshold=0.3000

### Citation Cards
- #1 scaffold-rag-demo.md / chunk=chunk_rag_demo_teaching_001 / source=vector / final=0.6708
  - score: rank=1 final=0.6708 similarity=0.6708 keyword=disabled hybrid=disabled rerank=disabled threshold=0.3000
- #2 scaffold-rag-demo.md / chunk=chunk_rag_demo_teaching_002 / source=vector / final=0.5778
  - score: rank=2 final=0.5778 similarity=0.5778 keyword=disabled hybrid=disabled rerank=disabled threshold=0.3000
- #3 scaffold-rag-demo.md / chunk=chunk_rag_demo_teaching_003 / source=vector / final=0.5743
  - score: rank=3 final=0.5743 similarity=0.5743 keyword=disabled hybrid=disabled rerank=disabled threshold=0.3000

## Evidence Commands
- `curl -fsS -X POST http://localhost:10001/api/test/rag-demo/evidence-report`
- `./scripts/create-demo-evidence.sh --dry-run`
- `./scripts/collect-demo-evidence.sh --dry-run`
- `./scripts/evaluate-rag-retrieval.sh`
- `./scripts/probe-rag-demo-runtime.sh`
- `./scripts/probe-rag-ingestion-runtime.sh`
- `./scripts/probe-doc-parser-boundary.sh --contract-only`
- `./scripts/probe-retrieval-adapters.sh --dry-run`
- `./scripts/probe-production-adapter-profile.sh --dry-run`
- `curl -fsS http://localhost:10001/api/retrieval/adapters/status`
- `./scripts/check-doc-parser-lifecycle.sh`
- `./scripts/smoke-doc-parser-async.sh`
- `./scripts/seed-rag-demo.sh`
- `./scripts/smoke-rag-demo.sh`
- `./scripts/check-template.sh`
- `./scripts/check-contracts.sh`
