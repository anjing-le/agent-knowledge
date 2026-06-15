#!/usr/bin/env node
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')

function fail(message) {
  console.error(`check-scaffold-alignment: ${message}`)
  process.exit(1)
}

function read(relativeFile) {
  const file = path.join(root, relativeFile)
  if (!fs.existsSync(file)) {
    fail(`missing required file: ${relativeFile}`)
  }
  return fs.readFileSync(file, 'utf8')
}

function readJson(relativeFile) {
  try {
    return JSON.parse(read(relativeFile))
  } catch (error) {
    fail(`invalid json in ${relativeFile}: ${error.message}`)
  }
}

function requireToken(relativeFile, token) {
  const source = read(relativeFile)
  if (!source.includes(token)) {
    fail(`${relativeFile} is missing token: ${token}`)
  }
}

function requireAbsent(relativeFile, pattern, description) {
  const source = read(relativeFile)
  if (pattern.test(source)) {
    fail(`${relativeFile} contains ${description}`)
  }
}

function requireAbsentInDir(relativeDir, pattern, description) {
  const dir = path.join(root, relativeDir)
  if (!fs.existsSync(dir)) {
    fail(`missing required directory: ${relativeDir}`)
  }
  for (const file of walk(dir)) {
    const relativeFile = path.relative(root, file)
    const source = fs.readFileSync(file, 'utf8')
    if (pattern.test(source)) {
      fail(`${relativeFile} contains ${description}`)
    }
  }
}

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (['node_modules', 'dist', 'target', 'logs'].includes(entry.name)) continue
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(fullPath, files)
    } else {
      files.push(fullPath)
    }
  }
  return files
}

function requireDependency(packageJson, group, name, expectedPrefix) {
  const actual = packageJson[group]?.[name]
  if (!actual) {
    fail(`frontend/package.json is missing ${group}.${name}`)
  }
  if (!actual.startsWith(expectedPrefix)) {
    fail(`frontend/package.json ${group}.${name} must start with ${expectedPrefix}, got ${actual}`)
  }
}

const frontendPackage = readJson('frontend/package.json')
const scaffoldStackContract = readJson('contracts/scaffold-stack-contract.json')
const platformContract = readJson('contracts/platform-contract.json')
const serviceBoundaries = readJson('contracts/service-boundaries.json')
const docParserContract = readJson('contracts/doc-parser-contract.json')
const retrievalAdapterContract = readJson('contracts/retrieval-adapter-contract.json')

for (const file of [
  'README.md',
  'CONTRIBUTING.md',
  'backend/pom.xml',
  'frontend/package.json',
  'frontend/vite.config.ts',
  'frontend/src/api/demo.ts',
  'frontend/src/views/pipeline/index.vue',
  'frontend/src/views/chat/index.vue',
  'contracts/platform-contract.json',
  'contracts/scaffold-stack-contract.json',
  'contracts/service-boundaries.json',
  'contracts/doc-parser-contract.json',
  'contracts/retrieval-adapter-contract.json',
  'scripts/check-template.sh',
  'scripts/check-contracts.sh',
  'scripts/quality-gate.sh',
  'scripts/create-demo-evidence.sh',
  'scripts/collect-demo-evidence.sh',
  'scripts/probe-doc-parser-boundary.sh',
  'scripts/probe-retrieval-adapters.sh',
  'scripts/check-doc-parser-lifecycle.sh',
  'scripts/check-retrieval-adapter-contract.js',
  'scripts/smoke-doc-parser-async.sh',
  'scripts/seed-rag-demo.sh',
  'scripts/evaluate-rag-retrieval.sh',
  'scripts/smoke-rag-demo.sh',
  'docs/evidence/README.md',
  'docs/evidence/TEMPLATE.md',
  'project_document/PROJECT_CONSTRAINTS.md',
  'project_document/NEW_MODULE_GUIDE.md',
  'project_document/SCAFFOLD_TO_RAG_AGENT_GUIDE.md',
  'project_document/DOC_PARSER_SERVICE_GUIDE.md',
  'project_document/RETRIEVAL_ADAPTER_GUIDE.md',
  'project_document/RETRIEVAL_ADAPTER_SWITCH_GUIDE.md',
  'backend/src/main/java/com/anjing/demo/controller/RagDemoController.java',
  'backend/src/main/java/com/anjing/demo/model/response/RagDemoSeedResponse.java',
  'backend/src/main/java/com/anjing/demo/model/response/RagRetrievalEvaluationResponse.java',
  'backend/src/main/java/com/anjing/demo/service/RagDemoSeedService.java',
  'backend/src/main/java/com/anjing/demo/service/RagRetrievalEvaluationService.java',
  'backend/src/main/java/com/anjing/config/properties/DocParserProperties.java',
  'backend/src/main/java/com/anjing/config/properties/KeywordSearchProperties.java',
  'backend/src/main/java/com/anjing/config/properties/RerankProperties.java',
  'backend/src/main/java/com/anjing/config/properties/VectorStoreProperties.java',
  'backend/src/main/java/com/anjing/knowledge/model/DocumentParseResult.java',
  'backend/src/main/java/com/anjing/knowledge/model/response/RagContextTrace.java',
  'backend/src/main/java/com/anjing/chat/service/ChatConversationLifecycleService.java',
  'backend/src/main/java/com/anjing/chat/service/ChatConversationConfigService.java',
  'backend/src/main/java/com/anjing/chat/service/ChatMessagePersistenceService.java',
  'backend/src/main/java/com/anjing/chat/service/RagChatOrchestrationService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentIngestionService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentProcessingContextService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentProcessingProgressService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocParserStatusMapper.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentParseResultMapper.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentParsingService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentAsyncParsingService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentParserRecoveryPollingService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentChunkingService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentChunkPersistenceService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentEmbeddingService.java',
  'backend/src/main/java/com/anjing/knowledge/service/KeywordSearchProvider.java',
  'backend/src/main/java/com/anjing/knowledge/service/LocalKeywordSearchProvider.java',
  'backend/src/main/java/com/anjing/knowledge/service/Bm25KeywordSearchProvider.java',
  'backend/src/main/java/com/anjing/knowledge/service/ElasticsearchKeywordSearchProvider.java',
  'backend/src/main/java/com/anjing/knowledge/service/PgVectorStoreService.java',
  'backend/src/main/java/com/anjing/knowledge/service/RetrievalResultEnrichmentService.java',
  'backend/src/main/java/com/anjing/knowledge/service/RetrievalHybridSearchService.java',
  'backend/src/main/java/com/anjing/knowledge/service/RerankProviderClient.java',
  'backend/src/main/java/com/anjing/knowledge/service/RetrievalRerankService.java',
  'backend/src/main/java/com/anjing/knowledge/service/RagPromptBuilderService.java',
  'backend/src/test/java/com/anjing/demo/service/RagDemoSeedServiceTest.java',
  'backend/src/test/java/com/anjing/demo/service/RagRetrievalEvaluationServiceTest.java',
  'backend/src/test/java/com/anjing/knowledge/service/Bm25KeywordSearchProviderTest.java',
  'backend/src/test/java/com/anjing/knowledge/service/ElasticsearchKeywordSearchProviderTest.java',
  'backend/src/test/java/com/anjing/knowledge/service/PgVectorStoreServiceTest.java',
  'backend/src/test/java/com/anjing/knowledge/service/DocumentSubmitOnlyRecoveryFlowTest.java',
  'backend/src/test/java/com/anjing/smoke/RagDemoSmokeTest.java',
  'doc-parser/kparser/app.py'
]) {
  read(file)
}

