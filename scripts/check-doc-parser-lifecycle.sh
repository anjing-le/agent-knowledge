#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() {
  echo "check-doc-parser-lifecycle: $*" >&2
  exit 1
}

require_file() {
  local file="$1"
  [[ -f "$file" ]] || fail "missing required file: $file"
}

require_token() {
  local file="$1"
  local token="$2"
  rg -q --fixed-strings -- "$token" "$file" \
    || fail "$file is missing token: $token"
}

for file in \
  contracts/doc-parser-contract.json \
  backend/src/main/java/com/anjing/knowledge/client/DocParserClient.java \
  backend/src/main/java/com/anjing/knowledge/model/entity/DocumentProcessingTask.java \
  backend/src/main/java/com/anjing/knowledge/model/response/DocumentProcessingTaskResponse.java \
  backend/src/main/java/com/anjing/knowledge/service/DocParserStatusMapper.java \
  backend/src/main/java/com/anjing/knowledge/service/DocumentProcessingProgressService.java \
  backend/src/main/java/com/anjing/knowledge/service/DocumentProcessingTaskService.java \
  backend/src/test/java/com/anjing/knowledge/service/DocParserStatusMapperTest.java \
  backend/src/test/java/com/anjing/knowledge/service/DocumentProcessingProgressServiceTest.java \
  backend/src/test/java/com/anjing/knowledge/service/DocumentProcessingTaskServiceTest.java \
  project_document/DOC_PARSER_SERVICE_GUIDE.md \
  project_document/SCAFFOLD_TO_RAG_AGENT_GUIDE.md
do
  require_file "$file"
done

for token in \
  'submitAsyncParseDocument' \
  'submitAsyncParseDocumentByUrl' \
  'getAsyncParseStatus' \
  'ASYNC_PARSE_PATH = "/loader/deep_parse/async"' \
  'ASYNC_STATUS_PATH = "/loader/status"' \
  'RemoteHttpClient'
do
  require_token backend/src/main/java/com/anjing/knowledge/client/DocParserClient.java "$token"
done

for token in \
  '@Component' \
  'DocumentStatus.CHUNKING' \
  'DocumentStatus.PARSE_FAILED' \
  'new MappedStatus(DocumentStatus.PARSING, "PENDING", "PARSING", 0.1f)' \
  'new MappedStatus(DocumentStatus.PARSING, "RUNNING", "PARSING", 0.2f)' \
  'new MappedStatus(DocumentStatus.CHUNKING, "RUNNING", "CHUNKING", 0.3f)'
do
  require_token backend/src/main/java/com/anjing/knowledge/service/DocParserStatusMapper.java "$token"
done

for token in \
  'applyDocParserStatus' \
  'docParserStatusMapper.map' \
  'markDocParserStatus' \
  'DocumentStatus.PARSE_FAILED' \
  'DocumentStatus.CHUNKING'
do
  require_token backend/src/main/java/com/anjing/knowledge/service/DocumentProcessingProgressService.java "$token"
done

for token in \
  'markDocParserStatus' \
  'setParserTaskId' \
  'setCompletedAt(DateUtils.nowLocalDateTime())' \
  'setStartedAt(DateUtils.nowLocalDateTime())'
do
  require_token backend/src/main/java/com/anjing/knowledge/service/DocumentProcessingTaskService.java "$token"
done

for token in \
  'mapShouldStayAlignedWithDocParserContract' \
  'doc-parser-contract.json' \
  'applyDocParserStatusShouldExposeRunningParserTask' \
  'applyDocParserStatusShouldMoveSucceededParserTaskIntoChunking' \
  'markDocParserStatusShouldStoreParserTaskAndLifecycleSnapshot'
do
  rg -q --fixed-strings -- "$token" backend/src/test/java/com/anjing/knowledge/service \
    || fail "lifecycle tests are missing token: $token"
done

node <<'NODE'
const fs = require('fs')

function fail(message) {
  console.error(`check-doc-parser-lifecycle: ${message}`)
  process.exit(1)
}

const contract = JSON.parse(fs.readFileSync('contracts/doc-parser-contract.json', 'utf8'))
const expected = {
  PENDING: { documentStatus: 'PARSING', taskStatus: 'PENDING', taskPhase: 'PARSING', progress: 0.1 },
  RUNNING: { documentStatus: 'PARSING', taskStatus: 'RUNNING', taskPhase: 'PARSING', progress: 0.2 },
  SUCCEEDED: { documentStatus: 'CHUNKING', taskStatus: 'RUNNING', taskPhase: 'CHUNKING', progress: 0.3 },
  FAILED: { documentStatus: 'PARSE_FAILED', taskStatus: 'FAILED', taskPhase: 'PARSING', progress: 0.0 },
  CANCELED: { documentStatus: 'PARSE_FAILED', taskStatus: 'FAILED', taskPhase: 'PARSING', progress: 0.0 }
}

for (const [status, expectedValue] of Object.entries(expected)) {
  const actual = contract.javaStatusMapping?.[status]
  if (!actual) {
    fail(`javaStatusMapping is missing ${status}`)
  }
  for (const [key, value] of Object.entries(expectedValue)) {
    if (actual[key] !== value) {
      fail(`${status}.${key} must be ${value}, got ${actual[key]}`)
    }
  }
}

const routes = Object.fromEntries(contract.routes.map((route) => [route.name, route]))
if (routes.asyncDeepParse?.path !== '/loader/deep_parse/async') {
  fail('asyncDeepParse path must stay /loader/deep_parse/async')
}
if (routes.asyncStatus?.path !== '/loader/status') {
  fail('asyncStatus path must stay /loader/status')
}

console.log(`check-doc-parser-lifecycle: statuses=${Object.keys(expected).join(',')}`)
console.log('check-doc-parser-lifecycle: ok')
NODE
