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
  project_document/README.md \
  project_document/ROADMAP.md \
  project_document/STATUS.md \
  project_document/PROJECT_CONSTRAINTS.md \
  project_document/SERVICE_BOUNDARY_GUIDE.md \
  project_document/DOC_PARSER_SERVICE_GUIDE.md \
  project_document/API_CONTRACT_GUIDE.md \
  project_document/LOCAL_STARTUP_GUIDE.md \
  project_document/REMOTE_CALL_GUIDE.md \
  project_document/DEMO_EVIDENCE.md \
  docs/evidence/README.md \
  docs/evidence/TEMPLATE.md \
  scripts/create-demo-evidence.sh \
  scripts/seed-rag-demo.sh \
  scripts/smoke-rag-demo.sh \
  backend/.env.example \
  backend/pom.xml \
  backend/src/main/resources/application.yml \
  backend/src/main/resources/application-dev.yml \
  backend/src/main/resources/application-test.yml \
  backend/src/main/resources/application-prod.yml \
  backend/src/main/java/com/anjing/model/response/APIResponse.java \
  backend/src/main/java/com/anjing/model/response/PageResult.java \
  backend/src/main/java/com/anjing/model/constants/ApiConstants.java \
  backend/src/main/java/com/anjing/model/constants/ServiceBoundaryConstants.java \
  backend/src/main/java/com/anjing/demo/service/RagDemoSeedService.java \
  backend/src/main/java/com/anjing/knowledge/client/DocParserClient.java \
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
  'Java must call doc-parser over HTTP'
do
  rg -q --fixed-strings "$token" contracts/doc-parser-contract.json \
    || fail "doc-parser contract is missing token: $token"
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
  'RAG Pipeline 教学视图' \
  'infra-dev-scaffolding' \
  'APIResponse / PageResult' \
  'RemoteHttpClient' \
  'Python FastAPI doc-parser' \
  'RagDemoService.seedRagDemo' \
  'Seed -> Retrieval -> Chat -> Evidence' \
  './scripts/create-demo-evidence.sh --dry-run' \
  'Demo 数据已生成' \
  './scripts/seed-rag-demo.sh' \
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
  './scripts/create-demo-evidence.sh --dry-run'
do
  rg -q --fixed-strings "$token" backend/src/main/java/com/anjing/demo/service/RagDemoSeedService.java \
    || fail "RAG demo seed service is missing token: $token"
done

for token in \
  'docs/evidence/YYYY-MM-DD/' \
  'Seed -> Retrieval -> Chat -> Evidence' \
  './scripts/create-demo-evidence.sh --dry-run' \
  'screenshots/chat-with-citations.png'
do
  rg -q --fixed-strings "$token" project_document/DEMO_EVIDENCE.md docs/evidence scripts/create-demo-evidence.sh \
    || fail "demo evidence template is missing token: $token"
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
