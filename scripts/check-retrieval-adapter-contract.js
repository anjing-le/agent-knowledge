#!/usr/bin/env node
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')

function fail(message) {
  console.error(`check-retrieval-adapter-contract: ${message}`)
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
  if (!read(relativeFile).includes(token)) {
    fail(`${relativeFile} is missing token: ${token}`)
  }
}

function requireArrayValue(object, pathLabel, value) {
  if (!Array.isArray(object) || !object.includes(value)) {
    fail(`${pathLabel} must include ${value}`)
  }
}

const contract = readJson('contracts/retrieval-adapter-contract.json')

if (contract.serviceId !== 'retrieval-adapter') {
  fail('serviceId must be retrieval-adapter')
}
if (contract.sourceProject !== 'infra-dev-scaffolding') {
  fail('sourceProject must be infra-dev-scaffolding')
}
if (contract.runtime !== 'java-spring-boot') {
  fail('runtime must be java-spring-boot')
}

for (const boundary of [
  'Business retrieval orchestration must depend on interfaces and stage services, not concrete middleware clients.',
  'Remote rerank adapters must use RemoteHttpClient and targetService=rerank-provider.',
  'Python doc-parser remains an independent FastAPI service and must not own retrieval adapters.'
]) {
  requireArrayValue(contract.boundaries, 'boundaries', boundary)
}

if (contract.vectorStore?.interface !== 'VectorStoreService') {
  fail('vectorStore.interface must be VectorStoreService')
}
if (contract.vectorStore?.properties !== 'VectorStoreProperties') {
  fail('vectorStore.properties must be VectorStoreProperties')
}
if (contract.vectorStore?.defaultProvider !== 'memory') {
  fail('vectorStore.defaultProvider must be memory')
}
requireArrayValue(contract.vectorStore?.productionProviderSkeletons, 'vectorStore.productionProviderSkeletons', 'pgvector')
if (contract.vectorStore?.sqlImplementation !== 'PgVectorStoreService') {
  fail('vectorStore.sqlImplementation must be PgVectorStoreService')
}
for (const provider of ['milvus', 'hosted-vector-db']) {
  requireArrayValue(contract.vectorStore?.futureProviders, 'vectorStore.futureProviders', provider)
}
for (const key of [
  'app.vector-store.provider',
  'VECTOR_STORE_PROVIDER',
  'VECTOR_STORE_COLLECTION_PREFIX',
  'VECTOR_STORE_PGVECTOR_TABLE_NAME',
  'VECTOR_STORE_PGVECTOR_SCHEMA_INITIALIZATION_ENABLED'
]) {
  requireArrayValue(contract.vectorStore?.configKeys, 'vectorStore.configKeys', key)
}

if (contract.keywordSearch?.interface !== 'KeywordSearchProvider') {
  fail('keywordSearch.interface must be KeywordSearchProvider')
}
if (contract.keywordSearch?.properties !== 'KeywordSearchProperties') {
  fail('keywordSearch.properties must be KeywordSearchProperties')
}
if (contract.keywordSearch?.defaultProvider !== 'local') {
  fail('keywordSearch.defaultProvider must be local')
}
requireArrayValue(contract.keywordSearch?.productionProviderSkeletons, 'keywordSearch.productionProviderSkeletons', 'bm25')
requireArrayValue(contract.keywordSearch?.productionProviderSkeletons, 'keywordSearch.productionProviderSkeletons', 'elasticsearch')
if (contract.keywordSearch?.rankingImplementation !== 'Bm25KeywordSearchProvider') {
  fail('keywordSearch.rankingImplementation must be Bm25KeywordSearchProvider')
}
if (contract.keywordSearch?.remoteImplementation !== 'ElasticsearchKeywordSearchProvider') {
  fail('keywordSearch.remoteImplementation must be ElasticsearchKeywordSearchProvider')
}
if (contract.keywordSearch?.targetService !== 'keyword-search-provider') {
  fail('keywordSearch.targetService must be keyword-search-provider')
}
for (const key of [
  'KEYWORD_SEARCH_PROVIDER',
  'KEYWORD_SEARCH_BM25_K1',
  'KEYWORD_SEARCH_BM25_B',
  'KEYWORD_SEARCH_BM25_MINIMUM_SCORE',
  'KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL',
  'KEYWORD_SEARCH_ELASTICSEARCH_INDEX_PREFIX',
  'KEYWORD_SEARCH_ELASTICSEARCH_API_KEY'
]) {
  requireArrayValue(contract.keywordSearch?.configKeys, 'keywordSearch.configKeys', key)
}

