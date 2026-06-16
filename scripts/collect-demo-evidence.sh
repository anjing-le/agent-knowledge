#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() {
  echo "collect-demo-evidence: $*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/collect-demo-evidence.sh [--date YYYY-MM-DD] [--force] [--dry-run]

Creates a dated evidence package and stores command/runtime outputs for the
RAG teaching demo under docs/evidence/YYYY-MM-DD/.

Options:
  --date YYYY-MM-DD          Evidence package date. Defaults to today.
  --force                    Allow replacing README.md in an existing package.
  --dry-run                  Print planned package and commands without writing.
  --backend-port PORT        Port for the temporary dev backend. Default: 10083.
  --backend-probe-port PORT  Port used by probe-backend-dev.sh. Default: backend-port + 1.
  --skip-backend-probe       Skip probe-backend-dev.sh.
  --skip-frontend-build      Skip pnpm build.
  --include-doc-parser-live  Run smoke-doc-parser-async.sh against a running doc-parser.
EOF
}

DATE_VALUE=""
FORCE=false
DRY_RUN=false
BACKEND_PORT="${EVIDENCE_BACKEND_PORT:-10083}"
BACKEND_PROBE_PORT=""
RUN_BACKEND_PROBE=true
RUN_FRONTEND_BUILD=true
RUN_DOC_PARSER_LIVE=false
BACKEND_ATTEMPTS="${EVIDENCE_BACKEND_ATTEMPTS:-90}"
BACKEND_PID=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --date)
      [[ $# -ge 2 ]] || fail "--date requires YYYY-MM-DD"
      DATE_VALUE="$2"
      shift 2
      ;;
    --force)
      FORCE=true
      shift
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --backend-port)
      [[ $# -ge 2 ]] || fail "--backend-port requires a port"
      BACKEND_PORT="$2"
      shift 2
      ;;
    --backend-probe-port)
      [[ $# -ge 2 ]] || fail "--backend-probe-port requires a port"
      BACKEND_PROBE_PORT="$2"
      shift 2
      ;;
    --skip-backend-probe)
      RUN_BACKEND_PROBE=false
      shift
      ;;
    --skip-frontend-build)
      RUN_FRONTEND_BUILD=false
      shift
      ;;
    --include-doc-parser-live)
      RUN_DOC_PARSER_LIVE=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      if [[ -z "$DATE_VALUE" ]]; then
        DATE_VALUE="$1"
        shift
      else
        fail "unexpected argument: $1"
      fi
      ;;
  esac
done

DATE_VALUE="${DATE_VALUE:-$(date +%F)}"
[[ "$DATE_VALUE" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] \
  || fail "date must match YYYY-MM-DD: $DATE_VALUE"
[[ "$BACKEND_PORT" =~ ^[0-9]+$ ]] || fail "backend port must be numeric: $BACKEND_PORT"

if [[ -z "$BACKEND_PROBE_PORT" ]]; then
  BACKEND_PROBE_PORT="$((BACKEND_PORT + 1))"
fi
[[ "$BACKEND_PROBE_PORT" =~ ^[0-9]+$ ]] \
  || fail "backend probe port must be numeric: $BACKEND_PROBE_PORT"

TARGET_DIR="docs/evidence/$DATE_VALUE"
OUTPUT_DIR="$TARGET_DIR/outputs"
RUNTIME_DIR="$TARGET_DIR/runtime"
TARGET_README="$TARGET_DIR/README.md"
BACKEND_BASE_URL="http://localhost:$BACKEND_PORT"
COMMIT="$(git rev-parse --short HEAD 2>/dev/null || true)"
COMMIT="${COMMIT:-unknown}"

# Runtime evidence files include runtime/demo-routes.txt,
# runtime/rag-demo-seed.json, runtime/rag-retrieval-evaluation.json,
# runtime/rag-evidence-report.json, runtime/rag-evidence-report.md,
# runtime/rag-citation-evidence.json, runtime/rag-citation-evidence.md
# and runtime/retrieval-adapter-status.json.

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is required"
}

for tool in bash curl node; do
  require_command "$tool"
done

