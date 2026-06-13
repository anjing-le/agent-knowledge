#!/usr/bin/env node
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const apiPathsFile = 'frontend/src/api/paths.ts'
const boundaryFile = 'contracts/service-boundaries.json'

function fail(message) {
  console.error(`check-frontend-api-boundaries: ${message}`)
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
    fail(`invalid ${relativeFile}: ${error.message}`)
  }
}

function findBlock(source, marker) {
  const markerIndex = source.indexOf(marker)
  if (markerIndex < 0) {
    fail(`missing block marker: ${marker}`)
  }

  const start = source.indexOf('{', markerIndex)
  if (start < 0) {
    fail(`missing block opening brace: ${marker}`)
  }

  let depth = 0
  for (let index = start; index < source.length; index += 1) {
    const char = source[index]
    if (char === '{') {
      depth += 1
    } else if (char === '}') {
      depth -= 1
      if (depth === 0) {
        return source.slice(start + 1, index)
      }
    }
  }

  fail(`missing block closing brace: ${marker}`)
}

function buildFrontendRoutePaths(manifest) {
  const values = {}
  for (const boundary of manifest.boundaries || []) {
    if (!boundary.apiPathsKey) continue
    values[boundary.apiPathsKey] = values[boundary.apiPathsKey] || {}
    for (const route of boundary.routes || []) {
      if (route.frontendKey) {
        values[boundary.apiPathsKey][route.frontendKey] = route.path
      }
    }
  }
  return values
}

function extractTsModule(source, objectName, moduleName, routePaths) {
  const objectBlock = findBlock(source, `export const ${objectName}`)
  const moduleBlock = findBlock(objectBlock, `${moduleName}:`)
  const values = {}

  for (const match of moduleBlock.matchAll(/(\w+):\s*'([^']+)'/g)) {
    values[match[1]] = match[2]
  }

  for (const match of moduleBlock.matchAll(/(\w+):\s*SERVICE_BOUNDARY_ROUTE_PATHS\.(\w+)\.(\w+)/g)) {
    values[match[1]] = routePaths[match[2]]?.[match[3]]
  }

  for (const match of moduleBlock.matchAll(/(\w+):\s*\([^)]*\)\s*=>\s*`([^`]+)`/g)) {
    values[match[1]] = match[2].replace(/\$\{encodePathValue\((\w+)\)\}/g, '{$1}')
  }

  for (const match of moduleBlock.matchAll(/(\w+):\s*\([^)]*\)\s*=>\s*bindApiPathParams\(SERVICE_BOUNDARY_ROUTE_PATHS\.(\w+)\.(\w+),/g)) {
    values[match[1]] = routePaths[match[2]]?.[match[3]]
  }

  return values
}

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === 'dist') continue
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(fullPath, files)
    } else if (/\.(ts|vue)$/.test(entry.name)) {
      files.push(fullPath)
    }
  }
  return files
}

function isGeneratedOrRegistryFile(relativeFile) {
  return relativeFile.startsWith('frontend/src/contracts/') || relativeFile === apiPathsFile
}

const source = read(apiPathsFile)
const manifest = readJson(boundaryFile)
const routePaths = buildFrontendRoutePaths(manifest)

const declaredRoutes = new Map()
for (const boundary of manifest.boundaries || []) {
  if (!boundary.apiPathsKey) {
    continue
  }

  for (const route of boundary.routes || []) {
    if (route.frontendKey) {
      declaredRoutes.set(`${boundary.apiPathsKey}.${route.frontendKey}`, route.path)
    }
  }
}

if (!declaredRoutes.size) {
  fail('contracts/service-boundaries.json must define frontend route mappings')
}

const apiPathsBlock = findBlock(source, 'export const ApiPaths')
if (!apiPathsBlock.includes('SERVICE_BOUNDARY_ROUTE_PATHS')) {
  fail('ApiPaths must reference SERVICE_BOUNDARY_ROUTE_PATHS from frontend service-boundary contract')
}
if (/['"`]\/api\//.test(apiPathsBlock)) {
  fail('ApiPaths must use SERVICE_BOUNDARY_ROUTE_PATHS instead of direct /api/... literals')
}