for (const token of [
  'RagDemoSeedService',
  'api/test/rag-demo/seed',
  'retrievalRoute',
  'seed-rag-demo: ok'
]) {
  requireToken('scripts/seed-rag-demo.sh', token)
}

for (const token of [
  'RagDemoSmokeTest',
  'SPRING_PROFILES_ACTIVE=test',
  'smoke-rag-demo: ok'
]) {
  requireToken('scripts/smoke-rag-demo.sh', token)
}

for (const token of [
  'smoke-doc-parser-async: submitted task_id=',
  'smoke-doc-parser-async: ok task_id=',
  '/loader/deep_parse/async',
  '/loader/status',
  'DOC_PARSER_ASYNC_SMOKE_MAX_ATTEMPTS',
  'metadata.doc_type must be PLAIN_TEXT'
]) {
  requireToken('scripts/smoke-doc-parser-async.sh', token)
}

for (const token of [
  'RagDemoSeedService',
  'DEMO_KB_NAME',
  'retrievalService.search',
  'chatService.sendMessage',
  '引用仍来自真实检索结果'
]) {
  requireToken('backend/src/test/java/com/anjing/smoke/RagDemoSmokeTest.java', token)
}

for (const token of [
  'class RagDemoSeedService',
  'RAG Demo Teaching KB',
  'agent-doc-parser',
  'documentEmbeddingService.embedChunks',
  'vectorStoreService.getVectorCount',
  'retrievalService.search',
  'autoSearch=1',
  './scripts/evaluate-rag-retrieval.sh',
  'autoSend=1',
  './scripts/create-demo-evidence.sh --dry-run',
  './scripts/collect-demo-evidence.sh --dry-run',
  './scripts/probe-doc-parser-boundary.sh --contract-only',
  './scripts/check-doc-parser-lifecycle.sh',
  './scripts/smoke-doc-parser-async.sh',
  'seed-rag-demo'
]) {
  requireToken('backend/src/main/java/com/anjing/demo/service/RagDemoSeedService.java', token)
}

for (const token of [
  '@Profile({"dev", "test"})',
  'ApiConstants.Test.RAG_DEMO_SEED',
  'ApiConstants.Test.RAG_DEMO_RETRIEVAL_EVALUATION',
  'APIResponse<RagRetrievalEvaluationResponse>',
  'APIResponse<RagDemoSeedResponse>'
]) {
  requireToken('backend/src/main/java/com/anjing/demo/controller/RagDemoController.java', token)
}

for (const token of [
  'class RagRetrievalEvaluationService',
  'ragDemoSeedService.seedTeachingDemo',
  'retrievalService.search',
  'recallAtK',
  'local-lexical',
  'chunk_rag_demo_teaching_001',
  'chunk_rag_demo_teaching_002',
  'chunk_rag_demo_teaching_003'
]) {
  requireToken('backend/src/main/java/com/anjing/demo/service/RagRetrievalEvaluationService.java', token)
}

for (const token of [
  'class RagRetrievalEvaluationResponse',
  'private Float recallAtK',
  'class CaseResult',
  'private Integer expectedRank'
]) {
  requireToken('backend/src/main/java/com/anjing/demo/model/response/RagRetrievalEvaluationResponse.java', token)
}

for (const token of [
  'class RagRetrievalEvaluationServiceTest',
  'evaluateDemoRetrievalShouldPassAllTeachingCases',
  'recallAtK',
  './scripts/evaluate-rag-retrieval.sh'
]) {
  requireToken('backend/src/test/java/com/anjing/demo/service/RagRetrievalEvaluationServiceTest.java', token)
}

for (const token of [
  'evaluate-rag-retrieval: ok',
  '/api/test/rag-demo/evaluate-retrieval',
  'recallAtK',
  'passedCases'
]) {
  requireToken('scripts/evaluate-rag-retrieval.sh', token)
}

for (const token of [
  '<artifactId>spring-boot-starter-parent</artifactId>',
  '<version>3.4.5</version>',
  '<java.version>17</java.version>',
  '<maven.compiler.source>17</maven.compiler.source>',
  '<maven.compiler.target>17</maven.compiler.target>',
  '<artifactId>spring-boot-starter-web</artifactId>',
  '<artifactId>spring-boot-starter-data-jpa</artifactId>',
  '<artifactId>spring-boot-starter-validation</artifactId>',
  '<artifactId>springdoc-openapi-starter-webmvc-api</artifactId>',
  '<artifactId>h2</artifactId>'
]) {
  requireToken('backend/pom.xml', token)
}

requireToken('backend/src/main/resources/application.yml', 'active: ${SPRING_PROFILES_ACTIVE:dev}')
requireToken('backend/src/main/resources/application.yml', 'mode: ${DOC_PARSER_MODE:sync}')
requireToken('backend/src/main/resources/application.yml', 'DOC_PARSER_ASYNC_MAX_POLL_ATTEMPTS')
requireToken('backend/src/main/resources/application.yml', 'DOC_PARSER_ASYNC_SUBMIT_ONLY_ENABLED')
requireToken('backend/src/main/resources/application.yml', 'DOC_PARSER_ASYNC_RECOVERY_ENABLED')
requireToken('backend/.env.example', 'SPRING_PROFILES_ACTIVE=dev')
requireToken('backend/.env.example', 'DOC_PARSER_MODE=sync')
requireToken('backend/.env.example', 'DOC_PARSER_ASYNC_SUBMIT_ONLY_ENABLED=false')
requireToken('backend/.env.example', 'DOC_PARSER_ASYNC_RECOVERY_ENABLED=false')
requireToken('backend/.env.example', 'EMBEDDING_PROVIDER=local-demo')
requireToken('backend/.env.example', 'KEYWORD_SEARCH_PROVIDER=local')
requireToken('backend/.env.example', 'LLM_PROVIDER=local-demo')
requireToken('backend/.env.example', 'RERANK_PROVIDER=local-demo')
requireToken('backend/src/main/resources/application-dev.yml', 'provider: ${EMBEDDING_PROVIDER:local-demo}')
requireToken('backend/src/main/resources/application-dev.yml', 'provider: ${KEYWORD_SEARCH_PROVIDER:local}')
requireToken('backend/src/main/resources/application-dev.yml', 'provider: ${LLM_PROVIDER:local-demo}')
requireToken('backend/src/main/resources/application-dev.yml', 'provider: ${RERANK_PROVIDER:local-demo}')
requireToken('project_document/LOCAL_STARTUP_GUIDE.md', '默认 profile 是 `dev`')
requireToken('README.md', '(cd backend && mvn spring-boot:run)')
requireToken('README.md', '# 3. frontend: http://localhost:20001')

