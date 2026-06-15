#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() {
  echo "check-template: $*" >&2
  exit 1
}

require_file() {
  local file="$1"
  [[ -f "$file" ]] || fail "missing required file: $file"
}

for file in \
  README.md \
  LICENSE \
  CONTRIBUTING.md \
  contracts/platform-contract.json \
  contracts/scaffold-stack-contract.json \
  contracts/service-boundaries.json \
  contracts/doc-parser-contract.json \
  contracts/retrieval-adapter-contract.json \
  project_document/README.md \
  project_document/ROADMAP.md \
  project_document/STATUS.md \
  project_document/PROJECT_CONSTRAINTS.md \
  project_document/SERVICE_BOUNDARY_GUIDE.md \
  project_document/DOC_PARSER_SERVICE_GUIDE.md \
  project_document/RETRIEVAL_ADAPTER_GUIDE.md \
  project_document/API_CONTRACT_GUIDE.md \
  project_document/LOCAL_STARTUP_GUIDE.md \
  project_document/REMOTE_CALL_GUIDE.md \
  project_document/DEMO_EVIDENCE.md \
  docs/evidence/README.md \
  docs/evidence/TEMPLATE.md \
  scripts/create-demo-evidence.sh \
  scripts/collect-demo-evidence.sh \
  scripts/probe-doc-parser-boundary.sh \
  scripts/check-doc-parser-lifecycle.sh \
  scripts/check-retrieval-adapter-contract.js \
  scripts/check-scaffold-source.sh \
  scripts/seed-rag-demo.sh \
  scripts/smoke-rag-demo.sh \
  scripts/smoke-doc-parser-async.sh \
  backend/.env.example \
  backend/pom.xml \
  backend/src/main/resources/application.yml \
  backend/src/main/resources/application-dev.yml \
  backend/src/main/resources/application-test.yml \
  backend/src/main/resources/application-prod.yml \
  backend/src/main/java/com/anjing/config/properties/DocParserProperties.java \
  backend/src/main/java/com/anjing/config/properties/RerankProperties.java \
  backend/src/main/java/com/anjing/model/response/APIResponse.java \
  backend/src/main/java/com/anjing/model/response/PageResult.java \
  backend/src/main/java/com/anjing/model/constants/ApiConstants.java \
  backend/src/main/java/com/anjing/model/constants/ServiceBoundaryConstants.java \
  backend/src/main/java/com/anjing/knowledge/model/DocumentParseResult.java \
  backend/src/main/java/com/anjing/knowledge/model/response/RagContextTrace.java \
  backend/src/main/java/com/anjing/demo/service/RagDemoSeedService.java \
  backend/src/main/java/com/anjing/knowledge/client/DocParserClient.java \
  backend/src/main/java/com/anjing/knowledge/service/DocumentParseResultMapper.java \
  backend/src/main/java/com/anjing/knowledge/service/DocumentAsyncParsingService.java \
  backend/src/main/java/com/anjing/knowledge/service/DocumentParserRecoveryPollingService.java \
  frontend/package.json \
  frontend/LICENSE \
  frontend/.env.development \
  frontend/.env.production \
  frontend/src/api/paths.ts \
  frontend/src/api/demo.ts \
  frontend/src/api/knowledge.ts \
  frontend/src/api/retrieval.ts \
  frontend/src/api/chat.ts \
  frontend/src/views/pipeline/index.vue \
  frontend/src/contracts/service-boundaries.ts \
  doc-parser/README.md \
  doc-parser/kparser/app.py
do
  require_file "$file"
done

for token in \
  'retrieval-adapter' \
  'VectorStoreService' \
  'KeywordSearchProvider' \
  'RerankProperties' \
  'RemoteHttpClient' \
  'rerank-provider' \
  'Milvus' \
  'pgvector' \
  'Elasticsearch' \
  'BM25'
do
  rg -q --fixed-strings "$token" contracts/retrieval-adapter-contract.json project_document/RETRIEVAL_ADAPTER_GUIDE.md scripts/check-retrieval-adapter-contract.js \
    || fail "retrieval adapter contract is missing token: $token"
done

