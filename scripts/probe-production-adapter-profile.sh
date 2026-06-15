#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MODE="dry-run"

fail() {
  echo "probe-production-adapter-profile: $*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/probe-production-adapter-profile.sh [--dry-run|--contract-only]

Checks the prod-adapters profile and prints the production RAG adapter startup
shape without connecting to PostgreSQL, Elasticsearch or remote rerank services.

Modes:
  --dry-run        Validate files/contracts and print profile/env commands. Default.
  --contract-only  Validate files/contracts only.
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
  backend/pom.xml \
  backend/.env.prod-adapters.example \
  backend/src/main/resources/application-prod-adapters.yml \
  contracts/retrieval-adapter-contract.json \
  project_document/ENVIRONMENT_PROFILE_GUIDE.md \
  project_document/RETRIEVAL_ADAPTER_SWITCH_GUIDE.md \
  scripts/probe-retrieval-adapters.sh
do
  require_file "$file"
done

for token in \
  '<artifactId>postgresql</artifactId>' \
  'org.postgresql'
do
  require_token backend/pom.xml "$token"
done

for token in \
  'SPRING_PROFILES_ACTIVE=prod,prod-adapters' \
  'DB_URL=jdbc:postgresql://localhost:5432/agent_knowledge' \
  'DB_DRIVER=org.postgresql.Driver' \
  'DOC_PARSER_MODE=async' \
  'DOC_PARSER_ASYNC_RECOVERY_ENABLED=true' \
  'VECTOR_STORE_PROVIDER=pgvector' \
  'KEYWORD_SEARCH_PROVIDER=bm25' \
  'RERANK_PROVIDER=remote'
do
  require_token backend/.env.prod-adapters.example "$token"
done

for token in \
  'provider: ${VECTOR_STORE_PROVIDER:pgvector}' \
  'provider: ${KEYWORD_SEARCH_PROVIDER:bm25}' \
  'provider: ${RERANK_PROVIDER:remote}' \
  'mode: ${DOC_PARSER_MODE:async}' \
  'recovery-enabled: ${DOC_PARSER_ASYNC_RECOVERY_ENABLED:true}' \
  'org.postgresql.Driver' \
  'org.hibernate.dialect.PostgreSQLDialect'
do
  require_token backend/src/main/resources/application-prod-adapters.yml "$token"
done

node <<'NODE'
const fs = require('fs')
const contract = JSON.parse(fs.readFileSync('contracts/retrieval-adapter-contract.json', 'utf8'))

function fail(message) {
  console.error(`probe-production-adapter-profile: ${message}`)
  process.exit(1)
}

const profile = contract.productionProfile || {}
if (profile.springProfile !== 'prod-adapters') fail('productionProfile.springProfile must be prod-adapters')
if (profile.activation !== 'SPRING_PROFILES_ACTIVE=prod,prod-adapters') fail('productionProfile.activation must match env example')
if (profile.profileFile !== 'backend/src/main/resources/application-prod-adapters.yml') fail('productionProfile.profileFile mismatch')
if (profile.envExample !== 'backend/.env.prod-adapters.example') fail('productionProfile.envExample mismatch')
if (profile.probeScript !== 'scripts/probe-production-adapter-profile.sh') fail('productionProfile.probeScript mismatch')

const providers = profile.defaultProviders || {}
if (providers.vectorStore !== 'pgvector') fail('productionProfile vectorStore must be pgvector')
if (providers.keywordSearch !== 'bm25') fail('productionProfile keywordSearch must be bm25')
if (providers.rerank !== 'remote') fail('productionProfile rerank must be remote')
if (providers.docParser !== 'async-recovery') fail('productionProfile docParser must be async-recovery')

console.log('probe-production-adapter-profile: contract profile=prod-adapters')
console.log('probe-production-adapter-profile: adapters=pgvector,bm25,remote,async-recovery')
NODE

if [[ "$MODE" == "dry-run" ]]; then
  cat <<'EOF'
probe-production-adapter-profile: dry-run env file
  backend/.env.prod-adapters.example

probe-production-adapter-profile: dry-run startup shape
  SPRING_PROFILES_ACTIVE=prod,prod-adapters
  DB_DRIVER=org.postgresql.Driver
  VECTOR_STORE_PROVIDER=pgvector
  KEYWORD_SEARCH_PROVIDER=bm25
  RERANK_PROVIDER=remote
  DOC_PARSER_MODE=async
  DOC_PARSER_ASYNC_RECOVERY_ENABLED=true

probe-production-adapter-profile: optional escalation
  KEYWORD_SEARCH_PROVIDER=elasticsearch

probe-production-adapter-profile: verification commands
  ./scripts/probe-retrieval-adapters.sh --dry-run
  curl -fsS http://localhost:10001/api/retrieval/adapters/status
EOF
else
  echo "probe-production-adapter-profile: dry-run=skipped"
fi

echo "probe-production-adapter-profile: ok"
