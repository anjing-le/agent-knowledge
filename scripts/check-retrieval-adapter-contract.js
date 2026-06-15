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
if (contract.vectorStore?.defaultProvider !== 'memory') {
  fail('vectorStore.defaultProvider must be memory')
}
for (const provider of ['milvus', 'pgvector', 'hosted-vector-db']) {
  requireArrayValue(contract.vectorStore?.futureProviders, 'vectorStore.futureProviders', provider)
}
for (const key of [
  'app.vector-store.provider',
  'VECTOR_STORE_PROVIDER',
  'VECTOR_STORE_COLLECTION_PREFIX'
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
requireArrayValue(contract.keywordSearch?.productionProviderSkeletons, 'keywordSearch.productionProviderSkeletons', 'elasticsearch')
if (contract.keywordSearch?.remoteImplementation !== 'ElasticsearchKeywordSearchProvider') {
  fail('keywordSearch.remoteImplementation must be ElasticsearchKeywordSearchProvider')
}
if (contract.keywordSearch?.targetService !== 'keyword-search-provider') {
  fail('keywordSearch.targetService must be keyword-search-provider')
}
for (const provider of ['bm25']) {
  requireArrayValue(contract.keywordSearch?.futureProviders, 'keywordSearch.futureProviders', provider)
}
for (const key of [
  'KEYWORD_SEARCH_PROVIDER',
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

for (const file of [
  'backend/src/main/java/com/anjing/knowledge/service/VectorStoreService.java',
  'backend/src/main/java/com/anjing/knowledge/service/MemoryVectorStoreService.java',
  'backend/src/main/java/com/anjing/knowledge/service/KeywordSearchProvider.java',
  'backend/src/main/java/com/anjing/knowledge/service/LocalKeywordSearchProvider.java',
  'backend/src/main/java/com/anjing/knowledge/service/ElasticsearchKeywordSearchProvider.java',
  'backend/src/main/java/com/anjing/config/properties/KeywordSearchProperties.java',
  'backend/src/main/java/com/anjing/knowledge/service/RetrievalHybridSearchService.java',
  'backend/src/main/java/com/anjing/knowledge/service/RetrievalRerankService.java',
  'backend/src/main/java/com/anjing/knowledge/service/RerankProviderClient.java',
  'backend/src/main/java/com/anjing/config/properties/RerankProperties.java',
  'backend/src/main/resources/application.yml',
  'backend/.env.example',
  'project_document/RETRIEVAL_ADAPTER_GUIDE.md'
]) {
  read(file)
}

requireToken(
  'backend/src/main/java/com/anjing/knowledge/service/MemoryVectorStoreService.java',
  '@ConditionalOnProperty(prefix = "app.vector-store"'
)
requireToken(
  'backend/src/main/java/com/anjing/knowledge/service/LocalKeywordSearchProvider.java',
  '@ConditionalOnProperty(prefix = "app.keyword-search"'
)
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
  'provider: ${VECTOR_STORE_PROVIDER:memory}',
  'provider: ${KEYWORD_SEARCH_PROVIDER:local}',
  'base-url: ${KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL:http://localhost:9200}',
  'provider: ${RERANK_PROVIDER:local-demo}'
]) {
  requireToken('backend/src/main/resources/application.yml', token)
}

for (const token of [
  'VECTOR_STORE_PROVIDER=memory',
  'KEYWORD_SEARCH_PROVIDER=local',
  'KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL=http://localhost:9200',
  'RERANK_PROVIDER=local-demo'
]) {
  requireToken('backend/.env.example', token)
}

for (const token of [
  'retrieval-adapter-contract.json',
  'VectorStoreService',
  'KeywordSearchProvider',
  'KeywordSearchProperties',
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

for (const gate of [
  'scripts/check-retrieval-adapter-contract.js',
  'scripts/check-scaffold-alignment.js',
  'scripts/check-contracts.sh'
]) {
  requireArrayValue(contract.qualityGates, 'qualityGates', gate)
}

console.log('check-retrieval-adapter-contract: ok')