if (contract.rerank?.orchestrator !== 'RetrievalRerankService') {
  fail('rerank.orchestrator must be RetrievalRerankService')
}
if (contract.rerank?.remoteClient !== 'RerankProviderClient') {
  fail('rerank.remoteClient must be RerankProviderClient')
}
if (contract.rerank?.properties !== 'RerankProperties') {
  fail('rerank.properties must be RerankProperties')
}
if (contract.rerank?.targetService !== 'rerank-provider') {
  fail('rerank.targetService must be rerank-provider')
}
for (const key of [
  'app.rerank.provider',
  'app.rerank.api-url',
  'RERANK_PROVIDER',
  'RERANK_API_URL',
  'RERANK_MODEL'
]) {
  requireArrayValue(contract.rerank?.configKeys, 'rerank.configKeys', key)
}

for (const field of [
  'rank',
  'scoreExplanation',
  'retrievalSource',
  'keywordScore',
  'hybridScore',
  'rerankScore',
  'rerankProvider'
]) {
  requireArrayValue(contract.teachingTraceFields, 'teachingTraceFields', field)
  requireToken('backend/src/main/java/com/anjing/knowledge/model/response/SearchResult.java', field)
}

if (contract.runtimeStatusEndpoint?.path !== '/api/retrieval/adapters/status') {
  fail('runtimeStatusEndpoint.path must stay /api/retrieval/adapters/status')
}
if (contract.runtimeStatusEndpoint?.controller !== 'RetrievalController.adapterStatus') {
  fail('runtimeStatusEndpoint.controller must stay RetrievalController.adapterStatus')
}
if (contract.runtimeStatusEndpoint?.service !== 'RetrievalAdapterStatusService') {
  fail('runtimeStatusEndpoint.service must stay RetrievalAdapterStatusService')
}
if (contract.runtimeStatusEndpoint?.frontendApi !== 'RetrievalService.adapterStatus') {
  fail('runtimeStatusEndpoint.frontendApi must stay RetrievalService.adapterStatus')
}

for (const file of [
  'backend/src/main/java/com/anjing/knowledge/service/VectorStoreService.java',
  'backend/src/main/java/com/anjing/knowledge/service/MemoryVectorStoreService.java',
  'backend/src/main/java/com/anjing/knowledge/service/PgVectorStoreService.java',
  'backend/src/main/java/com/anjing/config/properties/VectorStoreProperties.java',
  'backend/src/main/java/com/anjing/knowledge/service/KeywordSearchProvider.java',
  'backend/src/main/java/com/anjing/knowledge/service/LocalKeywordSearchProvider.java',
  'backend/src/main/java/com/anjing/knowledge/service/Bm25KeywordSearchProvider.java',
  'backend/src/main/java/com/anjing/knowledge/service/ElasticsearchKeywordSearchProvider.java',
  'backend/src/main/java/com/anjing/config/properties/KeywordSearchProperties.java',
  'backend/src/main/java/com/anjing/knowledge/service/RetrievalHybridSearchService.java',
  'backend/src/main/java/com/anjing/knowledge/service/RetrievalRerankService.java',
  'backend/src/main/java/com/anjing/knowledge/service/RerankProviderClient.java',
  'backend/src/main/java/com/anjing/knowledge/model/response/RetrievalAdapterStatusResponse.java',
  'backend/src/main/java/com/anjing/knowledge/service/RetrievalAdapterStatusService.java',
  'backend/src/main/java/com/anjing/config/properties/RerankProperties.java',
  'backend/src/main/java/com/anjing/knowledge/controller/RetrievalController.java',
  'backend/src/main/resources/application.yml',
  'backend/.env.example',
  'frontend/src/api/retrieval.ts',
  'frontend/src/views/pipeline/index.vue',
  'project_document/RETRIEVAL_ADAPTER_GUIDE.md',
  'project_document/RETRIEVAL_ADAPTER_SWITCH_GUIDE.md',
  'scripts/probe-retrieval-adapters.sh'
]) {
  read(file)
}

