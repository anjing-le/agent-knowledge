#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_PORT="${BACKEND_PORT:-10083}"
DOC_PARSER_PORT="${DOC_PARSER_PORT:-19001}"
ATTEMPTS="${PROBE_ATTEMPTS:-120}"
BACKEND_BASE_URL="http://localhost:$BACKEND_PORT"
DOC_PARSER_BASE_URL="http://localhost:$DOC_PARSER_PORT"
DOC_PARSER_PYTHON="${DOC_PARSER_PYTHON:-$ROOT/doc-parser/venv/bin/python}"
TMP_ROOT="${TMPDIR:-/tmp}"
RUN_DIR="$(mktemp -d "$TMP_ROOT/agent-knowledge-rag-ingestion.XXXXXX")"
BACKEND_LOG="$TMP_ROOT/agent-knowledge-rag-ingestion-backend.$BACKEND_PORT.log"
DOC_PARSER_LOG="$TMP_ROOT/agent-knowledge-rag-ingestion-doc-parser.$DOC_PARSER_PORT.log"
BACKEND_PID_FILE="$RUN_DIR/backend.pid"
DOC_PARSER_PID_FILE="$RUN_DIR/doc-parser.pid"
SAMPLE_FILE="$RUN_DIR/rag-ingestion-runtime-probe.txt"

cleanup() {
  for file in "$BACKEND_PID_FILE" "$DOC_PARSER_PID_FILE"; do
    if [[ -f "$file" ]]; then
      local pid
      pid="$(cat "$file")"
      kill "$pid" >/dev/null 2>&1 || true
      wait "$pid" >/dev/null 2>&1 || true
    fi
  done
  rm -rf "$RUN_DIR"
}

fail() {
  echo "probe-rag-ingestion-runtime: $*" >&2
  if [[ -f "$BACKEND_LOG" ]]; then
    echo "probe-rag-ingestion-runtime: backend log=$BACKEND_LOG" >&2
    tail -n 160 "$BACKEND_LOG" >&2 || true
  fi
  if [[ -f "$DOC_PARSER_LOG" ]]; then
    echo "probe-rag-ingestion-runtime: doc-parser log=$DOC_PARSER_LOG" >&2
    tail -n 160 "$DOC_PARSER_LOG" >&2 || true
  fi
  exit 1
}

wait_for_url() {
  local label="$1"
  local url="$2"
  local pid_file="$3"

  for _ in $(seq 1 "$ATTEMPTS"); do
    local pid
    pid="$(cat "$pid_file")"
    kill -0 "$pid" >/dev/null 2>&1 || fail "$label process exited before health check passed"
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done

  fail "$label did not pass health check in ${ATTEMPTS}s: $url"
}

command -v curl >/dev/null 2>&1 || fail "curl is required"
command -v node >/dev/null 2>&1 || fail "node is required"
[[ -x "$DOC_PARSER_PYTHON" ]] || fail "doc-parser python is not executable: $DOC_PARSER_PYTHON"

trap cleanup EXIT
rm -f "$BACKEND_LOG" "$DOC_PARSER_LOG"

cat >"$SAMPLE_FILE" <<'EOF'
agent-knowledge ingestion runtime probe.
The Java backend owns knowledge base, document task, chunk, embedding, retrieval and chat orchestration.
The Python FastAPI doc-parser remains an independent HTTP service and returns parsed text plus metadata.
infra-dev-scaffolding keeps APIResponse, PageResult, OpenAPI, request context and quality gates stable while the RAG agent grows on top.
EOF

(
  cd "$ROOT/doc-parser"
  DISABLE_APM=true "$DOC_PARSER_PYTHON" -m uvicorn kparser.app:app \
    --host 127.0.0.1 \
    --port "$DOC_PARSER_PORT" \
    >"$DOC_PARSER_LOG" 2>&1 &
  echo $! >"$DOC_PARSER_PID_FILE"
)