planned_commands=(
  "./scripts/check-template.sh"
  "./scripts/check-contracts.sh"
  "./scripts/probe-doc-parser-boundary.sh --contract-only"
  "./scripts/check-doc-parser-lifecycle.sh"
  "./scripts/probe-production-adapter-profile.sh --dry-run"
  "./scripts/smoke-doc-parser-async.sh"
  "curl -fsS $BACKEND_BASE_URL/api/retrieval/adapters/status"
  "curl -fsS -X POST $BACKEND_BASE_URL/api/test/rag-demo/evidence-report"
  "BACKEND_BASE_URL=$BACKEND_BASE_URL ./scripts/seed-rag-demo.sh"
  "BACKEND_BASE_URL=$BACKEND_BASE_URL ./scripts/evaluate-rag-retrieval.sh"
  "./scripts/probe-rag-demo-runtime.sh"
  "./scripts/probe-rag-ingestion-runtime.sh"
  "./scripts/smoke-rag-demo.sh"
  "./scripts/probe-backend-dev.sh $BACKEND_PROBE_PORT"
  "(cd frontend && pnpm build)"
)

if [[ "$DRY_RUN" == "true" ]]; then
  ./scripts/create-demo-evidence.sh --date "$DATE_VALUE" --dry-run
  echo "collect-demo-evidence: dry-run"
  echo "collect-demo-evidence: target=$TARGET_DIR"
  echo "collect-demo-evidence: backend=$BACKEND_BASE_URL"
  echo "collect-demo-evidence: commit=$COMMIT"
  echo "collect-demo-evidence: runtime=runtime/rag-citation-evidence.json"
  echo "collect-demo-evidence: runtime=runtime/rag-citation-evidence.md"
  printf 'collect-demo-evidence: command=%s\n' "${planned_commands[@]}"
  exit 0
fi

create_args=(--date "$DATE_VALUE")
if [[ "$FORCE" == "true" ]]; then
  create_args+=(--force)
fi
./scripts/create-demo-evidence.sh "${create_args[@]}"

mkdir -p "$OUTPUT_DIR" "$RUNTIME_DIR"

run_to_file() {
  local output_file="$1"
  local command_text="$2"

  echo "collect-demo-evidence: run $command_text"
  {
    printf '$ %s\n' "$command_text"
    bash -lc "$command_text"
  } >"$output_file" 2>&1 || {
    echo "collect-demo-evidence: failed command: $command_text" >&2
    tail -n 120 "$output_file" >&2 || true
    exit 1
  }
}

write_skip_file() {
  local output_file="$1"
  local label="$2"
  local reason="$3"

  {
    echo "$label: skipped"
    echo "$label: reason=$reason"
  } >"$output_file"
}

stop_backend() {
  if [[ -n "${BACKEND_PID:-}" ]]; then
    kill "$BACKEND_PID" >/dev/null 2>&1 || true
    wait "$BACKEND_PID" >/dev/null 2>&1 || true
    BACKEND_PID=""
  fi
}

trap stop_backend EXIT

start_backend() {
  local log_file="$RUNTIME_DIR/backend-dev.log"
  local pid_file="$RUNTIME_DIR/backend-dev.pid"

  rm -f "$log_file" "$pid_file"
  (
    cd "$ROOT/backend"
    SPRING_PROFILES_ACTIVE=dev SERVER_PORT="$BACKEND_PORT" mvn -q spring-boot:run >"$ROOT/$log_file" 2>&1
  ) &
  BACKEND_PID="$!"
  echo "$BACKEND_PID" >"$pid_file"

  for _ in $(seq 1 "$BACKEND_ATTEMPTS"); do
    kill -0 "$BACKEND_PID" >/dev/null 2>&1 \
      || fail "backend process exited before health check passed; see $log_file"

    if curl -fsS "$BACKEND_BASE_URL/api/test/health" >"$RUNTIME_DIR/backend-health.json" 2>/dev/null; then
      curl -fsS "$BACKEND_BASE_URL/api/test/features" >"$RUNTIME_DIR/backend-features.json"
      curl -fsS "$BACKEND_BASE_URL/v3/api-docs" >"$RUNTIME_DIR/openapi.json"
      echo "collect-demo-evidence: backend ready $BACKEND_BASE_URL"
      return 0
    fi

    sleep 1
  done

  fail "backend did not pass health check in ${BACKEND_ATTEMPTS}s; see $log_file"
}

