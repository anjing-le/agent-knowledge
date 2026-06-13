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

for (const file of [
  'README.md',
  'CONTRIBUTING.md',
  'backend/pom.xml',
  'frontend/package.json',
  'frontend/vite.config.ts',
  'frontend/src/views/pipeline/index.vue',
  'contracts/platform-contract.json',
  'contracts/scaffold-stack-contract.json',
  'contracts/service-boundaries.json',
  'contracts/doc-parser-contract.json',
  'scripts/check-template.sh',
  'scripts/check-contracts.sh',
  'scripts/quality-gate.sh',
  'scripts/seed-rag-demo.sh',
  'scripts/smoke-rag-demo.sh',
  'project_document/PROJECT_CONSTRAINTS.md',
  'project_document/NEW_MODULE_GUIDE.md',
  'project_document/SCAFFOLD_TO_RAG_AGENT_GUIDE.md',
  'project_document/DOC_PARSER_SERVICE_GUIDE.md',
  'backend/src/main/java/com/anjing/demo/controller/RagDemoController.java',
  'backend/src/main/java/com/anjing/demo/model/response/RagDemoSeedResponse.java',
  'backend/src/main/java/com/anjing/demo/service/RagDemoSeedService.java',
  'backend/src/main/java/com/anjing/chat/service/ChatConversationLifecycleService.java',
  'backend/src/main/java/com/anjing/chat/service/ChatConversationConfigService.java',
  'backend/src/main/java/com/anjing/chat/service/ChatMessagePersistenceService.java',
  'backend/src/main/java/com/anjing/chat/service/RagChatOrchestrationService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentIngestionService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentProcessingContextService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentProcessingProgressService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentParsingService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentChunkingService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentChunkPersistenceService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentEmbeddingService.java',
  'backend/src/main/java/com/anjing/knowledge/service/RetrievalResultEnrichmentService.java',
  'backend/src/main/java/com/anjing/knowledge/service/RagPromptBuilderService.java',
  'backend/src/test/java/com/anjing/demo/service/RagDemoSeedServiceTest.java',
  'backend/src/test/java/com/anjing/smoke/RagDemoSmokeTest.java',
  'doc-parser/kparser/app.py'
]) {
  read(file)
}

for (const token of [
  'RagDemoSeedService',
  'api/test/rag-demo/seed',
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
  'seed-rag-demo'
]) {
  requireToken('backend/src/main/java/com/anjing/demo/service/RagDemoSeedService.java', token)
}

for (const token of [
  '@Profile({"dev", "test"})',
  'ApiConstants.Test.RAG_DEMO_SEED',
  'APIResponse<RagDemoSeedResponse>'
]) {
  requireToken('backend/src/main/java/com/anjing/demo/controller/RagDemoController.java', token)
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
requireToken('backend/.env.example', 'SPRING_PROFILES_ACTIVE=dev')
requireToken('backend/.env.example', 'EMBEDDING_PROVIDER=local-demo')
requireToken('backend/.env.example', 'LLM_PROVIDER=local-demo')
requireToken('backend/src/main/resources/application-dev.yml', 'provider: ${EMBEDDING_PROVIDER:local-demo}')
requireToken('backend/src/main/resources/application-dev.yml', 'provider: ${LLM_PROVIDER:local-demo}')
requireToken('project_document/LOCAL_STARTUP_GUIDE.md', '默认 profile 是 `dev`')
requireToken('README.md', '(cd backend && mvn spring-boot:run)')
requireToken('README.md', '# 3. frontend: http://localhost:20001')

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
  '统一响应：`APIResponse<T>`',
  '标准分页：`PageResult<T>`',
  'DocumentIngestionService',
  'DocumentProcessingContextService',
  'DocumentProcessingProgressService',
  'DocumentParsingService',
  'DocumentChunkingService',
  'DocumentChunkPersistenceService',
  'DocumentEmbeddingService',
  'RetrievalResultEnrichmentService',
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
  './scripts/seed-rag-demo.sh',
  './scripts/smoke-rag-demo.sh'
]) {
  requireToken('frontend/src/views/pipeline/index.vue', token)
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
  'markParsing',
  'markChunking',
  'markEmbedding',
  'markSucceeded',
  'markUnexpectedFailed'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/DocumentProcessingProgressService.java', token)
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
  'parseDocument',
  'mapDocType'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/DocumentParsingService.java', token)
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
  'resultEnrichmentService.enrich',
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
  'KnowledgeBaseRepository',
  'DocumentRepository',
  'ChunkRepository',
  'ObjectMapper',
  'parseMetadata',
  'SearchResult'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/RetrievalResultEnrichmentService.java', token)
}

for (const token of [
  'rank',
  'scoreExplanation'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/model/response/SearchResult.java', token)
  requireToken('frontend/src/views/retrieval/index.vue', token)
}

for (const token of [
  'RagPromptBuilderService',
  'promptBuilderService.buildRagSystemPrompt'
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
  'buildRagSystemPrompt',
  'SearchResult',
  '知识库参考内容',
  '绝对禁止幻觉'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/RagPromptBuilderService.java', token)
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
  '@app.post("/parse_url"'
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
