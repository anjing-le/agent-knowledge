#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${1:-10082}"
ATTEMPTS="${PROBE_ATTEMPTS:-90}"
BASE_URL="http://localhost:$PORT"
TMP_ROOT="${TMPDIR:-/tmp}"
LOG_FILE="$TMP_ROOT/agent-knowledge-rag-demo-runtime.$PORT.log"
PID_FILE="$TMP_ROOT/agent-knowledge-rag-demo-runtime.$PORT.pid"

cleanup() {
  if [[ -f "$PID_FILE" ]]; then
    local pid
    pid="$(cat "$PID_FILE")"
    kill "$pid" >/dev/null 2>&1 || true
    wait "$pid" >/dev/null 2>&1 || true
    rm -f "$PID_FILE"
  fi
}

fail() {
  echo "probe-rag-demo-runtime: $*" >&2
  if [[ -f "$LOG_FILE" ]]; then
    tail -n 180 "$LOG_FILE" >&2 || true
  fi
  exit 1
}

command -v curl >/dev/null 2>&1 || fail "curl is required"
command -v node >/dev/null 2>&1 || fail "node is required"

cleanup
trap cleanup EXIT

rm -f "$LOG_FILE"

(
  cd "$ROOT/backend"
  SPRING_PROFILES_ACTIVE=dev SERVER_PORT="$PORT" mvn -q spring-boot:run >"$LOG_FILE" 2>&1 &
  echo $! > "$PID_FILE"
)

pid="$(cat "$PID_FILE")"

for _ in $(seq 1 "$ATTEMPTS"); do
  kill -0 "$pid" >/dev/null 2>&1 || fail "backend process exited before health check passed"
  if curl -fsS "$BASE_URL/api/test/health" >/dev/null 2>&1; then
    BASE_URL="$BASE_URL" node <<'NODE'
const http = require('http')
const https = require('https')

const baseUrl = process.env.BASE_URL

function request(method, path, body) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, baseUrl)
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

function assert(condition, message) {
  if (!condition) {
    throw new Error(message)
  }
}

function dataOf(payload, label) {
  assert(String(payload.code) === '0', `${label} code must be 0, got ${payload.code}: ${payload.message || ''}`)
  return payload.data
}

