# Evidence YYYY-MM-DD

- Commit: `<commit>`
- Frontend: `http://localhost:20001`
- Backend: `http://localhost:10001`
- Doc Parser: `http://localhost:9001`
- Scenario: `Seed -> Retrieval -> Chat -> Evidence`
- Package path: `docs/evidence/YYYY-MM-DD/`

## Commands

```bash
./scripts/check-template.sh
./scripts/check-contracts.sh
./scripts/create-demo-evidence.sh --dry-run
./scripts/probe-doc-parser-boundary.sh --contract-only
./scripts/check-doc-parser-lifecycle.sh
./scripts/seed-rag-demo.sh
./scripts/smoke-rag-demo.sh
./scripts/probe-backend-dev.sh
(cd frontend && pnpm build)
```

## Runtime Results

- RAG demo seed: pending
- Doc-parser boundary probe: pending
- Doc-parser lifecycle mapping: pending
- Retrieval route: pending
- Chat route: pending
- RAG demo smoke: pending
- Backend probe: pending
- Frontend build: pending

## Screenshots

- `screenshots/rag-pipeline.png`: Demo Ready checklist after seed.
- `screenshots/retrieval-auto-search.png`: retrieval page with auto query and chunk hits.
- `screenshots/chat-with-citations.png`: chat answer with citations.

## Output Files

- `outputs/check-template.txt`
- `outputs/check-contracts.txt`
- `outputs/probe-doc-parser-boundary.txt`
- `outputs/check-doc-parser-lifecycle.txt`
- `outputs/seed-rag-demo.txt`
- `outputs/smoke-rag-demo.txt`
- `outputs/probe-backend-dev.txt`
- `outputs/frontend-build.txt`

## Notes

- Do not include API keys, cookies, tokens, personal paths, or private uploaded files.
- Keep the evidence focused on scaffold alignment and the RAG demo path.
