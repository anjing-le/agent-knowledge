#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MODE="dry-run"

VECTOR_STORE_PROVIDER="${VECTOR_STORE_PROVIDER:-memory}"
VECTOR_STORE_PGVECTOR_TABLE_NAME="${VECTOR_STORE_PGVECTOR_TABLE_NAME:-rag_vectors}"
VECTOR_STORE_PGVECTOR_SCHEMA_INITIALIZATION_ENABLED="${VECTOR_STORE_PGVECTOR_SCHEMA_INITIALIZATION_ENABLED:-false}"
KEYWORD_SEARCH_PROVIDER="${KEYWORD_SEARCH_PROVIDER:-local}"
KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL="${KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL:-http://localhost:9200}"
KEYWORD_SEARCH_ELASTICSEARCH_INDEX_PREFIX="${KEYWORD_SEARCH_ELASTICSEARCH_INDEX_PREFIX:-kb_}"
RERANK_PROVIDER="${RERANK_PROVIDER:-local-demo}"
RERANK_API_URL="${RERANK_API_URL:-https://api.cohere.com/v2/rerank}"
RERANK_MODEL="${RERANK_MODEL:-rerank-v3.5}"

fail() {
  echo "probe-retrieval-adapters: $*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/probe-retrieval-adapters.sh [--dry-run|--contract-only]

Checks the retrieval adapter switch path without requiring PostgreSQL, Elasticsearch, or a remote rerank provider.

Modes:
  --dry-run        Validate contracts and print the production adapter env/commands. Default.
  --contract-only  Validate contracts and source boundaries only.

Environment:
  VECTOR_STORE_PROVIDER                                  Defaults to memory
  VECTOR_STORE_PGVECTOR_TABLE_NAME                       Defaults to rag_vectors
  VECTOR_STORE_PGVECTOR_SCHEMA_INITIALIZATION_ENABLED    Defaults to false
  KEYWORD_SEARCH_PROVIDER                                Defaults to local
  KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL                  Defaults to http://localhost:9200
  KEYWORD_SEARCH_ELASTICSEARCH_INDEX_PREFIX              Defaults to kb_
  RERANK_PROVIDER                                        Defaults to local-demo
  RERANK_API_URL                                         Defaults to https://api.cohere.com/v2/rerank
  RERANK_MODEL                                           Defaults to rerank-v3.5
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      MODE="dry-run"
      shift
      ;;
    --contract-only)
      MODE="contract-only"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "unexpected argument: $1"
      ;;
  esac
done

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
  contracts/retrieval-adapter-contract.json \
  backend/.env.example \
  backend/src/main/resources/application.yml \
  backend/src/main/java/com/anjing/config/properties/VectorStoreProperties.java \
  backend/src/main/java/com/anjing/config/properties/KeywordSearchProperties.java \
  backend/src/main/java/com/anjing/config/properties/RerankProperties.java \
  backend/src/main/java/com/anjing/knowledge/service/VectorStoreService.java \
  backend/src/main/java/com/anjing/knowledge/service/MemoryVectorStoreService.java \
  backend/src/main/java/com/anjing/knowledge/service/PgVectorStoreService.java \
  backend/src/main/java/com/anjing/knowledge/service/KeywordSearchProvider.java \
  backend/src/main/java/com/anjing/knowledge/service/LocalKeywordSearchProvider.java \
  backend/src/main/java/com/anjing/knowledge/service/ElasticsearchKeywordSearchProvider.java \
  backend/src/main/java/com/anjing/knowledge/service/RetrievalHybridSearchService.java \
  backend/src/main/java/com/anjing/knowledge/service/RetrievalRerankService.java \
  backend/src/main/java/com/anjing/knowledge/service/RerankProviderClient.java \
  project_document/RETRIEVAL_ADAPTER_GUIDE.md \
  project_document/RETRIEVAL_ADAPTER_SWITCH_GUIDE.md \
  project_document/VECTOR_STORE_ADAPTER_GUIDE.md
do
  require_file "$file"
done

for token in \
  'PGVECTOR_PROVIDER' \
  'private Pgvector pgvector = new Pgvector()' \
  '@ConditionalOnProperty(prefix = "app.vector-store", name = "provider", havingValue = "pgvector")' \
  'JdbcTemplate' \
  'embedding <=> ?::vector'
do
  rg -q --fixed-strings -- "$token" \
    backend/src/main/java/com/anjing/config/properties/VectorStoreProperties.java \
    backend/src/main/java/com/anjing/knowledge/service/PgVectorStoreService.java \
    || fail "pgvector adapter boundary is missing token: $token"
done

for token in \
  'ELASTICSEARCH_PROVIDER' \
  '@ConditionalOnProperty(prefix = "app.keyword-search", name = "provider", havingValue = "elasticsearch")' \
  'RemoteHttpClient' \
  'TARGET_SERVICE = "keyword-search-provider"' \
  'targetService(TARGET_SERVICE)'