wait_for_url "doc-parser" "$DOC_PARSER_BASE_URL/health" "$DOC_PARSER_PID_FILE"

(
  cd "$ROOT/backend"
  SPRING_PROFILES_ACTIVE=dev \
  SERVER_PORT="$BACKEND_PORT" \
  DOC_PARSER_URL="$DOC_PARSER_BASE_URL" \
  DOC_PARSER_MODE=sync \
  UPLOAD_BASE_DIR="$RUN_DIR/uploads" \
  mvn -q spring-boot:run >"$BACKEND_LOG" 2>&1 &
  echo $! >"$BACKEND_PID_FILE"
)

wait_for_url "backend" "$BACKEND_BASE_URL/api/test/health" "$BACKEND_PID_FILE"

BACKEND_BASE_URL="$BACKEND_BASE_URL" \
DOC_PARSER_BASE_URL="$DOC_PARSER_BASE_URL" \
SAMPLE_FILE="$SAMPLE_FILE" \
node <<'NODE'
const { execFileSync } = require('child_process')
const http = require('http')
const https = require('https')

const backendBaseUrl = process.env.BACKEND_BASE_URL
const docParserBaseUrl = process.env.DOC_PARSER_BASE_URL
const sampleFile = process.env.SAMPLE_FILE

function request(method, path, body) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, backendBaseUrl)
    const payload = body == null ? null : JSON.stringify(body)
    const client = url.protocol === 'https:' ? https : http
    const req = client.request(
      url,
      {
        method,
        headers: {
          Accept: 'application/json',
          ...(payload ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(payload) } : {})
        }
      },
      (res) => {
        let raw = ''
        res.setEncoding('utf8')
        res.on('data', (chunk) => {
          raw += chunk
        })
        res.on('end', () => {
          if (res.statusCode < 200 || res.statusCode >= 300) {
            reject(new Error(`${method} ${path} returned HTTP ${res.statusCode}: ${raw.slice(0, 500)}`))
            return
          }
          try {
            resolve(JSON.parse(raw))
          } catch (error) {
            reject(new Error(`${method} ${path} returned invalid JSON: ${error.message}`))
          }
        })
      }
    )
    req.on('error', reject)
    if (payload) req.write(payload)
    req.end()
  })
}

function dataOf(payload, label) {
  assert(String(payload.code) === '0', `${label} code must be 0, got ${payload.code}: ${payload.message || ''}`)
  return payload.data
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message)
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function uploadDocument(kbId) {
  const raw = execFileSync('curl', [
    '-fsS',
    '-X', 'POST',
    `${backendBaseUrl}/api/knowledge/bases/${encodeURIComponent(kbId)}/documents`,
    '-F', `file=@${sampleFile};filename=rag-ingestion-runtime-probe.txt;type=text/plain`
  ], { encoding: 'utf8' })
  return JSON.parse(raw)
}