for (const token of [
  '@ConfigurationProperties(prefix = "app.doc-parser")',
  'private String baseUrl = "http://localhost:9001"',
  'private String mode = "sync"',
  'private long timeout = 300000L',
  'private Async async = new Async()',
  'isAsyncMode',
  'maxPollAttempts',
  'pollIntervalMs',
  'submitOnlyEnabled',
  'recoveryEnabled',
  'recoveryFixedDelayMs',
  'recoveryBatchSize'
]) {
  requireToken('backend/src/main/java/com/anjing/config/properties/DocParserProperties.java', token)
}

if (frontendPackage.name !== 'agent-knowledge') {
  fail(`frontend package name must be agent-knowledge, got ${frontendPackage.name}`)
}
if (frontendPackage.type !== 'module') {
  fail('frontend/package.json must keep type=module')
}
if (frontendPackage.engines?.node !== '>=20.19.0') {
  fail('frontend node engine must stay aligned to scaffold: >=20.19.0')
}
if (!frontendPackage.packageManager?.startsWith('pnpm@10.10.0')) {
  fail('frontend packageManager must stay aligned to scaffold pnpm@10.10.0')
}

if (scaffoldStackContract.sourceProject !== 'infra-dev-scaffolding') {
  fail('scaffold stack contract must point to infra-dev-scaffolding')
}
if (scaffoldStackContract.frontend?.runtime !== 'vue-vite') {
  fail('scaffold stack contract frontend runtime must stay vue-vite')
}
if (scaffoldStackContract.frontend?.packageManagerPrefix !== 'pnpm@10.10.0') {
  fail('scaffold stack contract frontend package manager must stay pnpm@10.10.0')
}
if (scaffoldStackContract.frontend?.nodeEngine !== frontendPackage.engines?.node) {
  fail('scaffold stack contract frontend node engine must match frontend/package.json')
}
if (scaffoldStackContract.backend?.runtime !== 'spring-boot') {
  fail('scaffold stack contract backend runtime must stay spring-boot')
}
if (scaffoldStackContract.backend?.language !== 'Java') {
  fail('scaffold stack contract backend language must stay Java')
}
if (scaffoldStackContract.backend?.javaVersion !== '17') {
  fail('scaffold stack contract backend java version must stay 17')
}
if (scaffoldStackContract.backend?.frameworkVersion !== '3.4.5') {
  fail('scaffold stack contract backend Spring Boot version must stay 3.4.5')
}
if (scaffoldStackContract.docParser?.runtime !== docParserContract.runtime) {
  fail('scaffold stack contract doc-parser runtime must match doc-parser contract')
}
if (!scaffoldStackContract.docParser?.integration?.includes('Java backend calls doc-parser over HTTP')) {
  fail('scaffold stack contract must keep Java/doc-parser HTTP boundary')
}
for (const capability of [
  'APIResponse envelope',
  'PageResult pagination',
  'OpenAPI operation types',
  'RemoteHttpClient',
  'quality gate scripts'
]) {
  if (!scaffoldStackContract.inheritedCapabilities?.includes(capability)) {
    fail(`scaffold stack contract is missing inherited capability: ${capability}`)
  }
}
for (const nonGoal of [
  'do not replace the scaffold backend stack with Python',
  'do not embed Python parser dependencies into Spring Boot'
]) {
  if (!scaffoldStackContract.nonGoals?.includes(nonGoal)) {
    fail(`scaffold stack contract is missing non-goal: ${nonGoal}`)
  }
}

for (const [scriptName, token] of [
  ['dev', 'vite'],
  ['build', 'vue-tsc --noEmit && vite build'],
  ['lint', 'eslint'],
  ['clean:dev', 'tsx scripts/clean-dev.ts']
]) {
  const actual = frontendPackage.scripts?.[scriptName]
  if (!actual?.includes(token)) {
    fail(`frontend script ${scriptName} must include ${token}`)
  }
}

requireDependency(frontendPackage, 'dependencies', 'vue', '^3.5')
requireDependency(frontendPackage, 'dependencies', 'vue-router', '^4.')
requireDependency(frontendPackage, 'dependencies', 'pinia', '^3.')
requireDependency(frontendPackage, 'dependencies', 'element-plus', '^2.')
requireDependency(frontendPackage, 'dependencies', 'axios', '^1.')
requireDependency(frontendPackage, 'devDependencies', 'vite', '^7.')
requireDependency(frontendPackage, 'devDependencies', 'typescript', '~5.6')
requireDependency(frontendPackage, 'devDependencies', '@vitejs/plugin-vue', '^6.')
requireDependency(frontendPackage, 'devDependencies', 'vue-tsc', '~2.1')