write_demo_routes() {
  local seed_json="$1"
  local output_file="$2"

  node - "$seed_json" >"$output_file" <<'NODE'
const fs = require('fs')
const [, , seedFile] = process.argv
const payload = JSON.parse(fs.readFileSync(seedFile, 'utf8'))
const data = payload.data || {}

console.log(`pipelineRoute=${data.pipelineRoute || ''}`)
console.log(`knowledgeRoute=${data.knowledgeRoute || ''}`)
console.log(`retrievalRoute=${data.retrievalRoute || ''}`)
console.log(`chatRoute=${data.chatRoute || ''}`)
console.log(`topChunkId=${data.topChunkId || ''}`)
console.log(`topScoreExplanation=${data.topScoreExplanation || ''}`)
NODE
}

write_adapter_status_summary() {
  local adapter_status_json="$1"
  local output_file="$2"

  node - "$adapter_status_json" >"$output_file" <<'NODE'
const fs = require('fs')
const [, , statusFile] = process.argv
const payload = JSON.parse(fs.readFileSync(statusFile, 'utf8'))
const data = payload.data || {}
const adapters = Array.isArray(data.adapters) ? data.adapters : []

console.log(`summary=${data.summary || ''}`)
for (const adapter of adapters) {
  console.log([
    adapter.axis || '',
    adapter.currentProvider || '',
    adapter.currentImplementation || '',
    adapter.runtimeStatus || ''
  ].join('='))
}
NODE
}

write_evidence_report_markdown() {
  local evidence_report_json="$1"
  local output_file="$2"

  node - "$evidence_report_json" >"$output_file" <<'NODE'
const fs = require('fs')
const [, , reportFile] = process.argv
const payload = JSON.parse(fs.readFileSync(reportFile, 'utf8'))
const data = payload.data || {}

console.log(data.markdown || '# agent-knowledge RAG Demo Evidence')
NODE
}

write_citation_evidence_json() {
  local evidence_report_json="$1"
  local output_file="$2"

  node - "$evidence_report_json" >"$output_file" <<'NODE'
const fs = require('fs')
const [, , reportFile] = process.argv
const payload = JSON.parse(fs.readFileSync(reportFile, 'utf8'))
const data = payload.data || {}

console.log(JSON.stringify(data.citationEvidence || {}, null, 2))
NODE
}

write_citation_evidence_markdown() {
  local evidence_report_json="$1"
  local output_file="$2"

  node - "$evidence_report_json" >"$output_file" <<'NODE'
const fs = require('fs')
const [, , reportFile] = process.argv
const payload = JSON.parse(fs.readFileSync(reportFile, 'utf8'))
const data = payload.data || {}
const evidence = data.citationEvidence || {}
const promptSections = Array.isArray(evidence.promptSections) ? evidence.promptSections : []
const chunks = Array.isArray(evidence.includedChunks) ? evidence.includedChunks : []
const references = Array.isArray(evidence.references) ? evidence.references : []
const score = (value) => typeof value === 'number' ? value.toFixed(4) : '-'
const line = (value) => value === undefined || value === null || value === '' ? '-' : value

const promptLines = promptSections.length
  ? promptSections.map((section) => `- ${section}`).join('\n')
  : '- none'
const chunkLines = chunks.length
  ? chunks.map((chunk) => [
      `- #${line(chunk.rank)} ${line(chunk.docName || chunk.docId)}`,
      `  - chunk: ${line(chunk.chunkId)}`,
      `  - source: ${line(chunk.retrievalSource)}`,
      `  - final: ${score(chunk.finalScore)}`,
      `  - score: ${line(chunk.scoreExplanation)}`
    ].join('\n')).join('\n')
  : '- none'
const referenceLines = references.length
  ? references.map((reference) => [
      `- #${line(reference.rank)} ${line(reference.docName || reference.docId)}`,
      `  - chunk: ${line(reference.chunkId)}`,
      `  - source: ${line(reference.retrievalSource)}`,
      `  - final: ${score(reference.finalScore)}`,
      `  - score: ${line(reference.scoreExplanation)}`
    ].join('\n')).join('\n')
  : '- none'

console.log([
  '# RAG Citation Evidence',
  '',
  '## Citation Inspector',
  `- Chat Question: ${line(evidence.chatQuestion)}`,
  `- Answer Preview: ${line(evidence.answerPreview)}`,
  `- Chat Route: ${line(evidence.chatRoute)}`,
  `- Strategy: ${line(evidence.assemblyStrategy)}`,
  `- Context Policy: ${line(evidence.contextWindowPolicy)}`,
  `- References: ${line(evidence.referenceCount ?? references.length)}`,
  `- Included Chunks: ${line(evidence.includedChunkCount ?? chunks.length)}`,
  `- Prompt Chars: ${line(evidence.promptCharCount)}`,
  `- Context Chars: ${line(evidence.contextCharCount)}`,
  '',
  '## Prompt Sections',
  promptLines,
  '',
  '## Context Chunks',
  chunkLines,
  '',
  '## Citation Cards',
  referenceLines
].join('\n'))
NODE
}

