# Demo Evidence

This directory stores reusable evidence package templates for the RAG teaching demo.

Evidence packages should be created per demo date:

```text
docs/evidence/YYYY-MM-DD/
  README.md
  screenshots/
  outputs/
  runtime/
```

Create a new package:

```bash
./scripts/create-demo-evidence.sh --date YYYY-MM-DD
```

Collect command output and runtime JSON into a new package:

```bash
./scripts/collect-demo-evidence.sh --date YYYY-MM-DD --force
```

Preview without writing files:

```bash
./scripts/create-demo-evidence.sh --dry-run
./scripts/collect-demo-evidence.sh --dry-run
```

Rules:

- Keep API keys, cookies, tokens, personal paths, and uploaded private files out of evidence.
- Prefer command output text files and focused screenshots that prove the RAG path.
- Capture `runtime/retrieval-adapter-status.json` to prove the current VectorStore, KeywordSearch, Rerank and doc-parser adapter choices.
- Capture `runtime/rag-citation-evidence.json` and `runtime/rag-citation-evidence.md` to prove answer citations, context chunks, prompt sections and score explanations.
- Use `--include-doc-parser-live` when the package should include async smoke output from the temporary Python doc-parser.
- Do not overwrite an existing evidence package unless you intentionally pass `--force`.