requireToken(
  'backend/src/main/java/com/anjing/knowledge/service/MemoryVectorStoreService.java',
  '@ConditionalOnProperty(prefix = "app.vector-store"'
)
requireToken(
  'backend/src/main/java/com/anjing/knowledge/service/PgVectorStoreService.java',
  '@ConditionalOnProperty(prefix = "app.vector-store"'
)
requireToken('backend/src/main/java/com/anjing/knowledge/service/PgVectorStoreService.java', 'JdbcTemplate')
requireToken('backend/src/main/java/com/anjing/knowledge/service/PgVectorStoreService.java', 'SQL_IDENTIFIER_PATTERN')
requireToken('backend/src/main/java/com/anjing/knowledge/service/PgVectorStoreService.java', '?::vector')
requireToken('backend/src/main/java/com/anjing/knowledge/service/PgVectorStoreService.java', 'embedding <=> ?::vector')
requireToken(
  'backend/src/main/java/com/anjing/config/properties/VectorStoreProperties.java',
  '@ConfigurationProperties(prefix = "app.vector-store")'
)
requireToken('backend/src/main/java/com/anjing/config/properties/VectorStoreProperties.java', 'PGVECTOR_PROVIDER')
requireToken('backend/src/main/java/com/anjing/config/properties/VectorStoreProperties.java', 'private Pgvector pgvector = new Pgvector()')
requireToken(
  'backend/src/main/java/com/anjing/knowledge/service/LocalKeywordSearchProvider.java',
  '@ConditionalOnProperty(prefix = "app.keyword-search"'
)
requireToken(
  'backend/src/main/java/com/anjing/knowledge/service/Bm25KeywordSearchProvider.java',
  '@ConditionalOnProperty(prefix = "app.keyword-search"'
)
requireToken('backend/src/main/java/com/anjing/knowledge/service/Bm25KeywordSearchProvider.java', 'bm25Score')
requireToken('backend/src/main/java/com/anjing/knowledge/service/Bm25KeywordSearchProvider.java', 'documentFrequency')
requireToken('backend/src/main/java/com/anjing/knowledge/service/Bm25KeywordSearchProvider.java', 'minimumScore')
requireToken(
  'backend/src/main/java/com/anjing/knowledge/service/ElasticsearchKeywordSearchProvider.java',
  '@ConditionalOnProperty(prefix = "app.keyword-search"'
)
requireToken(
  'backend/src/main/java/com/anjing/knowledge/service/ElasticsearchKeywordSearchProvider.java',
  'targetService(TARGET_SERVICE)'
)
requireToken(
  'backend/src/main/java/com/anjing/knowledge/service/ElasticsearchKeywordSearchProvider.java',
  'RemoteHttpClient'
)
requireToken(
  'backend/src/main/java/com/anjing/config/properties/KeywordSearchProperties.java',
  '@ConfigurationProperties(prefix = "app.keyword-search")'
)
requireToken('backend/src/main/java/com/anjing/config/properties/KeywordSearchProperties.java', 'BM25_PROVIDER')
requireToken('backend/src/main/java/com/anjing/config/properties/KeywordSearchProperties.java', 'private Bm25 bm25 = new Bm25()')
requireToken('backend/src/main/java/com/anjing/config/properties/KeywordSearchProperties.java', 'ELASTICSEARCH_PROVIDER')
requireToken(
  'backend/src/main/java/com/anjing/knowledge/service/RerankProviderClient.java',
  'targetService("rerank-provider")'
)
requireToken(
  'backend/src/main/java/com/anjing/config/properties/RerankProperties.java',
  '@ConfigurationProperties(prefix = "app.rerank")'
)
requireToken('backend/src/main/java/com/anjing/config/properties/RerankProperties.java', 'LOCAL_DEMO_PROVIDER')
requireToken('backend/src/main/java/com/anjing/config/properties/RerankProperties.java', 'LOCAL_LEXICAL_PROVIDER')
requireToken('backend/src/main/java/com/anjing/config/properties/RerankProperties.java', 'isRemoteProvider')
requireToken('backend/src/main/java/com/anjing/config/properties/RerankProperties.java', 'resolveModel')
requireToken('backend/src/main/java/com/anjing/config/properties/RerankProperties.java', 'remoteProviderLabel')