update_readme_results() {
  node - "$TARGET_README" "$RUN_DOC_PARSER_LIVE" "$RUN_FRONTEND_BUILD" "$RUN_BACKEND_PROBE" <<'NODE'
const fs = require('fs')
const [, , readmeFile, docParserLive, frontendBuild, backendProbe] = process.argv
let source = fs.readFileSync(readmeFile, 'utf8')

const replacements = new Map([
  ['- RAG demo seed: pending', '- RAG demo seed: captured in `outputs/seed-rag-demo.txt`'],
  ['- Doc-parser boundary probe: pending', '- Doc-parser boundary probe: captured in `outputs/probe-doc-parser-boundary.txt`'],
  ['- Doc-parser lifecycle mapping: pending', '- Doc-parser lifecycle mapping: captured in `outputs/check-doc-parser-lifecycle.txt`'],
  ['- Production adapter profile probe: pending', '- Production adapter profile probe: captured in `outputs/probe-production-adapter-profile.txt`'],
  [
    '- Doc-parser async smoke: pending',
    docParserLive === 'true'
      ? '- Doc-parser async smoke: captured in `outputs/smoke-doc-parser-async.txt`'
      : '- Doc-parser async smoke: skipped; pass `--include-doc-parser-live` when doc-parser is running'
  ],
  ['- Retrieval route: pending', '- Retrieval route: captured in `runtime/demo-routes.txt`'],
  ['- Retrieval evaluation: pending', '- Retrieval evaluation: captured in `outputs/evaluate-rag-retrieval.txt` and `runtime/rag-retrieval-evaluation.json`'],
  ['- Backend evidence report: pending', '- Backend evidence report: captured in `runtime/rag-evidence-report.json` and `runtime/rag-evidence-report.md`'],
  ['- Adapter runtime status: pending', '- Adapter runtime status: captured in `runtime/retrieval-adapter-status.json` and `runtime/retrieval-adapter-status.txt`'],
  ['- Chat route: pending', '- Chat route: captured in `runtime/demo-routes.txt`'],
  ['- Chat citation trace: pending', '- Chat citation trace: captured in `runtime/rag-citation-evidence.json` and `runtime/rag-citation-evidence.md`'],
  ['- Chat context trace: pending', '- Chat context trace: captured in `runtime/rag-citation-evidence.md`'],
  ['- RAG demo smoke: pending', '- RAG demo smoke: captured in `outputs/smoke-rag-demo.txt`'],
  [
    '- Backend probe: pending',
    backendProbe === 'true'
      ? '- Backend probe: captured in `outputs/probe-backend-dev.txt`'
      : '- Backend probe: skipped by `--skip-backend-probe`'
  ],
  [
    '- Frontend build: pending',
    frontendBuild === 'true'
      ? '- Frontend build: captured in `outputs/frontend-build.txt`'
      : '- Frontend build: skipped by `--skip-frontend-build`'
  ]
])

for (const [from, to] of replacements) {
  source = source.replace(from, to)
}

fs.writeFileSync(readmeFile, source)
NODE
}

run_to_file "$OUTPUT_DIR/check-template.txt" "./scripts/check-template.sh"
run_to_file "$OUTPUT_DIR/check-contracts.txt" "./scripts/check-contracts.sh"
run_to_file "$OUTPUT_DIR/probe-doc-parser-boundary.txt" "./scripts/probe-doc-parser-boundary.sh --contract-only"
run_to_file "$OUTPUT_DIR/check-doc-parser-lifecycle.txt" "./scripts/check-doc-parser-lifecycle.sh"
run_to_file "$OUTPUT_DIR/probe-production-adapter-profile.txt" \
  "./scripts/probe-production-adapter-profile.sh --dry-run"

if [[ "$RUN_DOC_PARSER_LIVE" == "true" ]]; then
  run_to_file "$OUTPUT_DIR/smoke-doc-parser-async.txt" "./scripts/smoke-doc-parser-async.sh"