do
  rg -q --fixed-strings -- "$token" \
    backend/src/main/java/com/anjing/config/properties/KeywordSearchProperties.java \
    backend/src/main/java/com/anjing/knowledge/service/ElasticsearchKeywordSearchProvider.java \
    || fail "elasticsearch adapter boundary is missing token: $token"
done

for token in \
  '@ConfigurationProperties(prefix = "app.rerank")' \
  'targetService("rerank-provider")' \
  'RemoteHttpClient' \
  'isRemoteProvider'
do
  rg -q --fixed-strings -- "$token" \
    backend/src/main/java/com/anjing/config/properties/RerankProperties.java \
    backend/src/main/java/com/anjing/knowledge/service/RerankProviderClient.java \
    || fail "rerank adapter boundary is missing token: $token"
done

for token in \
  'VECTOR_STORE_PROVIDER=memory' \
  'VECTOR_STORE_PGVECTOR_TABLE_NAME=rag_vectors' \
  'KEYWORD_SEARCH_PROVIDER=local' \
  'KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL=http://localhost:9200' \
  'RERANK_PROVIDER=local-demo'
do
  require_token backend/.env.example "$token"
done

for token in \
  'provider: ${VECTOR_STORE_PROVIDER:memory}' \
  'table-name: ${VECTOR_STORE_PGVECTOR_TABLE_NAME:rag_vectors}' \
  'provider: ${KEYWORD_SEARCH_PROVIDER:local}' \
  'base-url: ${KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL:http://localhost:9200}' \
  'provider: ${RERANK_PROVIDER:local-demo}'
do
  require_token backend/src/main/resources/application.yml "$token"
done

node <<'NODE'
const fs = require('fs')

function fail(message) {
  console.error(`probe-retrieval-adapters: ${message}`)
  process.exit(1)
}

const contract = JSON.parse(fs.readFileSync('contracts/retrieval-adapter-contract.json', 'utf8'))

if (contract.serviceId !== 'retrieval-adapter') fail('serviceId must be retrieval-adapter')
if (contract.vectorStore?.sqlImplementation !== 'PgVectorStoreService') fail('vectorStore.sqlImplementation must stay PgVectorStoreService')
if (!contract.vectorStore?.productionProviderSkeletons?.includes('pgvector')) fail('vectorStore must include pgvector production skeleton')
if (contract.keywordSearch?.remoteImplementation !== 'ElasticsearchKeywordSearchProvider') fail('keywordSearch.remoteImplementation must stay ElasticsearchKeywordSearchProvider')
if (contract.keywordSearch?.targetService !== 'keyword-search-provider') fail('keywordSearch.targetService must stay keyword-search-provider')
if (contract.rerank?.targetService !== 'rerank-provider') fail('rerank.targetService must stay rerank-provider')

for (const key of [
  'VECTOR_STORE_PROVIDER',
  'VECTOR_STORE_PGVECTOR_TABLE_NAME',
  'KEYWORD_SEARCH_PROVIDER',
  'KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL',
  'RERANK_PROVIDER',
  'RERANK_API_URL',
  'RERANK_MODEL'
]) {
  const mergedKeys = [
    ...(contract.vectorStore?.configKeys || []),
    ...(contract.keywordSearch?.configKeys || []),
    ...(contract.rerank?.configKeys || [])
  ]
  if (!mergedKeys.includes(key)) fail(`retrieval adapter contract configKeys must include ${key}`)
}

console.log('probe-retrieval-adapters: contract serviceId=retrieval-adapter')
console.log('probe-retrieval-adapters: vectorStore=memory -> pgvector')
console.log('probe-retrieval-adapters: keywordSearch=local -> elasticsearch')
console.log('probe-retrieval-adapters: rerank=local-demo -> remote')
NODE

if [[ "$MODE" == "dry-run" ]]; then
  cat <<EOF
probe-retrieval-adapters: dry-run production env
  VECTOR_STORE_PROVIDER=pgvector
  VECTOR_STORE_PGVECTOR_TABLE_NAME=${VECTOR_STORE_PGVECTOR_TABLE_NAME}
  VECTOR_STORE_PGVECTOR_SCHEMA_INITIALIZATION_ENABLED=${VECTOR_STORE_PGVECTOR_SCHEMA_INITIALIZATION_ENABLED}
  KEYWORD_SEARCH_PROVIDER=elasticsearch
  KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL=${KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL}
  KEYWORD_SEARCH_ELASTICSEARCH_INDEX_PREFIX=${KEYWORD_SEARCH_ELASTICSEARCH_INDEX_PREFIX}
  RERANK_PROVIDER=remote
  RERANK_API_URL=${RERANK_API_URL}
  RERANK_MODEL=${RERANK_MODEL}

probe-retrieval-adapters: dry-run verification commands
  node scripts/check-retrieval-adapter-contract.js
  mvn -q -Dtest=PgVectorStoreServiceTest,ElasticsearchKeywordSearchProviderTest,RerankProviderClientTest test
  ./scripts/check-contracts.sh
EOF
else
  echo "probe-retrieval-adapters: dry-run=skipped"
fi

echo "probe-retrieval-adapters: ok"