async function main() {
  const health = dataOf(await request('GET', '/api/test/health'), 'health')
  assert(health.status === 'UP', 'health status must be UP')
  assert(Array.isArray(health.activeProfiles) && health.activeProfiles.includes('dev'), 'backend must run with dev profile')

  const seed = dataOf(await request('POST', '/api/test/rag-demo/seed'), 'seed')
  assert(seed.kbId === 'kb_rag_demo_teaching', 'seed kbId mismatch')
  assert(seed.kbName === 'RAG Demo Teaching KB', 'seed kbName mismatch')
  assert(seed.docName === 'scaffold-rag-demo.md', 'seed docName mismatch')
  assert(Array.isArray(seed.chunkIds) && seed.chunkIds.length === 3, 'seed must create 3 chunks')
  assert(seed.vectorCount === 3, 'seed must create 3 vectors')
  assert(seed.sampleResultCount >= 1, 'seed sample retrieval must return results')
  assert(String(seed.retrievalRoute || '').includes('/kb/retrieval'), 'seed retrieval route is missing')
  assert(String(seed.chatRoute || '').includes('/kb/chat'), 'seed chat route is missing')

  const evaluation = dataOf(await request('POST', '/api/test/rag-demo/evaluate-retrieval'), 'evaluation')
  assert(evaluation.passed === true, 'retrieval evaluation must pass')
  assert(evaluation.totalCases === 3 && evaluation.passedCases === 3, 'retrieval evaluation must pass 3/3 cases')
  assert(evaluation.recallAtK === 1, 'retrieval evaluation recallAtK must be 1')

  const adapterStatus = dataOf(await request('GET', '/api/retrieval/adapters/status'), 'adapter status')
  assert(Array.isArray(adapterStatus.adapters) && adapterStatus.adapters.length >= 4, 'adapter status must list RAG adapters')
  for (const adapter of adapterStatus.adapters) {
    assert(adapter.axis, 'adapter axis is required')
    assert(adapter.currentProvider, `adapter ${adapter.axis} currentProvider is required`)
    assert(adapter.currentImplementation, `adapter ${adapter.axis} currentImplementation is required`)
    assert(adapter.boundary, `adapter ${adapter.axis} boundary is required`)
    assert(adapter.configKey, `adapter ${adapter.axis} configKey is required`)
  }

  const results = dataOf(await request('POST', '/api/retrieval/search', {
    query: seed.retrievalQuery,
    kbIds: [seed.kbId],
    topK: 3,
    candidateCount: 6,
    similarityThreshold: 0,
    hybrid: true,
    rerank: false
  }), 'retrieval search')
  assert(Array.isArray(results) && results.length === 3, 'retrieval search must return 3 chunks')
  assert(results.some((item) => item.chunkId === seed.topChunkId), 'retrieval search must include seed top chunk')
  assert(results.every((item) => item.kbId === seed.kbId), 'retrieval search results must stay in demo KB')
  assert(results.every((item) => item.docName === seed.docName), 'retrieval search results must reference demo document')
  assert(results.some((item) => item.scoreExplanation), 'retrieval search should expose score explanation')

  const conversation = dataOf(await request('POST', '/api/chat/conversations', {
    title: 'RAG Demo Runtime Probe',
    description: 'Created by scripts/probe-rag-demo-runtime.sh',
    kbIds: [seed.kbId],
    config: {
      enableRetrieval: true,
      topK: 3,
      similarityThreshold: 0.3,
      enableRerank: false
    }
  }), 'create conversation')
  assert(conversation.conversationId, 'conversationId is required')
  assert(Array.isArray(conversation.kbIds) && conversation.kbIds.includes(seed.kbId), 'conversation must bind demo KB')

  const answer = dataOf(await request('POST', `/api/chat/conversations/${encodeURIComponent(conversation.conversationId)}/messages`, {
    content: seed.chatQuestion,
    kbIds: [seed.kbId],
    stream: false,
    overrideConfig: {
      enableRetrieval: true,
      topK: 3
    }
  }), 'send message')
  assert(answer.role === 'assistant', 'chat response role must be assistant')
  assert(typeof answer.content === 'string' && answer.content.length > 50, 'chat answer content is too short')
  assert(Array.isArray(answer.references) && answer.references.length === 3, 'chat answer must include 3 references')
  assert(answer.references.every((item) => item.docName === seed.docName), 'chat references must point to demo document')
  assert(answer.contextTrace?.includedChunkCount === 3, 'chat context trace must include 3 chunks')

  const messages = dataOf(await request('GET', `/api/chat/conversations/${encodeURIComponent(conversation.conversationId)}/messages`), 'list messages')
  assert(Array.isArray(messages) && messages.length >= 2, 'conversation history must include user and assistant messages')

  console.log('probe-rag-demo-runtime: ok')
  console.log(`probe-rag-demo-runtime: baseUrl=${baseUrl}`)
  console.log(`probe-rag-demo-runtime: kb=${seed.kbName} (${seed.kbId})`)
  console.log(`probe-rag-demo-runtime: chunks=${seed.chunkIds.length}, vectors=${seed.vectorCount}`)
  console.log(`probe-rag-demo-runtime: retrieval=${results.length} hits, evaluation=${evaluation.passedCases}/${evaluation.totalCases}, recall@${evaluation.topK}=${evaluation.recallAtK}`)
  console.log(`probe-rag-demo-runtime: chatReferences=${answer.references.length}, conversation=${conversation.conversationId}`)
  console.log(`probe-rag-demo-runtime: adapters=${adapterStatus.adapters.map((item) => `${item.axis}:${item.currentProvider}`).join(', ')}`)
  console.log(`probe-rag-demo-runtime: pipelineRoute=${seed.pipelineRoute}`)
  console.log(`probe-rag-demo-runtime: retrievalRoute=${seed.retrievalRoute}`)
  console.log(`probe-rag-demo-runtime: chatRoute=${seed.chatRoute}`)
}

main().catch((error) => {
  console.error(`probe-rag-demo-runtime: ${error.message}`)
  process.exit(1)
})
NODE
    echo "probe-rag-demo-runtime: log=$LOG_FILE"
    exit 0
  fi

  sleep 1
done

fail "backend did not pass health check in ${ATTEMPTS}s"
