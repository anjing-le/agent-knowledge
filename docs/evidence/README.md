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
- Use `--include-doc-parser-live` only when the Python doc-parser service is running locally.
- Do not overwrite an existing evidence package unless you intentionally pass `--force`.