if (platformContract.apiPrefix !== '/api') {
  fail('contracts/platform-contract.json apiPrefix must stay /api')
}
for (const field of ['code', 'message', 'data', 'timestamp', 'requestId']) {
  if (!platformContract.responseEnvelope?.fields?.includes(field)) {
    fail(`platform response envelope is missing field: ${field}`)
  }
}
for (const boundary of ['knowledge', 'retrieval', 'chat', 'auth', 'test']) {
  if (!serviceBoundaries.boundaries?.some((service) => service.id === boundary)) {
    fail(`contracts/service-boundaries.json is missing service: ${boundary}`)
  }
}
if (docParserContract.runtime !== 'python-fastapi') {
  fail('doc-parser contract runtime must stay python-fastapi')
}
if (!docParserContract.boundaries?.some((item) => item.includes('Java must call doc-parser over HTTP'))) {
  fail('doc-parser contract must state Java calls doc-parser over HTTP')
}
for (const status of ['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED']) {
  if (docParserContract.javaStatusMapping?.[status]?.progress === undefined) {
    fail(`doc-parser javaStatusMapping.${status} must define progress`)
  }
}
if (docParserContract.javaAsyncPolling?.service !== 'DocumentAsyncParsingService') {
  fail('doc-parser contract javaAsyncPolling.service must stay DocumentAsyncParsingService')
}
if (docParserContract.pythonAsyncContract?.submitResponse !== '_async_submit_success') {
  fail('doc-parser contract pythonAsyncContract.submitResponse must stay _async_submit_success')
}
if (docParserContract.pythonAsyncContract?.statusResponse !== '_async_status_response') {
  fail('doc-parser contract pythonAsyncContract.statusResponse must stay _async_status_response')
}
if (docParserContract.pythonAsyncContract?.smokeScript !== 'scripts/smoke-doc-parser-async.sh') {
  fail('doc-parser contract pythonAsyncContract.smokeScript must stay scripts/smoke-doc-parser-async.sh')
}
for (const key of ['task_id', 'request_id']) {
  if (!docParserContract.pythonAsyncContract?.statusRequestKeys?.includes(key)) {
    fail(`doc-parser contract pythonAsyncContract.statusRequestKeys must include ${key}`)
  }
}
if (docParserContract.javaAsyncPolling?.defaultMode !== 'sync') {
  fail('doc-parser contract javaAsyncPolling.defaultMode must stay sync')
}
if (docParserContract.javaAsyncPolling?.resultMapper !== 'DocumentParseResultMapper.fromClientResult') {
  fail('doc-parser contract javaAsyncPolling.resultMapper must stay DocumentParseResultMapper.fromClientResult')
}
if (docParserContract.javaAsyncPolling?.continuation !== 'DocumentProcessingService.continueAfterParsing') {
  fail('doc-parser contract javaAsyncPolling.continuation must stay DocumentProcessingService.continueAfterParsing')
}
if (docParserContract.javaAsyncPolling?.submitOnlyMode?.result !== 'DocumentParseResult.deferred') {
  fail('doc-parser contract javaAsyncPolling.submitOnlyMode.result must stay DocumentParseResult.deferred')
}
if (docParserContract.javaAsyncPolling?.submitOnlyMode?.defaultEnabled !== false) {
  fail('doc-parser contract javaAsyncPolling.submitOnlyMode.defaultEnabled must stay false')
}
if (docParserContract.javaAsyncPolling?.recoveryCoordinator?.service !== 'DocumentParserRecoveryPollingService') {
  fail('doc-parser contract javaAsyncPolling.recoveryCoordinator.service must stay DocumentParserRecoveryPollingService')
}
if (docParserContract.javaAsyncPolling?.recoveryCoordinator?.repository !== 'DocumentProcessingTaskRepository.findRecoverableParserTasks') {
  fail('doc-parser contract javaAsyncPolling.recoveryCoordinator.repository must stay DocumentProcessingTaskRepository.findRecoverableParserTasks')
}
if (docParserContract.javaAsyncPolling?.recoveryCoordinator?.defaultEnabled !== false) {
  fail('doc-parser contract javaAsyncPolling.recoveryCoordinator.defaultEnabled must stay false')
}
for (const field of ['parserTaskId', 'parserStatus', 'parserProgress', 'parserStatusUpdateCount', 'parserLastPolledAt']) {
  if (!docParserContract.javaAsyncPolling?.taskSnapshot?.fields?.includes(field)) {
    fail(`doc-parser contract javaAsyncPolling.taskSnapshot.fields must include ${field}`)
  }
}
if (retrievalAdapterContract.serviceId !== 'retrieval-adapter') {
  fail('retrieval adapter contract serviceId must stay retrieval-adapter')
}
if (retrievalAdapterContract.sourceProject !== 'infra-dev-scaffolding') {
  fail('retrieval adapter contract must point to infra-dev-scaffolding')
}
if (retrievalAdapterContract.runtime !== 'java-spring-boot') {
  fail('retrieval adapter contract runtime must stay java-spring-boot')
}
if (retrievalAdapterContract.vectorStore?.interface !== 'VectorStoreService') {
  fail('retrieval adapter contract vectorStore.interface must stay VectorStoreService')
}
if (retrievalAdapterContract.vectorStore?.properties !== 'VectorStoreProperties') {
  fail('retrieval adapter contract vectorStore.properties must stay VectorStoreProperties')
}
if (!retrievalAdapterContract.vectorStore?.productionProviderSkeletons?.includes('pgvector')) {
  fail('retrieval adapter contract vectorStore.productionProviderSkeletons must include pgvector')
}
if (retrievalAdapterContract.vectorStore?.sqlImplementation !== 'PgVectorStoreService') {
  fail('retrieval adapter contract vectorStore.sqlImplementation must stay PgVectorStoreService')
}
if (retrievalAdapterContract.keywordSearch?.interface !== 'KeywordSearchProvider') {
  fail('retrieval adapter contract keywordSearch.interface must stay KeywordSearchProvider')
}
if (retrievalAdapterContract.keywordSearch?.properties !== 'KeywordSearchProperties') {
  fail('retrieval adapter contract keywordSearch.properties must stay KeywordSearchProperties')
}
if (!retrievalAdapterContract.keywordSearch?.productionProviderSkeletons?.includes('bm25')) {
  fail('retrieval adapter contract keywordSearch.productionProviderSkeletons must include bm25')
}
if (!retrievalAdapterContract.keywordSearch?.productionProviderSkeletons?.includes('elasticsearch')) {
  fail('retrieval adapter contract keywordSearch.productionProviderSkeletons must include elasticsearch')
}
if (retrievalAdapterContract.keywordSearch?.rankingImplementation !== 'Bm25KeywordSearchProvider') {
  fail('retrieval adapter contract keywordSearch.rankingImplementation must stay Bm25KeywordSearchProvider')
}
if (retrievalAdapterContract.keywordSearch?.remoteImplementation !== 'ElasticsearchKeywordSearchProvider') {
  fail('retrieval adapter contract keywordSearch.remoteImplementation must stay ElasticsearchKeywordSearchProvider')
}
if (retrievalAdapterContract.keywordSearch?.targetService !== 'keyword-search-provider') {
  fail('retrieval adapter contract keywordSearch.targetService must stay keyword-search-provider')
}
if (retrievalAdapterContract.rerank?.properties !== 'RerankProperties') {
  fail('retrieval adapter contract rerank.properties must stay RerankProperties')
}
if (retrievalAdapterContract.rerank?.targetService !== 'rerank-provider') {
  fail('retrieval adapter contract rerank.targetService must stay rerank-provider')
}
for (const gate of [
  'scripts/check-retrieval-adapter-contract.js',
  'scripts/probe-retrieval-adapters.sh',
  'scripts/check-scaffold-alignment.js',
  'scripts/check-contracts.sh'
]) {
  if (!retrievalAdapterContract.qualityGates?.includes(gate)) {
    fail(`retrieval adapter contract qualityGates must include ${gate}`)
  }
}

for (const token of [
  '基于 `infra-dev-scaffolding` 生长出来',
  'project_document/PROJECT_CONSTRAINTS.md',
  'project_document/NEW_MODULE_GUIDE.md',
  'project_document/SCAFFOLD_ADOPTION_PROMPT.md',
  'project_document/UI_DESIGN_GUIDE.md',
  'project_document/DEMO_EVIDENCE.md',
  './scripts/quality-gate.sh'
]) {
  requireToken('README.md', token)
}

for (const token of [
  '底层技术栈、工程习惯和最佳实践来自脚手架',
  'retrieval-adapter-contract.json',
  '统一响应：`APIResponse<T>`',
  '标准分页：`PageResult<T>`',
  'DocumentIngestionService',
  'DocumentProcessingContextService',
  'DocumentProcessingProgressService',
  'DocumentParsingService',
  'DocumentChunkingService',
  'DocumentChunkPersistenceService',
  'DocumentEmbeddingService',
  'VectorStoreProperties',
  'PgVectorStoreService',
  'probe-retrieval-adapters.sh',
  'KeywordSearchProvider',
  'KeywordSearchProperties',
  'Bm25KeywordSearchProvider',
  'LocalKeywordSearchProvider',
  'Bm25KeywordSearchProvider',
  'ElasticsearchKeywordSearchProvider',
  'RetrievalResultEnrichmentService',
  'RetrievalHybridSearchService',
  'RerankProperties',
  'RerankProviderClient',
  'RetrievalRerankService',
  'RagPromptBuilderService',
  'RagChatOrchestrationService',
  'ChatConversationLifecycleService',
  'ChatConversationConfigService',
  'ChatMessagePersistenceService',
  '远程调用：`RemoteHttpClient`',
  '质量门禁：`scripts/check-*.js`'
]) {
  requireToken('project_document/SCAFFOLD_TO_RAG_AGENT_GUIDE.md', token)
}