async function main() {
  const health = dataOf(await request('GET', '/api/test/health'), 'backend health')
  assert(health.status === 'UP', 'backend health must be UP')

  const adapterStatus = dataOf(await request('GET', '/api/retrieval/adapters/status'), 'adapter status')
  const docParserAdapter = adapterStatus.adapters.find((item) => item.axis === 'docParser')
  assert(docParserAdapter?.currentProvider === 'sync', 'doc-parser adapter must run in sync mode')

  const kb = dataOf(await request('POST', '/api/knowledge/bases', {
    name: `RAG Ingestion Runtime Probe ${Date.now()}`,
    description: 'Created by scripts/probe-rag-ingestion-runtime.sh',
    chunkSize: 220,
    chunkOverlap: 20,
    embeddingModel: 'local-demo'
  }), 'create knowledge base')
  assert(kb.kbId, 'created knowledge base must include kbId')

  const upload = dataOf(uploadDocument(kb.kbId), 'upload document')
  assert(upload.docId, 'upload response must include docId')
  assert(upload.status === 'PENDING', `uploaded document must start as PENDING, got ${upload.status}`)
  assert(upload.docName === 'rag-ingestion-runtime-probe.txt', 'uploaded document name mismatch')

  let document = null
  let tasks = []
  let chunks = null
  for (let attempt = 1; attempt <= 90; attempt += 1) {
    document = dataOf(await request('GET', `/api/knowledge/documents/${encodeURIComponent(upload.docId)}`), 'document detail')
    tasks = dataOf(await request('GET', `/api/knowledge/documents/${encodeURIComponent(upload.docId)}/tasks`), 'document tasks')
    chunks = dataOf(await request('GET', `/api/knowledge/documents/${encodeURIComponent(upload.docId)}/chunks?page=1&size=10`), 'document chunks')
    if (document.status === 'COMPLETED' && Number(chunks.total || 0) > 0) {
      break
    }
    const latestTask = Array.isArray(tasks) ? tasks[0] : null
    if (document.status === 'FAILED' || latestTask?.status === 'FAILED') {
      throw new Error(`document processing failed: doc=${document.progressMsg || ''} task=${latestTask?.errorMessage || latestTask?.message || ''}`)
    }
    await sleep(1000)
  }

  assert(document?.status === 'COMPLETED', `document must be COMPLETED, got ${document?.status}`)
  assert(document?.progress === 1, `document progress must be 1, got ${document?.progress}`)
  assert(document?.chunkNum > 0, 'document must have chunks')
  assert(document?.tokenNum > 0, 'document must have tokens')
  assert(Array.isArray(tasks) && tasks.some((item) => item.status === 'SUCCEEDED' && item.phase === 'COMPLETED'), 'document tasks must include a succeeded completed task')
  assert(Number(chunks.total || 0) > 0, 'chunk list must include records')
  assert(Array.isArray(chunks.records) && chunks.records.some((item) => String(item.content || '').includes('Python FastAPI doc-parser')), 'chunks must include parsed doc-parser content')
  assert(chunks.records.every((item) => item.vectorId), 'chunks must have vector ids')

  const searchResults = dataOf(await request('POST', '/api/retrieval/search', {
    query: 'Python FastAPI doc-parser independent HTTP service',
    kbIds: [kb.kbId],
    topK: 3,
    candidateCount: 6,
    similarityThreshold: 0,
    hybrid: true,
    rerank: false
  }), 'retrieval search')
  assert(Array.isArray(searchResults) && searchResults.length > 0, 'retrieval must return at least one uploaded document chunk')
  assert(searchResults.some((item) => item.docId === upload.docId), 'retrieval results must include uploaded document')
  assert(searchResults.some((item) => String(item.content || '').includes('Python FastAPI doc-parser')), 'retrieval content must come from uploaded file')

  console.log('probe-rag-ingestion-runtime: ok')
  console.log(`probe-rag-ingestion-runtime: backend=${backendBaseUrl}`)
  console.log(`probe-rag-ingestion-runtime: docParser=${docParserBaseUrl}`)
  console.log(`probe-rag-ingestion-runtime: kb=${kb.name} (${kb.kbId})`)
  console.log(`probe-rag-ingestion-runtime: document=${document.docName} (${document.docId}) status=${document.status}`)
  console.log(`probe-rag-ingestion-runtime: chunks=${chunks.total}, tokens=${document.tokenNum}`)
  console.log(`probe-rag-ingestion-runtime: retrievalHits=${searchResults.length}`)
  console.log(`probe-rag-ingestion-runtime: latestTask=${tasks[0]?.status || ''}/${tasks[0]?.phase || ''}`)
}

main().catch((error) => {
  console.error(`probe-rag-ingestion-runtime: ${error.message}`)
  process.exit(1)
})
NODE

echo "probe-rag-ingestion-runtime: backendLog=$BACKEND_LOG"
echo "probe-rag-ingestion-runtime: docParserLog=$DOC_PARSER_LOG"
