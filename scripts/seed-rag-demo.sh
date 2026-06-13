#!/usr/bin/env bash
set -euo pipefail

BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://localhost:10001}"
ENDPOINT="${BACKEND_BASE_URL%/}/api/test/rag-demo/seed"

# Calls the dev/test RagDemoSeedService endpoint on a running backend.
fail() {
  echo "seed-rag-demo: $*" >&2
  exit 1
}

if ! command -v curl >/dev/null 2>&1; then
  fail "curl is required"
fi

if ! command -v node >/dev/null 2>&1; then
  fail "node is required"
fi

response="$(curl -fsS -X POST "$ENDPOINT" -H 'Content-Type: application/json')" \
  || fail "backend seed endpoint is not reachable: $ENDPOINT"

SEED_RESPONSE="$response" node <<'NODE'
const raw = process.env.SEED_RESPONSE || ''
let payload
try {
  payload = JSON.parse(raw)
} catch (error) {
  console.error(`seed-rag-demo: invalid JSON response: ${error.message}`)
  process.exit(1)
}

if (String(payload.code) !== '0') {
  console.error(`seed-rag-demo: backend returned code=${payload.code} message=${payload.message}`)
  process.exit(1)
}

const data = payload.data || {}
console.log('seed-rag-demo: ok')
console.log(`seed-rag-demo: kb=${data.kbName || ''} (${data.kbId || ''})`)
console.log(`seed-rag-demo: doc=${data.docName || ''} (${data.docId || ''})`)
console.log(`seed-rag-demo: vectors=${data.vectorCount ?? 0}, sampleResults=${data.sampleResultCount ?? 0}`)
console.log(`seed-rag-demo: retrievalQuery=${data.retrievalQuery || ''}`)
console.log(`seed-rag-demo: chatRoute=${data.chatRoute || ''}`)
NODE
