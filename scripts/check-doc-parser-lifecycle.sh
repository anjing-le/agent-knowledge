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
  doc-parser/kparser/app.py \
  backend/src/main/java/com/anjing/config/properties/DocParserProperties.java \
  backend/src/main/java/com/anjing/knowledge/client/DocParserClient.java \
  backend/src/main/java/com/anjing/knowledge/model/DocumentParseResult.java \
  backend/src/main/java/com/anjing/knowledge/service/DocumentParseResultMapper.java \
  backend/src/main/java/com/anjing/knowledge/model/entity/DocumentProcessingTask.java \
  backend/src/main/java/com/anjing/knowledge/model/response/DocumentProcessingTaskResponse.java \
  backend/src/main/java/com/anjing/knowledge/service/DocParserStatusMapper.java \
  backend/src/main/java/com/anjing/knowledge/service/DocumentAsyncParsingService.java \
  backend/src/main/java/com/anjing/knowledge/service/DocumentParserRecoveryPollingService.java \
  backend/src/main/java/com/anjing/knowledge/service/DocumentProcessingProgressService.java \
  backend/src/main/java/com/anjing/knowledge/service/DocumentProcessingTaskService.java \
  backend/src/test/java/com/anjing/knowledge/service/DocumentAsyncParsingServiceTest.java \
  backend/src/test/java/com/anjing/knowledge/service/DocumentParserRecoveryPollingServiceTest.java \
  backend/src/test/java/com/anjing/knowledge/service/DocumentSubmitOnlyRecoveryFlowTest.java \
  backend/src/test/java/com/anjing/config/properties/DocParserPropertiesTest.java \
  backend/src/test/java/com/anjing/knowledge/service/DocParserStatusMapperTest.java \
  backend/src/test/java/com/anjing/knowledge/service/DocumentProcessingProgressServiceTest.java \
  backend/src/test/java/com/anjing/knowledge/service/DocumentProcessingTaskServiceTest.java \
  project_document/DOC_PARSER_SERVICE_GUIDE.md \
  project_document/SCAFFOLD_TO_RAG_AGENT_GUIDE.md
do
  require_file "$file"
done

for token in \
  '_async_submit_success' \
  '_async_status_response' \
  '_run_uploaded_file_parse_task' \
  '_run_url_file_parse_task' \
  '"task_id"' \
  '"SUCCEEDED"' \
  '"FAILED"' \
  '"CANCELED"'
do
  require_token doc-parser/kparser/app.py "$token"
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
  '@ConfigurationProperties(prefix = "app.doc-parser")' \
  'isAsyncMode' \
  'maxPollAttempts' \
  'pollIntervalMs' \
  'submitOnlyEnabled' \
  'recoveryEnabled' \
  'recoveryFixedDelayMs' \
  'recoveryBatchSize'
do
  require_token backend/src/main/java/com/anjing/config/properties/DocParserProperties.java "$token"
done

for token in \
  'DocumentAsyncParsingService' \
  'submitAsyncParseDocument' \
  'getAsyncParseStatus' \
  'maxPollAttempts' \
  'pollIntervalMs' \
  'GlobalRequestContextHolder.requestIdOrNull()' \
  'applyDocParserStatus' \
  'docParserProperties.getAsync().getMaxPollAttempts()' \
  'docParserProperties.getAsync().getPollIntervalMs()' \
  'docParserProperties.getAsync().isSubmitOnlyEnabled()' \
  'DocumentParseResult.deferred' \
  'task_id 为空'
do
  require_token backend/src/main/java/com/anjing/knowledge/service/DocumentAsyncParsingService.java "$token"
done

for token in \
  '@Scheduled(fixedDelayString = "${app.doc-parser.async.recovery-fixed-delay-ms:15000}")' \
  'pollRecoverableTasksOnce' \
  'findRecoverableParserTasks' \
  'continueAfterParsing' \
  'DocumentParseResultMapper'
do
  require_token backend/src/main/java/com/anjing/knowledge/service/DocumentParserRecoveryPollingService.java "$token"
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
  'setParserStatus' \
  'setParserProgress' \
  'setParserStatusUpdateCount' \
  'setParserLastPolledAt(DateUtils.nowLocalDateTime())' \
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
  'parseDocumentShouldSubmitTaskPollUntilSucceededAndReturnResult' \
  'uploadShouldSubmitParserTaskAndRecoveryShouldCompletePipeline' \
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
if (contract.javaAsyncPolling?.service !== 'DocumentAsyncParsingService') {
  fail('javaAsyncPolling.service must stay DocumentAsyncParsingService')
}
if (contract.pythonAsyncContract?.submitResponse !== '_async_submit_success') {
  fail('pythonAsyncContract.submitResponse must stay _async_submit_success')
}
if (contract.pythonAsyncContract?.statusResponse !== '_async_status_response') {
  fail('pythonAsyncContract.statusResponse must stay _async_status_response')
}
for (const key of ['task_id', 'request_id']) {
  if (!contract.pythonAsyncContract?.statusRequestKeys?.includes(key)) {
    fail(`pythonAsyncContract.statusRequestKeys must include ${key}`)
  }
}
if (contract.javaAsyncPolling?.defaultMode !== 'sync') {
  fail('javaAsyncPolling.defaultMode must stay sync')
}
if (contract.javaAsyncPolling?.resultMapper !== 'DocumentParseResultMapper.fromClientResult') {
  fail('javaAsyncPolling.resultMapper must stay DocumentParseResultMapper.fromClientResult')
}
if (contract.javaAsyncPolling?.continuation !== 'DocumentProcessingService.continueAfterParsing') {
  fail('javaAsyncPolling.continuation must stay DocumentProcessingService.continueAfterParsing')
}
if (contract.javaAsyncPolling?.submitOnlyMode?.result !== 'DocumentParseResult.deferred') {
  fail('javaAsyncPolling.submitOnlyMode.result must stay DocumentParseResult.deferred')
}
if (contract.javaAsyncPolling?.submitOnlyMode?.defaultEnabled !== false) {
  fail('javaAsyncPolling.submitOnlyMode.defaultEnabled must stay false')
}
if (contract.javaAsyncPolling?.recoveryCoordinator?.service !== 'DocumentParserRecoveryPollingService') {
  fail('javaAsyncPolling.recoveryCoordinator.service must stay DocumentParserRecoveryPollingService')
}
if (contract.javaAsyncPolling?.recoveryCoordinator?.repository !== 'DocumentProcessingTaskRepository.findRecoverableParserTasks') {
  fail('javaAsyncPolling.recoveryCoordinator.repository must stay DocumentProcessingTaskRepository.findRecoverableParserTasks')
}
if (contract.javaAsyncPolling?.recoveryCoordinator?.defaultEnabled !== false) {
  fail('javaAsyncPolling.recoveryCoordinator.defaultEnabled must stay false')
}
const snapshotFields = contract.javaAsyncPolling?.taskSnapshot?.fields || []
for (const field of ['parserTaskId', 'parserStatus', 'parserProgress', 'parserStatusUpdateCount', 'parserLastPolledAt']) {
  if (!snapshotFields.includes(field)) {
    fail(`javaAsyncPolling.taskSnapshot.fields is missing ${field}`)
  }
}

console.log(`check-doc-parser-lifecycle: statuses=${Object.keys(expected).join(',')}`)
console.log('check-doc-parser-lifecycle: ok')
NODE
