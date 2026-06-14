#!/usr/bin/env bash
set -euo pipefail

BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://localhost:10001}"
ENDPOINT="${BACKEND_BASE_URL%/}/api/test/rag-demo/evaluate-retrieval"

fail() {
  echo "evaluate-rag-retrieval: $*" >&2
  exit 1
}

if ! command -v curl >/dev/null 2>&1; then
  fail "curl is required"
fi

if ! command -v node >/dev/null 2>&1; then
  fail "node is required"
fi

response="$(curl -fsS -X POST "$ENDPOINT" -H 'Content-Type: application/json')" \
  || fail "backend evaluation endpoint is not reachable: $ENDPOINT"

EVALUATION_RESPONSE="$response" node <<'NODE'
const raw = process.env.EVALUATION_RESPONSE || ''
let payload
try {
  payload = JSON.parse(raw)
} catch (error) {
  console.error(`evaluate-rag-retrieval: invalid JSON response: ${error.message}`)
  process.exit(1)
}

if (String(payload.code) !== '0') {
  console.error(`evaluate-rag-retrieval: backend returned code=${payload.code} message=${payload.message}`)
  process.exit(1)
}

const data = payload.data || {}
const cases = Array.isArray(data.cases) ? data.cases : []
if (!data.passed || !cases.length || data.passedCases !== data.totalCases) {
  console.error(
    `evaluate-rag-retrieval: evaluation failed passed=${data.passed} passedCases=${data.passedCases} totalCases=${data.totalCases}`
  )
  process.exit(1)
}

console.log('evaluate-rag-retrieval: ok')
console.log(`evaluate-rag-retrieval: suite=${data.suiteName || ''}`)
console.log(`evaluate-rag-retrieval: kb=${data.kbId || ''}`)
console.log(`evaluate-rag-retrieval: recallAtK=${data.recallAtK ?? 0}, topK=${data.topK ?? 0}`)
for (const item of cases) {
  console.log(
    `evaluate-rag-retrieval: case passed=${Boolean(item.passed)} rank=${item.expectedRank ?? 'miss'} top=${item.topChunkId || ''} query=${item.query || ''}`
  )
}
NODE