for (const token of [
  'RAG Pipeline 教学视图',
  'infra-dev-scaffolding',
  'APIResponse / PageResult',
  'ApiConstants / ApiPaths',
  'RemoteHttpClient',
  'Python FastAPI doc-parser',
  'RagDemoService.seedRagDemo',
  'RagDemoService.evaluateRetrieval',
  'retrievalEvaluation',
  'recallAtKDisplay',
  'demoTeachingSteps',
  'displayEvidenceCommands',
  'Seed -> Evaluate -> Retrieval -> Chat -> Evidence',
  './scripts/create-demo-evidence.sh --dry-run',
  './scripts/probe-doc-parser-boundary.sh --contract-only',
  './scripts/check-doc-parser-lifecycle.sh',
  './scripts/smoke-doc-parser-async.sh',
  'Demo 数据已生成',
  '检索评估已通过',
  './scripts/seed-rag-demo.sh',
  './scripts/evaluate-rag-retrieval.sh',
  './scripts/smoke-rag-demo.sh'
]) {
  requireToken('frontend/src/views/pipeline/index.vue', token)
}

for (const token of [
  'docs/evidence/YYYY-MM-DD/',
  'Seed -> Evaluate -> Retrieval -> Chat -> Evidence',
  'screenshots/chat-with-citations.png',
  './scripts/create-demo-evidence.sh --dry-run',
  './scripts/collect-demo-evidence.sh --dry-run',
  './scripts/check-doc-parser-lifecycle.sh',
  './scripts/smoke-doc-parser-async.sh',
  './scripts/evaluate-rag-retrieval.sh'
]) {
  requireToken('docs/evidence/TEMPLATE.md', token)
  requireToken('project_document/DEMO_EVIDENCE.md', token)
}

for (const token of [
  'collect-demo-evidence: ok',
  '--include-doc-parser-live',
  'BACKEND_BASE_URL',
  'runtime/demo-routes.txt',
  'runtime/rag-demo-seed.json',
  'runtime/rag-retrieval-evaluation.json'
]) {
  requireToken('scripts/collect-demo-evidence.sh', token)
}

for (const token of [
  'probe-doc-parser-boundary: contract serviceId=',
  'DOC_PARSER_SERVICE_ID = "agent-doc-parser"',
  'docParserUrl("/parse_url")',
  '@app.post("/parse_url"',
  '--live'
]) {
  requireToken('scripts/probe-doc-parser-boundary.sh', token)
}

for (const token of [
  'check-doc-parser-lifecycle: statuses=',
  'javaStatusMapping',
  'applyDocParserStatus',
  'markDocParserStatus',
  'DocumentStatus.CHUNKING'
]) {
  requireToken('scripts/check-doc-parser-lifecycle.sh', token)
}

for (const token of [
  'class RagDemoService',
  'ApiPaths.test.ragDemoSeed',
  'ApiPaths.test.ragDemoRetrievalEvaluation',
  'request.post<RagDemoSeedResponse>',
  'request.post<RagRetrievalEvaluationResponse>',
  'normalizeSeedResponse'
]) {
  requireToken('frontend/src/api/demo.ts', token)
}

