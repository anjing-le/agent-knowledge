#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() {
  echo "check-contracts: $*" >&2
  exit 1
}

require_file() {
  local file="$1"
  [[ -f "$file" ]] || fail "missing required file: $file"
}

require_token() {
  local file="$1"
  local token="$2"
  rg -q --fixed-strings "$token" "$file" \
    || fail "$file is missing required token: $token"
}

require_absent() {
  local pattern="$1"
  shift
  if rg -n "$pattern" "$@"; then
    fail "contract violation found for pattern: $pattern"
  fi
}

for file in \
  contracts/platform-contract.json \
  contracts/scaffold-stack-contract.json \
  contracts/service-boundaries.json \
  contracts/doc-parser-contract.json \
  backend/src/main/java/com/anjing/model/constants/ApiConstants.java \
  backend/src/main/java/com/anjing/model/constants/PlatformContractConstants.java \
  backend/src/main/java/com/anjing/model/constants/ServiceBoundaryConstants.java \
  backend/src/main/java/com/anjing/model/response/APIResponse.java \
  backend/src/main/java/com/anjing/model/response/PageResult.java \
  backend/src/main/java/com/anjing/config/http/RequestContextFilter.java \
  backend/src/main/java/com/anjing/knowledge/client/DocParserClient.java \
  frontend/src/api/paths.ts \
  frontend/src/api/knowledge.ts \
  frontend/src/api/retrieval.ts \
  frontend/src/api/chat.ts \
  frontend/src/api/auth.ts \
  frontend/src/contracts/platform-contract.ts \
  frontend/src/contracts/service-boundaries.ts \
  frontend/src/utils/http/context.ts \
  frontend/src/utils/http/response.ts \
  frontend/src/utils/time/index.ts \
  frontend/src/utils/locale/index.ts \
  project_document/API_CONTRACT_GUIDE.md \
  project_document/SERVICE_BOUNDARY_GUIDE.md \
  project_document/DOC_PARSER_SERVICE_GUIDE.md \
  project_document/REMOTE_CALL_GUIDE.md
do
  require_file "$file"
done

# URL contract: RAG runtime APIs use generated paths and backend constants.
require_absent '@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\(\s*"/api' \
  backend/src/main/java/com/anjing/knowledge \
  backend/src/main/java/com/anjing/chat \
  --glob '*Controller.java'

require_absent 'url:\s*['"'"'"]/api' \
  frontend/src/api/knowledge.ts \
  frontend/src/api/chat.ts \
  frontend/src/api/auth.ts

require_token backend/src/main/java/com/anjing/knowledge/controller/KnowledgeBaseController.java 'ApiConstants.Knowledge.BASE'
require_token backend/src/main/java/com/anjing/knowledge/controller/DocumentController.java 'ApiConstants.Knowledge.BASE'
require_token backend/src/main/java/com/anjing/knowledge/controller/ChunkController.java 'ApiConstants.Knowledge.BASE'
require_token backend/src/main/java/com/anjing/knowledge/controller/RetrievalController.java 'ApiConstants.Retrieval.BASE'
require_token backend/src/main/java/com/anjing/chat/controller/ChatController.java 'ApiConstants.Chat.BASE'
require_token frontend/src/api/knowledge.ts 'ApiPaths.knowledge'
require_token frontend/src/api/knowledge.ts "openApiRequest('listKnowledgeBases'"
require_token frontend/src/api/knowledge.ts "openApiRequest('listDocuments'"
require_token frontend/src/api/knowledge.ts "openApiRequest('listChunks'"
require_token frontend/src/api/retrieval.ts "openApiRequest('search'"
require_token frontend/src/api/retrieval.ts "openApiRequest('simpleSearch'"
require_token frontend/src/api/chat.ts "openApiRequest('listConversations'"
require_token frontend/src/api/chat.ts "openApiRequest('sendMessage'"
require_token frontend/src/api/auth.ts "openApiRequest('login'"
require_token frontend/src/api/auth.ts "openApiRequest('getCurrentUser'"

# Response and pagination contract.
require_token backend/src/main/java/com/anjing/model/response/APIResponse.java 'PlatformContractConstants.Response.SUCCESS_CODE'
require_token backend/src/main/java/com/anjing/model/response/APIResponse.java 'private String message'
require_token backend/src/main/java/com/anjing/model/response/APIResponse.java 'private String requestId'
require_token backend/src/main/java/com/anjing/model/response/PageResult.java 'private List<T> records'
require_token backend/src/main/java/com/anjing/model/response/PageResult.java 'current'
require_token backend/src/main/java/com/anjing/model/response/PageResult.java 'size'
require_token backend/src/main/java/com/anjing/model/response/PageResult.java 'total'
require_token frontend/src/types/common/response.ts 'PaginatedResponse'
require_token frontend/src/utils/http/response.ts 'extractResponseMessage'

# Context contract.
require_token backend/src/main/java/com/anjing/config/http/RequestContextFilter.java 'GlobalRequestContextHolder.set(context)'
require_token backend/src/main/java/com/anjing/config/http/RequestContextFilter.java 'response.setHeader(RequestHeaderConstants.REQUEST_ID'
require_token frontend/src/utils/http/context.ts 'buildRequestContextHeaders'
require_token frontend/src/utils/http/index.ts 'applyRequestContextHeaders'

# doc-parser boundary.
require_token contracts/doc-parser-contract.json 'python-fastapi'
require_token contracts/doc-parser-contract.json 'syncParseFile'
require_token contracts/doc-parser-contract.json 'asyncDeepParse'
require_token backend/src/main/java/com/anjing/knowledge/client/DocParserClient.java 'baseUrl + "/parse"'
require_token backend/src/main/java/com/anjing/knowledge/client/DocParserClient.java 'baseUrl + "/parse_url"'
require_token project_document/DOC_PARSER_SERVICE_GUIDE.md '不应该被粗暴塞进 Spring Boot'

node scripts/generate-platform-contract-backend.js --check
node scripts/generate-platform-contract-frontend.js --check
node scripts/generate-service-boundaries-backend.js --check
node scripts/generate-service-boundaries-frontend.js --check
node scripts/check-platform-contract.js
node scripts/check-service-boundaries.js
node scripts/check-scaffold-alignment.js
node scripts/check-frontend-api-boundaries.js
node scripts/check-backend-controller-contracts.js
node scripts/check-backend-time-contract.js
node scripts/check-remote-http-contract.js
./scripts/probe-doc-parser-boundary.sh --contract-only
./scripts/check-doc-parser-lifecycle.sh

echo "check-contracts: ok"
