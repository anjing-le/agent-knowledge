#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DOC_PARSER_URL="${DOC_PARSER_URL:-http://localhost:9001}"
MAX_ATTEMPTS="${DOC_PARSER_ASYNC_SMOKE_MAX_ATTEMPTS:-20}"
POLL_INTERVAL_SECONDS="${DOC_PARSER_ASYNC_SMOKE_POLL_INTERVAL_SECONDS:-1}"

fail() {
  echo "smoke-doc-parser-async: $*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/smoke-doc-parser-async.sh

Submits a small text document to doc-parser /loader/deep_parse/async and polls
/loader/status until SUCCEEDED. The doc-parser service must already be running.

Environment:
  DOC_PARSER_URL                              Defaults to http://localhost:9001
  DOC_PARSER_ASYNC_SMOKE_MAX_ATTEMPTS        Defaults to 20
  DOC_PARSER_ASYNC_SMOKE_POLL_INTERVAL_SECONDS Defaults to 1
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi
[[ $# -eq 0 ]] || fail "unexpected argument: $1"

command -v curl >/dev/null 2>&1 || fail "curl is required"
command -v node >/dev/null 2>&1 || fail "node is required"

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/agent-knowledge-doc-parser-async.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

SAMPLE_FILE="$TMP_DIR/agent-knowledge-async-smoke.txt"
SUBMIT_RESPONSE="$TMP_DIR/submit.json"
STATUS_RESPONSE="$TMP_DIR/status.json"
TASK_ID="parser_smoke_$(date +%Y%m%d%H%M%S)_$$"

cat >"$SAMPLE_FILE" <<'EOF'
agent-knowledge grows from infra-dev-scaffolding.
This smoke verifies async doc-parser submit, status polling, and RAG-shaped chunks.
EOF

curl -fsS "$DOC_PARSER_URL/health" >/dev/null \
  || fail "doc-parser health check failed: $DOC_PARSER_URL/health"

curl -fsS -X POST "$DOC_PARSER_URL/loader/deep_parse/async" \
  -F "task_id=$TASK_ID" \
  -F "doc_type=PLAIN_TEXT" \
  -F "metadata={\"docId\":\"doc_async_smoke\",\"kbId\":\"kb_async_smoke\",\"requestId\":\"$TASK_ID\"}" \
  -F "file=@$SAMPLE_FILE;filename=agent-knowledge-async-smoke.txt;type=text/plain" \
  >"$SUBMIT_RESPONSE" \
  || fail "async submit failed: $DOC_PARSER_URL/loader/deep_parse/async"

TASK_ID="$(
  node - "$SUBMIT_RESPONSE" <<'NODE'
const fs = require('fs')
const file = process.argv[2]
const response = JSON.parse(fs.readFileSync(file, 'utf8'))
if (response.success !== true) {
  console.error(response.error || response.message || 'submit response is not successful')
  process.exit(1)
}
if (!response.task_id) {
  console.error('submit response is missing task_id')
  process.exit(1)
}
if (!['PENDING', 'RUNNING'].includes(response.status)) {
  console.error(`submit status must be PENDING or RUNNING, got ${response.status}`)
  process.exit(1)
}
console.log(response.task_id)
NODE
)" || fail "invalid async submit response"

echo "smoke-doc-parser-async: submitted task_id=$TASK_ID"

for ((attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)); do
  curl -fsS -X POST "$DOC_PARSER_URL/loader/status" \
    -H 'Content-Type: application/json' \
    -d "{\"task_id\":\"$TASK_ID\"}" \
    >"$STATUS_RESPONSE" \
    || fail "async status request failed: $DOC_PARSER_URL/loader/status"

  STATUS="$(
    node - "$STATUS_RESPONSE" <<'NODE'
const fs = require('fs')
const file = process.argv[2]
const response = JSON.parse(fs.readFileSync(file, 'utf8'))
if (response.success !== true) {
  console.error(response.error || response.message || 'status response is not successful')
  process.exit(1)
}
if (!response.task_id) {
  console.error('status response is missing task_id')
  process.exit(1)
}
if (!['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED'].includes(response.status)) {
  console.error(`unsupported async status: ${response.status}`)
  process.exit(1)
}
console.log(response.status)
NODE
  )" || fail "invalid async status response"

  PROGRESS="$(
    node - "$STATUS_RESPONSE" <<'NODE'
const fs = require('fs')
const response = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))
console.log(response.progress ?? 0)
NODE
  )"
  echo "smoke-doc-parser-async: attempt=$attempt status=$STATUS progress=$PROGRESS"

  if [[ "$STATUS" == "SUCCEEDED" ]]; then
    node - "$STATUS_RESPONSE" <<'NODE'
const fs = require('fs')
const response = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))
if (!response.result || response.result.success !== true) {
  console.error('SUCCEEDED status must include successful result')
  process.exit(1)
}
if (!Array.isArray(response.result.chunks) || response.result.chunks.length === 0) {
  console.error('async result must include at least one chunk')
  process.exit(1)
}
if (!response.result.metadata || response.result.metadata.doc_type !== 'PLAIN_TEXT') {
  console.error('async result metadata.doc_type must be PLAIN_TEXT')
  process.exit(1)
}
console.log(`smoke-doc-parser-async: ok task_id=${response.task_id} chunks=${response.result.chunks.length}`)
NODE
    exit 0
  fi

  if [[ "$STATUS" == "FAILED" || "$STATUS" == "CANCELED" ]]; then
    node - "$STATUS_RESPONSE" <<'NODE' >&2
const fs = require('fs')
const response = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))
console.error(response.error || response.message || `terminal status ${response.status}`)
NODE
    fail "async parser task ended with $STATUS"
  fi

  sleep "$POLL_INTERVAL_SECONDS"
done

fail "async parser task did not finish after $MAX_ATTEMPTS attempts"