for (const token of [
  '@Facade(scene = "上传 RAG 文档"',
  'TransactionTemplate',
  'processDocumentAsync'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/DocumentIngestionService.java', token)
}

for (const token of [
  'DocumentIngestionService',
  'ingestionService.uploadDocument',
  'ingestionService.batchUploadDocuments',
  'ingestionService.reprocessDocument',
  'ingestionService.listDocumentTasks'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/controller/DocumentController.java', token)
}

requireAbsent(
  'backend/src/main/java/com/anjing/knowledge/service/DocumentService.java',
  /\bApplicationContext\b|\bTransactionSynchronization\b|\bprocessDocumentAsync\s*\(/,
  'RAG ingestion application orchestration'
)

for (const token of [
  'FILE_COUNTER',
  'String.format("file_%s_%04d"'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/DocumentService.java', token)
}

requireAbsent(
  'backend/src/main/java/com/anjing/knowledge/service/DocumentService.java',
  /\bSystem\.currentTimeMillis\s*\(|\bMath\.random\s*\(/,
  'random or direct millisecond file id generation'
)

for (const token of [
  'class DocumentParseResult',
  'class ChunkData',
  'static DocumentParseResult error'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/model/DocumentParseResult.java', token)
}

for (const token of [
  'class DocumentParseResultMapper',
  'fromClientResult',
  'DocParserClient.ParseResult',
  'DocumentParseResult.ChunkData'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/DocumentParseResultMapper.java', token)
}

for (const token of [
  'DocumentProcessingContextService',
  'contextService.loadContext',
  'DocumentProcessingProgressService',
  'progressService.markParsing',
  'progressService.markSucceeded',
  'DocumentChunkingService',
  'chunkingService.createChunks',
  'DocumentEmbeddingService',
  'documentEmbeddingService.embedChunks',
  'DocumentParsingService',
  'parsingService.parseDocument',
  'DocumentParseResult',
  'continueAfterParsing',
  'DocumentChunkPersistenceService',
  'chunkPersistenceService.saveChunks'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/DocumentProcessingService.java', token)
}

for (const token of [
  'class DocumentChunkingService',
  'createChunks',
  'simpleChunking',
  'estimateTokens'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/DocumentChunkingService.java', token)
}

requireAbsent(
  'backend/src/main/java/com/anjing/knowledge/service/DocumentProcessingService.java',
  /\bObjectMapper\b|private\s+List<Chunk>\s+simpleChunking|private\s+int\s+estimateTokens|private\s+String\s+generateChunkId|\bEmbeddingService\b|\bVectorStoreService\b|private\s+boolean\s+embedChunks|\bDocParserClient\b|\bFileStorageRepository\b|\bDocumentRepository\b|\bKnowledgeBaseRepository\b|\bDocumentService\b|\bDocumentProcessingTaskService\b|\bDocumentStatus\b|private\s+.*parseDocument|private\s+String\s+mapDocType|\bChunkRepository\b|\.setChunkNum\s*\(|\.setTokenNum\s*\(|\.saveAll\s*\(\s*chunks\s*\)|\.updateDocumentStatus\s*\(/,
  'context loading, progress, parsing, chunk, persistence or embedding implementation details'
)

for (const token of [
  'class DocumentProcessingContextService',
  'DocumentRepository',
  'KnowledgeBaseRepository',
  'loadContext',
  'DocumentProcessingContext'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/DocumentProcessingContextService.java', token)
}

for (const token of [
  'class DocumentProcessingProgressService',
  'DocumentService',
  'DocumentProcessingTaskService',
  'DocParserStatusMapper',
  'applyDocParserStatus',
  'markParsing',
  'markChunking',
  'markEmbedding',
  'markSucceeded',
  'markUnexpectedFailed',
  'status.getStatus()',
  'status.getProgress()'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/DocumentProcessingProgressService.java', token)
}

for (const token of [
  'parserStatus',
  'parserProgress',
  'parserMessage',
  'parserErrorMessage',
  'parserStatusUpdateCount',
  'parserLastPolledAt'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/model/entity/DocumentProcessingTask.java', token)
  requireToken('backend/src/main/java/com/anjing/knowledge/model/response/DocumentProcessingTaskResponse.java', token)
  requireToken('frontend/src/contracts/openapi/schemas.ts', token)
}

for (const token of [
  'hasParserSnapshot',
  'task-parser-snapshot',
  'task.parserStatus',
  'task.parserStatusUpdateCount'
]) {
  requireToken('frontend/src/views/knowledge/detail.vue', token)
}

for (const token of [
  'class DocumentChunkPersistenceService',
  'ChunkRepository',
  'DocumentRepository',
  'saveChunks',
  'PersistedChunks'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/DocumentChunkPersistenceService.java', token)
}

for (const token of [
  'class DocumentParsingService',
  'DocParserClient',
  'FileStorageRepository',
  'DocumentAsyncParsingService',
  'DocParserProperties',
  'docParserProperties.isAsyncMode()',
  'asyncParsingService.parseDocument',
  'parseDocument',
  'mapDocType'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/DocumentParsingService.java', token)
}

for (const token of [
  'class DocumentAsyncParsingService',
  'DocParserProperties',
  'submitAsyncParseDocument',
  'getAsyncParseStatus',
  'GlobalRequestContextHolder.requestIdOrNull()',
  'maxPollAttempts',
  'pollIntervalMs',
  'docParserProperties.getAsync().getMaxPollAttempts()',
  'docParserProperties.getAsync().getPollIntervalMs()',
  'docParserProperties.getAsync().isSubmitOnlyEnabled()',
  'DocumentParseResult.deferred',
  'applyDocParserStatus',
  'task_id 为空'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/DocumentAsyncParsingService.java', token)
}

for (const token of [
  'class DocumentParserRecoveryPollingService',
  '@Scheduled(fixedDelayString = "${app.doc-parser.async.recovery-fixed-delay-ms:15000}")',
  'pollRecoverableTasksOnce',
  'findRecoverableParserTasks',
  'DocumentProcessingService',
  'continueAfterParsing',
  'DocumentParseResultMapper',
  'isRecoveryEnabled'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/DocumentParserRecoveryPollingService.java', token)
}

for (const token of [
  'class DocumentSubmitOnlyRecoveryFlowTest',
  'uploadShouldSubmitParserTaskAndRecoveryShouldCompletePipeline',
  'docParserProperties.getAsync().setSubmitOnlyEnabled(true)',
  'docParserProperties.getAsync().setRecoveryEnabled(true)',
  'recoveryPollingService.pollRecoverableTasksOnce',
  'DocumentIngestionService'
]) {
  requireToken('backend/src/test/java/com/anjing/knowledge/service/DocumentSubmitOnlyRecoveryFlowTest.java', token)
}

for (const token of [
  'class DocumentEmbeddingService',
  'EmbeddingService',
  'VectorStoreService',
  'EmbeddingStatus.EMBEDDING',
  'EmbeddingStatus.FAILED',
  'EmbeddingStatus.EMBEDDED'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/DocumentEmbeddingService.java', token)
}

for (const token of [
  'RetrievalResultEnrichmentService',
  'RetrievalHybridSearchService',
  'RetrievalRerankService',
  'resultEnrichmentService.enrich',
  'hybridSearchService.merge',
  'rerankService.rerank',
  'annotateScoreExplanations',
  'setScoreExplanation'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/RetrievalService.java', token)
}

requireAbsent(
  'backend/src/main/java/com/anjing/knowledge/service/RetrievalService.java',
  /\bChunkRepository\b|\bDocumentRepository\b|\bObjectMapper\b|parseMetadata|chunkRepository\.findById|documentRepository\.findById/,
  'retrieval result enrichment implementation details'
)

for (const token of [
  'class RetrievalResultEnrichmentService',
  'SOURCE_VECTOR = "vector"',
  'KnowledgeBaseRepository',
  'DocumentRepository',
  'ChunkRepository',
  'ObjectMapper',
  'parseMetadata',
  'setRetrievalSource(SOURCE_VECTOR)',
  'SearchResult'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/RetrievalResultEnrichmentService.java', token)
}

for (const token of [
  'class RetrievalHybridSearchService',
  'KeywordSearchProvider',
  'keywordSearchProvider.search',
  'resultEnrichmentService.enrich',
  'RRF_K',
  'normalizedRrfScore',
  'setHybridScore',
  'setRetrievalSource'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/RetrievalHybridSearchService.java', token)
}

requireAbsent(
  'backend/src/main/java/com/anjing/knowledge/service/RetrievalHybridSearchService.java',
  /\bChunkRepository\b|ASCII_TERM_PATTERN|calculateKeywordScore|extractTerms|findByKbIdAndIsEnabledTrue/,
  'keyword search provider implementation details'
)

for (const token of [
  '@ConfigurationProperties(prefix = "app.vector-store")',
  'MEMORY_PROVIDER',
  'PGVECTOR_PROVIDER',
  'private Pgvector pgvector = new Pgvector()',
  'class Pgvector',
  'private String tableName = "rag_vectors"',
  'private boolean schemaInitializationEnabled = false'
]) {
  requireToken('backend/src/main/java/com/anjing/config/properties/VectorStoreProperties.java', token)
}

for (const token of [
  'class PgVectorStoreService',
  'implements VectorStoreService',
  '@ConditionalOnProperty(prefix = "app.vector-store", name = "provider", havingValue = "pgvector")',
  'JdbcTemplate',
  'SQL_IDENTIFIER_PATTERN',
  'CREATE EXTENSION IF NOT EXISTS vector',
  '?::vector',
  'embedding <=> ?::vector',
  'VectorSearchResult',
  'tableName must be a safe SQL identifier'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/PgVectorStoreService.java', token)
}

for (const token of [
  'class PgVectorStoreServiceTest',
  'upsertShouldUsePgvectorLiteralAndConfiguredTable',
  'searchShouldQueryEachKnowledgeBaseAndReturnTopScores',
  'tableNameShouldRejectUnsafeSqlIdentifier'
]) {
  requireToken('backend/src/test/java/com/anjing/knowledge/service/PgVectorStoreServiceTest.java', token)
}

for (const token of [
  'probe-retrieval-adapters: ok',
  'VECTOR_STORE_PROVIDER=pgvector',
  'KEYWORD_SEARCH_PROVIDER=bm25',
  'KEYWORD_SEARCH_PROVIDER=elasticsearch',
  'RERANK_PROVIDER=remote',
  'PgVectorStoreServiceTest,Bm25KeywordSearchProviderTest,ElasticsearchKeywordSearchProviderTest,RerankProviderClientTest',
  '--dry-run',
  '--contract-only'
]) {
  requireToken('scripts/probe-retrieval-adapters.sh', token)
}

for (const token of [
  'Retrieval Adapter Switch Guide',
  'memory -> pgvector',
  'local keyword -> bm25 -> elasticsearch',
  'local-demo rerank -> remote rerank',
  './scripts/probe-retrieval-adapters.sh --dry-run',
  'PgVectorStoreService',
  'Bm25KeywordSearchProvider',
  'ElasticsearchKeywordSearchProvider',
  'RerankProviderClient',
  'infra-dev-scaffolding'
]) {
  requireToken('project_document/RETRIEVAL_ADAPTER_SWITCH_GUIDE.md', token)
}

for (const token of [
  'interface KeywordSearchProvider',
  'List<KeywordSearchHit> search',
  'record KeywordSearchHit'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/KeywordSearchProvider.java', token)
}

for (const token of [
  '@ConfigurationProperties(prefix = "app.keyword-search")',
  'LOCAL_PROVIDER',
  'BM25_PROVIDER',
  'ELASTICSEARCH_PROVIDER',
  'private Bm25 bm25 = new Bm25()',
  'class Bm25',
  'private float k1 = 1.2f',
  'private float b = 0.75f',
  'private float minimumScore = 0.0f',
  'class Elasticsearch',
  'private String baseUrl = "http://localhost:9200"',
  'private String indexPrefix = "kb_"',
  'private List<String> fields'
]) {
  requireToken('backend/src/main/java/com/anjing/config/properties/KeywordSearchProperties.java', token)
}

for (const token of [
  'class LocalKeywordSearchProvider',
  'implements KeywordSearchProvider',
  '@ConditionalOnProperty(prefix = "app.keyword-search"',
  'findByKbIdAndIsEnabledTrue',
  'calculateKeywordScore',
  '本地关键词召回完成'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/LocalKeywordSearchProvider.java', token)
}

for (const token of [
  'class Bm25KeywordSearchProvider',
  'implements KeywordSearchProvider',
  '@ConditionalOnProperty(prefix = "app.keyword-search", name = "provider", havingValue = "bm25")',
  'KeywordSearchProperties',
  'bm25Score',
  'documentFrequency',
  'minimumScore',
  'BM25 关键词召回完成'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/Bm25KeywordSearchProvider.java', token)
}

for (const token of [
  'class ElasticsearchKeywordSearchProvider',
  'implements KeywordSearchProvider',
  '@ConditionalOnProperty(prefix = "app.keyword-search", name = "provider", havingValue = "elasticsearch")',
  'RemoteHttpClient',
  'RemoteHttpRequest.builder()',
  'TARGET_SERVICE = "keyword-search-provider"',
  'targetService(TARGET_SERVICE)',
  'searchBody',
  'extractHits'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/ElasticsearchKeywordSearchProvider.java', token)
}

for (const token of [
  'class LocalKeywordSearchProviderTest',
  'searchShouldRankLexicalMatches',
  'searchShouldSupportChineseTeachingQueries'
]) {
  requireToken('backend/src/test/java/com/anjing/knowledge/service/LocalKeywordSearchProviderTest.java', token)
}

for (const token of [
  'class Bm25KeywordSearchProviderTest',
  'searchShouldRankTermFrequencyAndInverseDocumentFrequency',
  'searchShouldSupportChineseTeachingQueries',
  'searchShouldRespectMinimumScore'
]) {
  requireToken('backend/src/test/java/com/anjing/knowledge/service/Bm25KeywordSearchProviderTest.java', token)
}

for (const token of [
  'class ElasticsearchKeywordSearchProviderTest',
  'searchShouldUseRemoteHttpClientAndMapHits',
  'searchShouldFallbackToHitIdAndRequestedKnowledgeBase',
  'keyword-search-provider'
]) {
  requireToken('backend/src/test/java/com/anjing/knowledge/service/ElasticsearchKeywordSearchProviderTest.java', token)
}

for (const token of [
  'class RetrievalHybridSearchServiceTest',
  'mergeShouldCombineVectorAndKeywordResultsWithRrf',
  'mergeShouldReturnKeywordOnlyResultsWhenVectorRecallIsEmpty'
]) {
  requireToken('backend/src/test/java/com/anjing/knowledge/service/RetrievalHybridSearchServiceTest.java', token)
}

requireToken(
  'backend/src/test/java/com/anjing/knowledge/service/RetrievalServiceTest.java',
  'searchShouldApplyHybridKeywordRecallWhenEnabled'
)

for (const token of [
  '@ConfigurationProperties(prefix = "app.rerank")',
  'LOCAL_DEMO_PROVIDER',
  'LOCAL_LEXICAL_PROVIDER',
  'isRemoteProvider',
  'resolveModel',
  'remoteProviderLabel'
]) {
  requireToken('backend/src/main/java/com/anjing/config/properties/RerankProperties.java', token)
}

for (const token of [
  'retrieval-adapter-contract.json',
  'VectorStoreService',
  'VectorStoreProperties',
  'PgVectorStoreService',
  'RETRIEVAL_ADAPTER_SWITCH_GUIDE.md',
  'probe-retrieval-adapters.sh',
  'KeywordSearchProvider',
  'KeywordSearchProperties',
  'Bm25KeywordSearchProvider',
  'ElasticsearchKeywordSearchProvider',
  'keyword-search-provider',
  'RerankProperties',
  'RemoteHttpClient',
  'Milvus',
  'pgvector',
  'Elasticsearch',
  'BM25',
  'Python doc-parser'
]) {
  requireToken('project_document/RETRIEVAL_ADAPTER_GUIDE.md', token)
}

for (const token of [
  'class RerankProviderClient',
  'RemoteHttpClient',
  'RemoteHttpRequest.builder()',
  'targetService("rerank-provider")',
  'app.rerank.api-url',
  'extractScores'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/RerankProviderClient.java', token)
}

for (const token of [
  'class RerankProviderClientTest',
  'rerankShouldUseRemoteHttpClientAndMapRankedScores',
  'rerankShouldMapDirectScoresResponse'
]) {
  requireToken('backend/src/test/java/com/anjing/knowledge/service/RerankProviderClientTest.java', token)
}

for (const token of [
  'class RetrievalRerankService',
  'DEFAULT_RERANK_PROVIDER',
  'RerankProviderClient',
  'rerankProviderClient.rerank',
  'SIMILARITY_WEIGHT',
  'RERANK_WEIGHT',
  'calculateRerankScore',
  'setRerankProvider',
  'local-lexical'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/RetrievalRerankService.java', token)
}

for (const token of [
  'class RetrievalRerankServiceTest',
  'rerankShouldPreferLexicallyRelevantContent',
  'rerankShouldHandleChineseTeachingQueries'
]) {
  requireToken('backend/src/test/java/com/anjing/knowledge/service/RetrievalRerankServiceTest.java', token)
}

requireToken(
  'backend/src/test/java/com/anjing/knowledge/service/RetrievalServiceTest.java',
  'searchShouldApplyLocalRerankWhenEnabled'
)

for (const token of [
  'rank',
  'scoreExplanation',
  'rerankProvider',
  'keywordScore',
  'hybridScore',
  'retrievalSource'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/model/response/SearchResult.java', token)
  requireToken('frontend/src/views/retrieval/index.vue', token)
}

for (const token of [
  'private Boolean hybrid = false',
  '向量召回 + 本地关键词召回 + RRF 合并'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/model/request/SearchRequest.java', token)
}

for (const token of [
  'hybrid?: boolean',
  'contextTrace?: RagContextTrace',
  'export interface RagContextTrace',
  'includedChunks?: IncludedChunk[]',
  'promptSections?: string[]',
  'keywordScore?: number',
  'hybridScore?: number',
  'retrievalSource?: string',
  'rerankProvider?: string',
  'scoreExplanation?: string'
]) {
  requireToken('frontend/src/contracts/openapi/schemas.ts', token)
}

for (const token of [
  'class ReferenceInfo',
  'private RagContextTrace contextTrace',
  'private Integer rank',
  'private String retrievalSource',
  'private Float keywordScore',
  'private Float hybridScore',
  'private Float rerankScore',
  'private String scoreExplanation'
]) {
  requireToken('backend/src/main/java/com/anjing/chat/model/response/MessageResponse.java', token)
}

for (const token of [
  'route.query.q',
  'route.query.kbIds',
  'route.query.autoSearch',
  '已带入 Demo 检索参数'
]) {
  requireToken('frontend/src/views/retrieval/index.vue', token)
}

for (const token of [
  'applyRetrievalHandoff',
  'handleDemoAutoSend',
  'route.query.q',
  'route.query.kbIds',
  'route.query.autoSend',
  '已带入检索调试参数',
  '已进入 Demo 自动问答',
  'formatReferenceTrace',
  'ref-trace-chip',
  'ref-score-explanation',
  'message-context-trace',
  'formatContextTraceStats',
  'formatPromptSection',
  'scoreExplanation'
]) {
  requireToken('frontend/src/views/chat/index.vue', token)
}

for (const token of [
  'RagPromptBuilderService',
  'promptBuilderService.buildRagContext',
  'RagGenerationResult',
  'contextTrace'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/LLMService.java', token)
}

requireAbsent(
  'backend/src/main/java/com/anjing/knowledge/service/LLMService.java',
  /private\s+String\s+buildRAGSystemPrompt|知识库参考内容|绝对禁止幻觉/,
  'RAG prompt assembly implementation details'
)

for (const token of [
  'class RagPromptBuilderService',
  'RagPromptContext',
  'RagContextTrace',
  'retrieval-context-to-system-prompt',
  'buildRagSystemPrompt',
  'buildRagContext',
  'includedChunks',
  'SearchResult',
  '知识库参考内容',
  '绝对禁止幻觉'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/RagPromptBuilderService.java', token)
}

for (const token of [
  'class RagContextTrace',
  'private String assemblyStrategy',
  'private Integer promptCharCount',
  'private Integer contextCharCount',
  'private List<IncludedChunk> includedChunks',
  'class IncludedChunk'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/model/response/RagContextTrace.java', token)
}

for (const token of [
  'RagChatOrchestrationService',
  'ragChatOrchestrationService.generateAnswer',
  'ChatConversationLifecycleService',
  'chatConversationLifecycleService.createConversation',
  'chatConversationLifecycleService.requireConversation',
  'chatConversationLifecycleService.listConversations',
  'chatConversationLifecycleService.deleteConversation',
  'chatConversationLifecycleService.updateTitle',
  'chatConversationLifecycleService.incrementMessageCount',
  'ChatConversationConfigService',
  'chatConversationConfigService.resolveKnowledgeBaseIds',
  'chatConversationConfigService.syncKnowledgeBaseIds',
  'ChatMessagePersistenceService',
  'chatMessagePersistenceService.saveUserMessage',
  'chatMessagePersistenceService.saveAssistantMessage',
  'chatMessagePersistenceService.listMessages'
]) {
  requireToken('backend/src/main/java/com/anjing/chat/service/ChatService.java', token)
}

requireAbsent(
  'backend/src/main/java/com/anjing/chat/service/ChatService.java',
  /\bRetrievalService\b|\bLLMService\b|\bSearchRequest\b|\bMessageRepository\b|\bConversationRepository\b|\bObjectMapper\b|\bTypeReference\b|\bDateUtils\b|retrieveKnowledge|generateResponse|buildHistoryMessages|generateRAGResponse|fromJsonList|toJson|generateConversationId|private\s+Message\s+saveMessage|generateMessageId|MSG_COUNTER|CONV_COUNTER|PageRequest/,
  'RAG chat orchestration, message persistence, conversation config or conversation lifecycle implementation details'
)

for (const token of [
  'class RagChatOrchestrationService',
  'MessageRepository',
  'RetrievalService',
  'LLMService',
  'SearchRequest',
  'generateAnswer',
  'buildHistoryMessages',
  'generateRAGResponse',
  'RagChatAnswer'
]) {
  requireToken('backend/src/main/java/com/anjing/chat/service/RagChatOrchestrationService.java', token)
}

for (const token of [
  'class ChatConversationLifecycleService',
  'ConversationRepository',
  'ChatConversationConfigService',
  'chatConversationConfigService.applyCreateRequest',
  'ChatMessagePersistenceService',
  'chatMessagePersistenceService.deleteConversationMessages',
  'createConversation',
  'requireConversation',
  'listConversations',
  'deleteConversation',
  'updateTitle',
  'incrementMessageCount',
  'generateConversationId'
]) {
  requireToken('backend/src/main/java/com/anjing/chat/service/ChatConversationLifecycleService.java', token)
}

for (const token of [
  'class ChatConversationConfigService',
  'ObjectMapper',
  'applyCreateRequest',
  'resolveKnowledgeBaseIds',
  'syncKnowledgeBaseIds',
  'deserializeKnowledgeBaseIds',
  'toJson'
]) {
  requireToken('backend/src/main/java/com/anjing/chat/service/ChatConversationConfigService.java', token)
}

for (const token of [
  'class ChatMessagePersistenceService',
  'MessageRepository',
  'ObjectMapper',
  'saveUserMessage',
  'saveAssistantMessage',
  'listMessages',
  'deleteConversationMessages',
  'generateMessageId'
]) {
  requireToken('backend/src/main/java/com/anjing/chat/service/ChatMessagePersistenceService.java', token)
}

for (const token of [
  'FastAPI',
  '@app.get("/health"',
  '@app.post("/parse"',
  '@app.post("/parse_url"',
  '@app.post("/loader/deep_parse/async"',
  '@app.post("/loader/status"',
  '_async_submit_success',
  '_async_status_response',
  '_run_uploaded_file_parse_task',
  '_run_url_file_parse_task',
  '"task_id"',
  '"SUCCEEDED"'
]) {
  requireToken('doc-parser/kparser/app.py', token)
}

for (const token of [
  'node scripts/check-scaffold-alignment.js',
  'node scripts/check-service-boundaries.js'
]) {
  requireToken('scripts/check-contracts.sh', token)
}

requireAbsentInDir(
  'backend/src/main/java',
  /\b(fastapi|uvicorn|pdfplumber|pypdf|python-docx|kparser)\b/i,
  'Python doc-parser implementation dependency'
)

console.log('check-scaffold-alignment: ok')