for (const token of [
  'class RetrievalAdapterStatusService',
  'VectorStoreProperties',
  'KeywordSearchProperties',
  'RerankProperties',
  'DocParserProperties',
  'KEYWORD_SEARCH_PROVIDER=bm25',
  'contracts/retrieval-adapter-contract.json',
  'contracts/doc-parser-contract.json'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/service/RetrievalAdapterStatusService.java', token)
}

for (const token of [
  'class RetrievalAdapterStatusResponse',
  'class AdapterStatus',
  'private String currentProvider',
  'private String currentImplementation',
  'private String switchCommand',
  'private String runtimeStatus'
]) {
  requireToken('backend/src/main/java/com/anjing/knowledge/model/response/RetrievalAdapterStatusResponse.java', token)
}

requireToken('backend/src/main/java/com/anjing/knowledge/controller/RetrievalController.java', 'ApiConstants.Retrieval.ADAPTER_STATUS')
requireToken('frontend/src/api/retrieval.ts', "openApiRequest('adapterStatus'")
requireToken('frontend/src/views/pipeline/index.vue', 'RetrievalService.adapterStatus')
requireToken('frontend/src/views/pipeline/index.vue', 'curl -fsS http://localhost:10001/api/retrieval/adapters/status')
requireToken('scripts/collect-demo-evidence.sh', 'runtime/retrieval-adapter-status.json')
requireToken('scripts/collect-demo-evidence.sh', '/api/retrieval/adapters/status')

for (const token of [
  'provider: ${VECTOR_STORE_PROVIDER:memory}',
  'table-name: ${VECTOR_STORE_PGVECTOR_TABLE_NAME:rag_vectors}',
  'provider: ${KEYWORD_SEARCH_PROVIDER:local}',
  'k1: ${KEYWORD_SEARCH_BM25_K1:1.2}',
  'base-url: ${KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL:http://localhost:9200}',
  'provider: ${RERANK_PROVIDER:local-demo}'
]) {
  requireToken('backend/src/main/resources/application.yml', token)
}

for (const token of [
  'VECTOR_STORE_PROVIDER=memory',
  'VECTOR_STORE_PGVECTOR_TABLE_NAME=rag_vectors',
  'KEYWORD_SEARCH_PROVIDER=local',
  'KEYWORD_SEARCH_BM25_K1=1.2',
  'KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL=http://localhost:9200',
  'RERANK_PROVIDER=local-demo'
]) {
  requireToken('backend/.env.example', token)
}

for (const token of [
  'retrieval-adapter-contract.json',
  'RETRIEVAL_ADAPTER_SWITCH_GUIDE.md',
  'probe-retrieval-adapters.sh',
  'VectorStoreService',
  'VectorStoreProperties',
  'PgVectorStoreService',
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
  'Retrieval Adapter Switch Guide',
  './scripts/probe-retrieval-adapters.sh --dry-run',
  'memory -> pgvector',
  'local keyword -> bm25 -> elasticsearch',
  'local-demo rerank -> remote rerank',
  'PgVectorStoreService',
  'Bm25KeywordSearchProvider',
  'ElasticsearchKeywordSearchProvider',
  'RerankProviderClient'
]) {
  requireToken('project_document/RETRIEVAL_ADAPTER_SWITCH_GUIDE.md', token)
}

for (const token of [
  'probe-retrieval-adapters: ok',
  'VECTOR_STORE_PROVIDER=pgvector',
  'KEYWORD_SEARCH_PROVIDER=bm25',
  'KEYWORD_SEARCH_PROVIDER=elasticsearch',
  'RERANK_PROVIDER=remote',
  '--dry-run',
  '--contract-only'
]) {
  requireToken('scripts/probe-retrieval-adapters.sh', token)
}

for (const gate of [
  'scripts/check-retrieval-adapter-contract.js',
  'scripts/probe-retrieval-adapters.sh',
  'scripts/check-scaffold-alignment.js',
  'scripts/check-contracts.sh'
]) {
  requireArrayValue(contract.qualityGates, 'qualityGates', gate)
}

console.log('check-retrieval-adapter-contract: ok')
