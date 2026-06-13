#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MODE="contract-only"
DOC_PARSER_URL="${DOC_PARSER_URL:-http://localhost:9001}"
BACKEND_URL="${BACKEND_URL:-http://localhost:10001}"

fail() {
  echo "probe-doc-parser-boundary: $*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/probe-doc-parser-boundary.sh [--contract-only|--live]

Checks the Java/Python doc-parser boundary used by the RAG ingestion pipeline.

Modes:
  --contract-only  Validate contracts, Java client calls, and Python FastAPI routes. Default.
  --live           Also call DOC_PARSER_URL /health and backend /api/test/health.

Environment:
  DOC_PARSER_URL   Defaults to http://localhost:9001
  BACKEND_URL      Defaults to http://localhost:10001
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --contract-only)
      MODE="contract-only"
      shift
      ;;
    --live)
      MODE="live"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "unexpected argument: $1"
      ;;
  esac
done

require_file() {
  local file="$1"
  [[ -f "$file" ]] || fail "missing required file: $file"
}

require_token() {
  local file="$1"
  local token="$2"
  rg -q --fixed-strings "$token" "$file" \
    || fail "$file is missing token: $token"
}

for file in \
  contracts/doc-parser-contract.json \
  contracts/scaffold-stack-contract.json \
  backend/src/main/java/com/anjing/knowledge/client/DocParserClient.java \
  backend/src/main/java/com/anjing/knowledge/service/DocumentParsingService.java \
  backend/src/main/java/com/anjing/controller/TestController.java \
  doc-parser/kparser/app.py \
  project_document/DOC_PARSER_SERVICE_GUIDE.md \
  project_document/REMOTE_CALL_GUIDE.md
do
  require_file "$file"
done

for token in \
  'baseUrl + "/health"' \
  'baseUrl + "/parse"' \
  'baseUrl + "/parse_url"' \
  'DOC_PARSER_SERVICE_ID = "agent-doc-parser"' \
  'RemoteHttpClient' \
  'ASYNC_PARSE_PATH = "/loader/deep_parse/async"' \
  'ASYNC_STATUS_PATH = "/loader/status"'
do
  require_token backend/src/main/java/com/anjing/knowledge/client/DocParserClient.java "$token"
done

for token in \
  'doc-parser 服务不可用，请确保 doc-parser 已启动（端口9001）' \
  'docParserClient.isHealthy()' \
  'docParserClient.parseDocument'
do
  require_token backend/src/main/java/com/anjing/knowledge/service/DocumentParsingService.java "$token"
done

for token in \
  '@app.get("/health"' \
  '@app.post("/parse"' \
  '@app.post("/parse_url"'
do
  require_token doc-parser/kparser/app.py "$token"
done

for token in \
  'serviceId", "agent-doc-parser"' \
  'downstreams' \
  'docParserClient.isHealthy()'
do
  require_token backend/src/main/java/com/anjing/controller/TestController.java "$token"
done

node <<'NODE'
const fs = require('fs')

const contract = JSON.parse(fs.readFileSync('contracts/doc-parser-contract.json', 'utf8'))
const scaffold = JSON.parse(fs.readFileSync('contracts/scaffold-stack-contract.json', 'utf8'))

function assert(condition, message) {
  if (!condition) {
    console.error(`probe-doc-parser-boundary: ${message}`)
    process.exit(1)
  }
}

const routeByName = Object.fromEntries(contract.routes.map((route) => [route.name, route]))
for (const [name, path, method] of [
  ['health', '/health', 'GET'],
  ['syncParseFile', '/parse', 'POST'],
  ['syncParseUrl', '/parse_url', 'POST'],
  ['asyncDeepParse', '/loader/deep_parse/async', 'POST'],
  ['asyncStatus', '/loader/status', 'POST']
]) {
  assert(routeByName[name], `missing contract route: ${name}`)
  assert(routeByName[name].path === path, `${name} path must be ${path}`)
  assert(routeByName[name].methods.includes(method), `${name} must support ${method}`)
}

assert(contract.serviceId === 'agent-doc-parser', 'contract serviceId must be agent-doc-parser')
assert(contract.runtime === 'python-fastapi', 'doc-parser runtime must be python-fastapi')
assert(scaffold.docParser?.serviceId === contract.serviceId, 'scaffold stack doc-parser serviceId must match contract')
assert(scaffold.docParser?.integration?.includes('Java backend calls doc-parser over HTTP'), 'scaffold stack must keep HTTP integration wording')
assert(contract.boundaries?.some((item) => item.includes('Java must call doc-parser over HTTP')), 'contract must state Java calls doc-parser over HTTP')

console.log(`probe-doc-parser-boundary: contract serviceId=${contract.serviceId}`)
console.log(`probe-doc-parser-boundary: contract runtime=${contract.runtime}`)
console.log(`probe-doc-parser-boundary: contract routes=${Object.keys(routeByName).join(',')}`)
NODE

if [[ "$MODE" == "live" ]]; then
  DOC_HEALTH_FILE="$(mktemp "${TMPDIR:-/tmp}/agent-knowledge-doc-parser-health.XXXXXX.json")"
  BACKEND_HEALTH_FILE="$(mktemp "${TMPDIR:-/tmp}/agent-knowledge-backend-health.XXXXXX.json")"

  curl -fsS "$DOC_PARSER_URL/health" >"$DOC_HEALTH_FILE" \
    || fail "doc-parser health check failed: $DOC_PARSER_URL/health"
  curl -fsS "$BACKEND_URL/api/test/health" >"$BACKEND_HEALTH_FILE" \
    || fail "backend health check failed: $BACKEND_URL/api/test/health"

  node - "$DOC_HEALTH_FILE" "$BACKEND_HEALTH_FILE" <<'NODE'
const fs = require('fs')

const [docFile, backendFile] = process.argv.slice(2)
const docHealth = JSON.parse(fs.readFileSync(docFile, 'utf8'))
const backendHealth = JSON.parse(fs.readFileSync(backendFile, 'utf8'))

function assert(condition, message) {
  if (!condition) {
    console.error(`probe-doc-parser-boundary: ${message}`)
    process.exit(1)
  }
}

assert(typeof docHealth === 'object' && docHealth !== null, 'doc-parser health must be JSON object')
assert(backendHealth.code === '0' || backendHealth.success === true, 'backend health response must be successful')
const docParser = backendHealth.data?.downstreams?.docParser
assert(docParser?.serviceId === 'agent-doc-parser', 'backend health must expose agent-doc-parser downstream')
assert(['UP', 'DOWN'].includes(docParser.status), 'backend doc-parser downstream status must be UP or DOWN')

console.log(`probe-doc-parser-boundary: live doc-parser health=${docHealth.status || docHealth.message || 'ok'}`)
console.log(`probe-doc-parser-boundary: live backend downstream=${docParser.serviceId}:${docParser.status}`)
NODE

  echo "probe-doc-parser-boundary: docParserHealth=$DOC_HEALTH_FILE"
  echo "probe-doc-parser-boundary: backendHealth=$BACKEND_HEALTH_FILE"
else
  echo "probe-doc-parser-boundary: live=skipped"
fi

echo "probe-doc-parser-boundary: ok"