else
  write_skip_file "$OUTPUT_DIR/smoke-doc-parser-async.txt" \
    "smoke-doc-parser-async" \
    "doc-parser live smoke is optional; pass --include-doc-parser-live when the Python service is running"
fi

start_backend

run_to_file "$OUTPUT_DIR/seed-rag-demo.txt" \
  "BACKEND_BASE_URL='$BACKEND_BASE_URL' ./scripts/seed-rag-demo.sh"
run_to_file "$OUTPUT_DIR/evaluate-rag-retrieval.txt" \
  "BACKEND_BASE_URL='$BACKEND_BASE_URL' ./scripts/evaluate-rag-retrieval.sh"

curl -fsS -X POST "$BACKEND_BASE_URL/api/test/rag-demo/seed" \
  -H 'Content-Type: application/json' >"$RUNTIME_DIR/rag-demo-seed.json"
curl -fsS -X POST "$BACKEND_BASE_URL/api/test/rag-demo/evaluate-retrieval" \
  -H 'Content-Type: application/json' >"$RUNTIME_DIR/rag-retrieval-evaluation.json"
curl -fsS -X POST "$BACKEND_BASE_URL/api/test/rag-demo/evidence-report" \
  -H 'Content-Type: application/json' >"$RUNTIME_DIR/rag-evidence-report.json"
curl -fsS "$BACKEND_BASE_URL/api/retrieval/adapters/status" \
  >"$RUNTIME_DIR/retrieval-adapter-status.json"
write_demo_routes "$RUNTIME_DIR/rag-demo-seed.json" "$RUNTIME_DIR/demo-routes.txt"
write_adapter_status_summary \
  "$RUNTIME_DIR/retrieval-adapter-status.json" \
  "$RUNTIME_DIR/retrieval-adapter-status.txt"
write_evidence_report_markdown \
  "$RUNTIME_DIR/rag-evidence-report.json" \
  "$RUNTIME_DIR/rag-evidence-report.md"
write_citation_evidence_json \
  "$RUNTIME_DIR/rag-evidence-report.json" \
  "$RUNTIME_DIR/rag-citation-evidence.json"
write_citation_evidence_markdown \
  "$RUNTIME_DIR/rag-evidence-report.json" \
  "$RUNTIME_DIR/rag-citation-evidence.md"

stop_backend

run_to_file "$OUTPUT_DIR/probe-rag-demo-runtime.txt" "./scripts/probe-rag-demo-runtime.sh"
run_to_file "$OUTPUT_DIR/probe-rag-ingestion-runtime.txt" "./scripts/probe-rag-ingestion-runtime.sh"
run_to_file "$OUTPUT_DIR/smoke-rag-demo.txt" "./scripts/smoke-rag-demo.sh"

if [[ "$RUN_BACKEND_PROBE" == "true" ]]; then
  run_to_file "$OUTPUT_DIR/probe-backend-dev.txt" "./scripts/probe-backend-dev.sh '$BACKEND_PROBE_PORT'"
else
  write_skip_file "$OUTPUT_DIR/probe-backend-dev.txt" \
    "probe-backend-dev" \
    "skipped by --skip-backend-probe"
fi

if [[ "$RUN_FRONTEND_BUILD" == "true" ]]; then
  run_to_file "$OUTPUT_DIR/frontend-build.txt" "(cd frontend && pnpm build)"
else
  write_skip_file "$OUTPUT_DIR/frontend-build.txt" \
    "frontend-build" \
    "skipped by --skip-frontend-build"
fi

{
  echo "date=$DATE_VALUE"
  echo "commit=$COMMIT"
  echo "backend=$BACKEND_BASE_URL"
  echo "evidenceReport=$BACKEND_BASE_URL/api/test/rag-demo/evidence-report"
  echo "citationEvidence=$TARGET_DIR/runtime/rag-citation-evidence.md"
  echo "adapterStatus=$BACKEND_BASE_URL/api/retrieval/adapters/status"
  echo "docParserLive=$RUN_DOC_PARSER_LIVE"
  echo "frontendBuild=$RUN_FRONTEND_BUILD"
  echo "backendProbe=$RUN_BACKEND_PROBE"
  echo "package=$TARGET_DIR"
} >"$RUNTIME_DIR/summary.txt"

update_readme_results

echo "collect-demo-evidence: ok"
echo "collect-demo-evidence: target=$TARGET_DIR"
