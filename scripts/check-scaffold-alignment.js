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
const platformContract = readJson('contracts/platform-contract.json')
const serviceBoundaries = readJson('contracts/service-boundaries.json')
const docParserContract = readJson('contracts/doc-parser-contract.json')

for (const file of [
  'README.md',
  'CONTRIBUTING.md',
  'backend/pom.xml',
  'frontend/package.json',
  'frontend/vite.config.ts',
  'contracts/platform-contract.json',
  'contracts/service-boundaries.json',
  'contracts/doc-parser-contract.json',
  'scripts/check-template.sh',
  'scripts/check-contracts.sh',
  'scripts/quality-gate.sh',
  'project_document/PROJECT_CONSTRAINTS.md',
  'project_document/NEW_MODULE_GUIDE.md',
  'project_document/SCAFFOLD_TO_RAG_AGENT_GUIDE.md',
  'project_document/DOC_PARSER_SERVICE_GUIDE.md',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentIngestionService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentChunkingService.java',
  'backend/src/main/java/com/anjing/knowledge/service/DocumentEmbeddingService.java',
  'doc-parser/kparser/app.py'
]) {
  read(file)
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
  'DocumentChunkingService',
  'DocumentEmbeddingService',
  '远程调用：`RemoteHttpClient`',
  '质量门禁：`scripts/check-*.js`'
]) {
  requireToken('project_document/SCAFFOLD_TO_RAG_AGENT_GUIDE.md', token)
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
  'DocumentChunkingService',
  'chunkingService.createChunks',
  'DocumentEmbeddingService',
  'documentEmbeddingService.embedChunks'
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
  /\bObjectMapper\b|private\s+List<Chunk>\s+simpleChunking|private\s+int\s+estimateTokens|private\s+String\s+generateChunkId|\bEmbeddingService\b|\bVectorStoreService\b|private\s+boolean\s+embedChunks/,
  'chunk or embedding implementation details'
)

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