project_info="$(
  node -e '
    const fs = require("fs");
    const pkg = JSON.parse(fs.readFileSync("frontend/package.json", "utf8"));
    const pom = fs.readFileSync("backend/pom.xml", "utf8").replace(/<parent>[\s\S]*?<\/parent>/, "");
    const artifact = pom.match(/<artifactId>([^<]+)<\/artifactId>/)?.[1] || "";
    const app = fs.readFileSync("backend/src/main/resources/application.yml", "utf8").match(/^\s{4}name:\s*([^\s#]+)/m)?.[1] || "";
    console.log([pkg.name || "", artifact, app].join("\n"));
  '
)"

frontend_name="$(printf '%s\n' "$project_info" | sed -n '1p')"
backend_artifact="$(printf '%s\n' "$project_info" | sed -n '2p')"
spring_name="$(printf '%s\n' "$project_info" | sed -n '3p')"

[[ "$frontend_name" == "agent-knowledge" ]] || fail "frontend package name must be agent-knowledge"
[[ "$backend_artifact" == "agent-knowledge" ]] || fail "backend artifactId must be agent-knowledge"
[[ "$spring_name" == "agent-knowledge" ]] || fail "spring.application.name must be agent-knowledge"

for token in \
  'RAG 智能知识库' \
  'doc-parser' \
  'Python FastAPI' \
  '/api/knowledge' \
  '/api/retrieval' \
  '/api/chat'
do
  rg -q --fixed-strings "$token" README.md project_document \
    || fail "missing project token in docs: $token"
done

for token in \
  'agent-doc-parser' \
  'syncParseFile' \
  'syncParseUrl' \
  'javaAsyncPolling' \
  'DocumentProcessingService.continueAfterParsing' \
  'DocumentParseResultMapper.fromClientResult' \
  'DocumentParserRecoveryPollingService' \
  'taskSnapshot' \
  'javaStatusMapping' \
  'pythonAsyncContract' \
  '_async_status_response' \
  'statusRequestKeys' \
  'smoke-doc-parser-async.sh' \
  'Java must call doc-parser over HTTP'
do
  rg -q --fixed-strings "$token" contracts/doc-parser-contract.json \
    || fail "doc-parser contract is missing token: $token"
done

for token in \
  'mode: ${DOC_PARSER_MODE:sync}' \
  'DOC_PARSER_ASYNC_MAX_POLL_ATTEMPTS' \
  'DOC_PARSER_ASYNC_POLL_INTERVAL_MS' \
  'DOC_PARSER_ASYNC_SUBMIT_ONLY_ENABLED' \
  'DOC_PARSER_ASYNC_RECOVERY_ENABLED' \
  'DOC_PARSER_ASYNC_RECOVERY_FIXED_DELAY_MS' \
  'DOC_PARSER_ASYNC_RECOVERY_BATCH_SIZE'
do
  rg -q --fixed-strings -- "$token" backend/src/main/resources/application.yml backend/.env.example \
    || fail "doc-parser async config is missing token: $token"
done

for token in \
  '@ConfigurationProperties(prefix = "app.doc-parser")' \
  'private String baseUrl = "http://localhost:9001"' \
  'private String mode = "sync"' \
  'private Async async = new Async()' \
  'isAsyncMode' \
  'submitOnlyEnabled' \
  'recoveryEnabled' \
  'recoveryFixedDelayMs' \
  'recoveryBatchSize'
do
  rg -q --fixed-strings -- "$token" backend/src/main/java/com/anjing/config/properties/DocParserProperties.java \
    || fail "DocParserProperties is missing token: $token"
done

for token in \
  'ApiConstants.Knowledge.BASE' \
  'ApiConstants.Retrieval.BASE' \
  'ApiConstants.Chat.BASE'
do
  rg -q --fixed-strings "$token" backend/src/main/java/com/anjing \
    || fail "backend controllers are missing token: $token"
done

for token in \
  'ApiPaths.knowledge' \
  'ApiPaths.test.ragDemoSeed' \
  'RagDemoService' \
  "openApiRequest('search'" \
  "openApiRequest('sendMessage'"
do
  rg -q --fixed-strings "$token" frontend/src/api \
    || fail "frontend API modules are missing token: $token"
done

for token in \
  'private RagContextTrace contextTrace' \
  'private Integer rank' \
  'private String retrievalSource' \
  'private Float keywordScore' \
  'private Float hybridScore' \
  'private Float rerankScore' \
  'private String scoreExplanation'
do
  rg -q --fixed-strings "$token" backend/src/main/java/com/anjing/chat/model/response/MessageResponse.java \
    || fail "chat reference response is missing token: $token"
done

for token in \
  'class RagContextTrace' \
  'private String assemblyStrategy' \
  'private Integer promptCharCount' \
  'private Integer contextCharCount' \
  'private List<IncludedChunk> includedChunks' \
  'class IncludedChunk'
do
  rg -q --fixed-strings "$token" backend/src/main/java/com/anjing/knowledge/model/response/RagContextTrace.java \
    || fail "RAG context trace model is missing token: $token"
done

for token in \
  'contextTrace?: RagContextTrace' \
  'export interface RagContextTrace' \
  'includedChunks?: IncludedChunk[]' \
  'promptSections?: string[]'
do
  rg -q --fixed-strings "$token" frontend/src/contracts/openapi/schemas.ts \
    || fail "frontend OpenAPI RAG context trace contract is missing token: $token"
done

for token in \
  'RagPromptContext' \
  'buildRagContext' \
  'retrieval-context-to-system-prompt' \
  'includedChunks'
do
  rg -q --fixed-strings "$token" backend/src/main/java/com/anjing/knowledge/service/RagPromptBuilderService.java \
    || fail "RAG prompt builder trace is missing token: $token"
done

for token in \
  'SOURCE_VECTOR = "vector"' \
  'setRetrievalSource(SOURCE_VECTOR)'
do
  rg -q --fixed-strings "$token" backend/src/main/java/com/anjing/knowledge/service/RetrievalResultEnrichmentService.java \
    || fail "retrieval enrichment is missing vector source token: $token"
done

for token in \
  'formatReferenceTrace' \
  'ref-trace-chip' \
  'ref-score-explanation' \
  'message-context-trace' \
  'formatContextTraceStats' \
  'formatPromptSection' \
  'scoreExplanation'
do
  rg -q --fixed-strings "$token" frontend/src/views/chat/index.vue \
    || fail "frontend chat reference evidence view is missing token: $token"
done

for token in \
  'RAG Pipeline 教学视图' \
  'infra-dev-scaffolding' \
  'APIResponse / PageResult' \
  'RemoteHttpClient' \
  'Python FastAPI doc-parser' \
  'RagDemoService.seedRagDemo' \
  'RagDemoService.evaluateRetrieval' \
  'retrievalEvaluation' \
  'recallAtKDisplay' \
  'Seed -> Evaluate -> Retrieval -> Chat -> Evidence' \
  './scripts/create-demo-evidence.sh --dry-run' \
  './scripts/collect-demo-evidence.sh --dry-run' \
  './scripts/probe-doc-parser-boundary.sh --contract-only' \
  './scripts/check-doc-parser-lifecycle.sh' \
  './scripts/smoke-doc-parser-async.sh' \
  'Demo 数据已生成' \
  '检索评估已通过' \
  './scripts/seed-rag-demo.sh' \
  './scripts/evaluate-rag-retrieval.sh' \
  './scripts/smoke-rag-demo.sh'
do
  rg -q --fixed-strings "$token" frontend/src/views/pipeline/index.vue \
    || fail "frontend RAG Pipeline view is missing token: $token"
done

for token in \
  'RagDemoSeedService' \
  'RAG Demo Teaching KB' \
  'agent-doc-parser' \
  'documentEmbeddingService.embedChunks' \
  'retrievalService.search' \
  'autoSearch=1' \
  'autoSend=1' \
  './scripts/create-demo-evidence.sh --dry-run' \
  './scripts/collect-demo-evidence.sh --dry-run' \
  './scripts/probe-doc-parser-boundary.sh --contract-only' \
  './scripts/check-doc-parser-lifecycle.sh' \
  './scripts/smoke-doc-parser-async.sh'
do
  rg -q --fixed-strings "$token" backend/src/main/java/com/anjing/demo/service/RagDemoSeedService.java \
    || fail "RAG demo seed service is missing token: $token"
done

for token in \
  'docs/evidence/YYYY-MM-DD/' \
  'Seed -> Evaluate -> Retrieval -> Chat -> Evidence' \
  './scripts/create-demo-evidence.sh --dry-run' \
  './scripts/collect-demo-evidence.sh --dry-run' \
  './scripts/probe-doc-parser-boundary.sh --contract-only' \
  './scripts/check-doc-parser-lifecycle.sh' \
  './scripts/smoke-doc-parser-async.sh' \
  './scripts/evaluate-rag-retrieval.sh' \
  'screenshots/chat-with-citations.png'
do
  rg -q --fixed-strings "$token" project_document/DEMO_EVIDENCE.md docs/evidence scripts/create-demo-evidence.sh \
    || fail "demo evidence template is missing token: $token"
done

for token in \
  'collect-demo-evidence: ok' \
  '--include-doc-parser-live' \
  'BACKEND_BASE_URL' \
  'runtime/demo-routes.txt' \
  'runtime/rag-demo-seed.json' \
  'runtime/rag-retrieval-evaluation.json'
do
  rg -q --fixed-strings -- "$token" scripts/collect-demo-evidence.sh \
    || fail "collect demo evidence script is missing token: $token"
done

for token in \
  'probe-doc-parser-boundary: contract serviceId=' \
  'DOC_PARSER_SERVICE_ID = "agent-doc-parser"' \
  '@app.post("/parse_url"' \
  '_async_status_response' \
  '--live'
do
  rg -q --fixed-strings -- "$token" scripts/probe-doc-parser-boundary.sh \
    || fail "doc-parser boundary probe is missing token: $token"
done

for token in \
  'smoke-doc-parser-async: submitted task_id=' \
  'smoke-doc-parser-async: ok task_id=' \
  '/loader/deep_parse/async' \
  '/loader/status' \
  'DOC_PARSER_ASYNC_SMOKE_MAX_ATTEMPTS' \
  'metadata.doc_type must be PLAIN_TEXT'
do
  rg -q --fixed-strings -- "$token" scripts/smoke-doc-parser-async.sh \
    || fail "async doc-parser smoke script is missing token: $token"
done

for token in \
  'check-doc-parser-lifecycle: statuses=' \
  'javaStatusMapping' \
  'parserStatusUpdateCount' \
  'applyDocParserStatus' \
  'markDocParserStatus' \
  'DocumentStatus.CHUNKING'
do
  rg -q --fixed-strings -- "$token" scripts/check-doc-parser-lifecycle.sh \
    || fail "doc-parser lifecycle check is missing token: $token"
done

for token in \
  'check-scaffold-source: ok' \
  'contracts/scaffold-stack-contract.json' \
  'infra-dev-scaffolding/README.md' \
  'Frontend: Vue 3.5 + TypeScript + Vite 7' \
  'Backend: Spring Boot 3.4.5 + Java 17' \
  'source=missing path='
do
  rg -q --fixed-strings -- "$token" scripts/check-scaffold-source.sh \
    || fail "scaffold source check is missing token: $token"
done

for token in \
  'class DocumentParseResult' \
  'class ChunkData' \
  'static DocumentParseResult error' \
  'static DocumentParseResult deferred'
do
  rg -q --fixed-strings -- "$token" backend/src/main/java/com/anjing/knowledge/model/DocumentParseResult.java \
    || fail "DocumentParseResult is missing token: $token"
done

for token in \
  '@Scheduled(fixedDelayString = "${app.doc-parser.async.recovery-fixed-delay-ms:15000}")' \
  'pollRecoverableTasksOnce' \
  'findRecoverableParserTasks' \
  'continueAfterParsing' \
  'DocumentParseResultMapper'
do
  rg -q --fixed-strings -- "$token" backend/src/main/java/com/anjing/knowledge/service/DocumentParserRecoveryPollingService.java \
    || fail "DocumentParserRecoveryPollingService is missing token: $token"
done

for token in \
  'retrievalRoute' \
  'seed-rag-demo: retrievalRoute='
do
  rg -q --fixed-strings "$token" scripts/seed-rag-demo.sh \
    || fail "RAG demo seed script is missing token: $token"
done

if rg -n 'agent-dev-scaffolding|apifoxmock|6400575|6097373|Daymychen/art-design-pro|Agent Dev Scaffolding' \
  README.md CONTRIBUTING.md project_document backend frontend \
  --glob '!frontend/node_modules/**' \
  --glob '!frontend/dist/**' \
  --glob '!backend/target/**' \
  --glob '!frontend/LICENSE'
then
  fail "stale template identity or mock endpoint found"
fi

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  tracked_build_outputs="$(git ls-files frontend/dist backend/target backend/logs)"
  [[ -z "$tracked_build_outputs" ]] || fail "build outputs are tracked by git"
fi

echo "check-template: ok"
