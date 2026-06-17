# Evidence 2026-06-17

- Commit: `f766cf1`
- Frontend: `http://localhost:20001`
- Backend: `http://localhost:10001`
- Doc Parser: `http://localhost:9001`
- Scenario: `Seed -> Evaluate -> Retrieval -> Chat -> Evidence`
- Package path: `docs/evidence/YYYY-MM-DD/`

## Commands

```bash
./scripts/check-template.sh
./scripts/check-contracts.sh
./scripts/create-demo-evidence.sh --dry-run
./scripts/collect-demo-evidence.sh --dry-run
./scripts/probe-doc-parser-boundary.sh --contract-only
DOC_PARSER_URL=http://localhost:9001 BACKEND_URL=http://localhost:10001 ./scripts/probe-doc-parser-boundary.sh --live
./scripts/check-doc-parser-lifecycle.sh
./scripts/probe-production-adapter-profile.sh --dry-run
./scripts/smoke-doc-parser-async.sh
curl -fsS http://localhost:10001/api/retrieval/adapters/status
curl -fsS -X POST http://localhost:10001/api/test/rag-demo/evidence-report
./scripts/seed-rag-demo.sh
./scripts/evaluate-rag-retrieval.sh
./scripts/probe-rag-demo-runtime.sh
./scripts/probe-rag-ingestion-runtime.sh
./scripts/smoke-rag-demo.sh
./scripts/probe-backend-dev.sh
(cd frontend && pnpm build)
```

## Runtime Results

- RAG demo seed: captured in `outputs/seed-rag-demo.txt`
- Doc-parser boundary probe: captured in `outputs/probe-doc-parser-boundary.txt`
- Doc-parser live boundary probe: captured in `outputs/probe-doc-parser-boundary-live.txt`
- Doc-parser lifecycle mapping: captured in `outputs/check-doc-parser-lifecycle.txt`
- Production adapter profile probe: captured in `outputs/probe-production-adapter-profile.txt`
- Doc-parser async smoke: captured in `outputs/smoke-doc-parser-async.txt`
- Retrieval route: captured in `runtime/demo-routes.txt`
- Retrieval evaluation: captured in `outputs/evaluate-rag-retrieval.txt` and `runtime/rag-retrieval-evaluation.json`
- Backend evidence report: captured in `runtime/rag-evidence-report.json` and `runtime/rag-evidence-report.md`
- RAG runtime probe: captured in `outputs/probe-rag-demo-runtime.txt`
- RAG ingestion runtime probe: captured in `outputs/probe-rag-ingestion-runtime.txt`
- Adapter runtime status: captured in `runtime/retrieval-adapter-status.json` and `runtime/retrieval-adapter-status.txt`
- Chat route: captured in `runtime/demo-routes.txt`
- Chat citation trace: captured in `runtime/rag-citation-evidence.json` and `runtime/rag-citation-evidence.md`
- Chat context trace: captured in `runtime/rag-citation-evidence.md`
- RAG demo smoke: captured in `outputs/smoke-rag-demo.txt`
- Backend probe: captured in `outputs/probe-backend-dev.txt`
- Frontend build: captured in `outputs/frontend-build.txt`

## Screenshots

- `screenshots/rag-pipeline.png`: Demo Ready checklist and retrieval evaluation panel after seed.
- `screenshots/retrieval-auto-search.png`: retrieval page with auto query and chunk hits.
- `screenshots/chat-with-citations.png`: chat answer with context assembly trace, citations, rank/source trace and score explanation.

## Output Files

- `outputs/check-template.txt`
- `outputs/check-contracts.txt`
- `outputs/probe-doc-parser-boundary.txt`
- `outputs/probe-doc-parser-boundary-live.txt`
- `outputs/check-doc-parser-lifecycle.txt`
- `outputs/probe-production-adapter-profile.txt`
- `outputs/smoke-doc-parser-async.txt`
- `outputs/seed-rag-demo.txt`
- `outputs/evaluate-rag-retrieval.txt`
- `outputs/probe-rag-demo-runtime.txt`
- `outputs/probe-rag-ingestion-runtime.txt`
- `outputs/smoke-rag-demo.txt`
- `outputs/probe-backend-dev.txt`
- `outputs/frontend-build.txt`
- `runtime/summary.txt`
- `runtime/backend-health.json`
- `runtime/doc-parser-health.json`
- `runtime/backend-features.json`
- `runtime/openapi.json`
- `runtime/rag-demo-seed.json`
- `runtime/rag-retrieval-evaluation.json`
- `runtime/rag-evidence-report.json`
- `runtime/rag-evidence-report.md`
- `runtime/rag-citation-evidence.json`
- `runtime/rag-citation-evidence.md`
- `runtime/retrieval-adapter-status.json`
- `runtime/retrieval-adapter-status.txt`
- `runtime/demo-routes.txt`

## Notes

- Do not include API keys, cookies, tokens, personal paths, or private uploaded files.
- Keep the evidence focused on scaffold alignment and the RAG demo path.
- Use `./scripts/collect-demo-evidence.sh --date YYYY-MM-DD --force` to collect command output and runtime JSON in one pass.