for (const match of apiPathsBlock.matchAll(/([a-zA-Z]\w*):\s*{/g)) {
  const moduleName = match[1]
  const values = extractTsModule(source, 'ApiPaths', moduleName, routePaths)

  for (const [key, value] of Object.entries(values)) {
    const routeKey = `${moduleName}.${key}`
    const expected = declaredRoutes.get(routeKey)
    if (!expected) {
      fail(`ApiPaths.${routeKey} is not declared in contracts/service-boundaries.json`)
    }

    if (expected !== value) {
      fail(`ApiPaths.${routeKey} (${value}) does not match service-boundaries route ${expected}`)
    }
  }
}

for (const legacyToken of [
  'verify2FA',
  'sendOtp',
  'tenantMembers',
  'userInfo',
  'avatarUpload',
  'simpleMenus'
]) {
  if (apiPathsBlock.includes(legacyToken)) {
    fail(`ApiPaths must not contain legacy endpoint key: ${legacyToken}`)
  }
}

if (source.includes('export const ApiLegacyPaths')) {
  const legacyBlock = findBlock(source, 'export const ApiLegacyPaths')
  for (const legacyPath of [
    '/auth/login/verify-2fa',
    '/auth/otp/send',
    '/auth/tenant/account/list',
    '/auth/user/info',
    '/api/v3/system/menus/simple'
  ]) {
    if (!legacyBlock.includes(legacyPath)) {
      fail(`ApiLegacyPaths is missing legacy path: ${legacyPath}`)
    }
  }
}

const chatApiSource = read('frontend/src/api/chat.ts')
if (
  /from ['"]@\/utils\/http['"]/.test(chatApiSource) ||
  /from ['"]@\/api\/paths['"]/.test(chatApiSource)
) {
  fail('frontend/src/api/chat.ts must use openApiRequest and generated OpenAPI operation types')
}

for (const token of [
  "openApiRequest('listConversations'",
  "openApiRequest('getConversation'",
  "openApiRequest('createConversation'",
  "openApiRequest('deleteConversation'",
  "openApiRequest('updateConversationTitle'",
  "openApiRequest('getMessages'",
  "openApiRequest('sendMessage'"
]) {
  if (!chatApiSource.includes(token)) {
    fail(`frontend/src/api/chat.ts is missing OpenAPI operation binding: ${token}`)
  }
}

if (/enableRetrieval\?:|enableRetrieval:/.test(chatApiSource)) {
  fail('frontend/src/api/chat.ts must not define the legacy top-level sendMessage enableRetrieval field')
}

const knowledgeApiSource = read('frontend/src/api/knowledge.ts')
for (const token of [
  "openApiRequest('listKnowledgeBases'",
  "openApiRequest('listAllKnowledgeBases'",
  "openApiRequest('getKnowledgeBase'",
  "openApiRequest('createKnowledgeBase'",
  "openApiRequest('updateKnowledgeBase'",
  "openApiRequest('deleteKnowledgeBase'",
  "openApiRequest('listDocuments'",
  "openApiRequest('getDocument'",
  "openApiRequest('setDocumentEnabled'",
  "openApiRequest('deleteDocument'",
  "openApiRequest('batchDeleteDocuments'",
  "openApiRequest('reprocessDocument'",
  "openApiRequest('listDocumentTasks'",
  "openApiRequest('listChunks'",
  "openApiRequest('getChunk'",
  "openApiRequest('getChunkCount'",
  "openApiRequest('updateChunkStatus'"
]) {
  if (!knowledgeApiSource.includes(token)) {
    fail(`frontend/src/api/knowledge.ts is missing OpenAPI operation binding: ${token}`)
  }
}

if (/request\.(get|put|del)\b/.test(knowledgeApiSource)) {
  fail('frontend/src/api/knowledge.ts must use openApiRequest for read/update/delete operations')
}

const knowledgePostFallbacks = knowledgeApiSource.match(/request\.post\b/g) || []
if (
  knowledgePostFallbacks.length !== 1 ||
  !knowledgeApiSource.includes('ApiPaths.knowledge.baseDocuments(kbId)') ||
  !knowledgeApiSource.includes('FormData')
) {
  fail('frontend/src/api/knowledge.ts may only use request.post for the FormData document upload fallback')
}

const retrievalApiSource = read('frontend/src/api/retrieval.ts')
for (const token of [
  "openApiRequest('search'",
  "openApiRequest('simpleSearch'"
]) {
  if (!retrievalApiSource.includes(token)) {
    fail(`frontend/src/api/retrieval.ts is missing OpenAPI operation binding: ${token}`)
  }
}

if (
  /from ['"]@\/utils\/http['"]/.test(retrievalApiSource) ||
  /from ['"]@\/api\/paths['"]/.test(retrievalApiSource)
) {
  fail('frontend/src/api/retrieval.ts must use openApiRequest and generated OpenAPI operation types')
}

const knowledgeRouteSource = read('frontend/src/router/modules/knowledge.ts')
for (const token of [
  "name: 'RagPipeline'",
  "component: '/pipeline/index'",
  "title: 'menus.kb.pipeline'",
  "name: 'RetrievalDebug'",
  "component: '/retrieval/index'",
  "title: 'menus.kb.retrieval'"
]) {
  if (!knowledgeRouteSource.includes(token)) {
    fail(`frontend RAG workspace route is missing retrieval debug token: ${token}`)
  }
}

const componentLoaderSource = read('frontend/src/router/core/ComponentLoader.ts')
if (!componentLoaderSource.includes("'../../views/pipeline/**/*.vue'")) {
  fail('ComponentLoader must include RAG Pipeline teaching views')
}
if (!componentLoaderSource.includes("'../../views/retrieval/**/*.vue'")) {
  fail('ComponentLoader must include retrieval workspace views')
}

const pipelineViewSource = read('frontend/src/views/pipeline/index.vue')
for (const token of [
  'RAG Pipeline 教学视图',
  'infra-dev-scaffolding',
  'APIResponse / PageResult',
  'RemoteHttpClient',
  'Python FastAPI doc-parser',
  './scripts/seed-rag-demo.sh',
  './scripts/smoke-rag-demo.sh'
]) {
  if (!pipelineViewSource.includes(token)) {
    fail(`frontend RAG Pipeline view is missing teaching token: ${token}`)
  }
}

const retrievalViewSource = read('frontend/src/views/retrieval/index.vue')
for (const token of [
  '带入问答',
  "source: 'retrieval'",
  'kbIds: selectedKbIds.value'
]) {
  if (!retrievalViewSource.includes(token)) {
    fail(`frontend retrieval debug view is missing chat handoff token: ${token}`)
  }
}

const chatViewSource = read('frontend/src/views/chat/index.vue')
for (const token of [
  'applyRetrievalHandoff',
  'route.query.q',
  'route.query.kbIds',
  '已带入检索调试参数'
]) {
  if (!chatViewSource.includes(token)) {
    fail(`frontend chat view is missing retrieval handoff token: ${token}`)
  }
}

for (const file of walk(path.join(root, 'frontend/src'))) {
  const relativeFile = path.relative(root, file)
  if (isGeneratedOrRegistryFile(relativeFile)) {
    continue
  }

  const runtimeSource = fs.readFileSync(file, 'utf8')
  if (/['"`]\/api\//.test(runtimeSource) || /`\$\{[^}]+}\/api\//.test(runtimeSource)) {
    fail(`runtime frontend code must use ApiPaths or openApiRequest instead of hardcoded /api path: ${relativeFile}`)
  }
}

console.log('check-frontend-api-boundaries: ok')
