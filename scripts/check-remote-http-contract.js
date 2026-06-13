#!/usr/bin/env node
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const serviceBoundariesPath = path.join(root, 'contracts/service-boundaries.json')
const platformContractPath = path.join(root, 'contracts/platform-contract.json')

const files = {
  platform: 'backend/src/main/java/com/anjing/model/constants/PlatformContractConstants.java',
  properties: 'backend/src/main/java/com/anjing/config/properties/RemoteHttpClientProperties.java',
  request: 'backend/src/main/java/com/anjing/client/RemoteHttpRequest.java',
  client: 'backend/src/main/java/com/anjing/client/RemoteHttpClient.java',
  callerResolver: 'backend/src/main/java/com/anjing/client/RemoteCallerResolver.java',
  defaultCallerResolver: 'backend/src/main/java/com/anjing/client/DefaultRemoteCallerResolver.java',
  endpointResolver: 'backend/src/main/java/com/anjing/client/ServiceEndpointResolver.java',
  serviceEndpoint: 'backend/src/main/java/com/anjing/client/ServiceEndpoint.java',
  endpointRegistry: 'backend/src/main/java/com/anjing/client/ServiceEndpointRegistry.java',
  configuredEndpointRegistry: 'backend/src/main/java/com/anjing/client/ConfiguredServiceEndpointRegistry.java',
  configuredEndpointResolver: 'backend/src/main/java/com/anjing/client/ConfiguredServiceEndpointResolver.java',
  callPolicy: 'backend/src/main/java/com/anjing/client/RemoteCallPolicy.java',
  callPolicyContext: 'backend/src/main/java/com/anjing/client/RemoteCallPolicyContext.java',
  configuredCallPolicy: 'backend/src/main/java/com/anjing/client/ConfiguredRemoteCallPolicy.java',
  noopCallPolicy: 'backend/src/main/java/com/anjing/client/NoopRemoteCallPolicy.java',
  callObserver: 'backend/src/main/java/com/anjing/client/RemoteCallObserver.java',
  callObservation: 'backend/src/main/java/com/anjing/client/RemoteCallObservation.java',
  noopCallObserver: 'backend/src/main/java/com/anjing/client/NoopRemoteCallObserver.java',
  httpClientConfig: 'backend/src/main/java/com/anjing/config/http/RemoteHttpClientConfig.java',
  embeddingService: 'backend/src/main/java/com/anjing/knowledge/service/EmbeddingService.java',
  llmService: 'backend/src/main/java/com/anjing/knowledge/service/LLMService.java',
  remoteWrapper: 'backend/src/main/java/com/anjing/util/RemoteCallWrapper.java',
  application: 'backend/src/main/resources/application.yml',
  guide: 'project_document/REMOTE_CALL_GUIDE.md'
}

function fail(message) {
  console.error(`check-remote-http-contract: ${message}`)
  process.exit(1)
}

function read(relativeFile) {
  const file = path.join(root, relativeFile)
  if (!fs.existsSync(file)) {
    fail(`missing required file: ${relativeFile}`)
  }
  return fs.readFileSync(file, 'utf8')
}

function requireToken(relativeFile, token) {
  const source = read(relativeFile)
  if (!source.includes(token)) {
    fail(`${relativeFile} is missing token: ${token}`)
  }
}

function requireAbsent(relativeFile, token) {
  const source = read(relativeFile)
  if (source.includes(token)) {
    fail(`${relativeFile} must not contain token: ${token}`)
  }
}

let serviceBoundaries
try {
  serviceBoundaries = JSON.parse(fs.readFileSync(serviceBoundariesPath, 'utf8'))
} catch (error) {
  fail(`invalid contracts/service-boundaries.json: ${error.message}`)
}

let platformContract
try {
  platformContract = JSON.parse(fs.readFileSync(platformContractPath, 'utf8'))
} catch (error) {
  fail(`invalid contracts/platform-contract.json: ${error.message}`)
}

const applicationId = serviceBoundaries.applicationId
if (!applicationId) {
  fail('contracts/service-boundaries.json must define applicationId')
}

for (const [relativeFile, tokens] of Object.entries({
  [files.properties]: [
    'private Map<String, String> serviceBaseUrls',
    'ServiceBoundaryConstants.APPLICATION_ID',
    'private Policy policy',
    'private boolean enabled',
    'private List<String> blockedServiceIds',
    'private List<String> allowedCallerIds',
    'private Map<String, List<String>> allowedCallerIdsByService'
  ],
  [files.request]: ['private String serviceId', 'private String path'],
  [files.client]: [
    'getFromService',
    'postToService',
    'ParameterizedTypeReference',
    'exchange(RemoteHttpRequest request, ParameterizedTypeReference<R> responseType)',
    'responseSpec.body(responseType)',
    'resolveUrl',
    'RemoteCallerResolver',
    'remoteCallerResolver.resolveCallerId',
    'ServiceEndpointResolver',
    'serviceEndpointResolver.resolveUrl',
    'RemoteCallPolicy',
    'remoteCallPolicy.beforeCall',
    'remoteCallPolicy.afterSuccess',
    'remoteCallPolicy.afterFailure',
    'RemoteCallObserver',
    'remoteCallObserver.onComplete',
    'RemoteCallObservation',
    'durationMs'
  ],
  [files.callerResolver]: ['interface RemoteCallerResolver', 'resolveCallerId(RemoteHttpRequest request)'],
  [files.defaultCallerResolver]: [
    'implements RemoteCallerResolver',
    'request.getCallerId()',
    'properties.getDefaultCallerId()',
    'ServiceBoundaryConstants.APPLICATION_ID'
  ],
  [files.endpointResolver]: ['interface ServiceEndpointResolver', 'resolveUrl(String serviceId, String path)'],
  [files.serviceEndpoint]: ['record ServiceEndpoint', 'String baseUrl', 'String source'],
  [files.endpointRegistry]: ['interface ServiceEndpointRegistry', 'Optional<ServiceEndpoint> findEndpoint(String serviceId)'],
  [files.configuredEndpointRegistry]: [
    'implements ServiceEndpointRegistry',
    'properties.getServiceBaseUrls()',
    'new ServiceEndpoint(serviceId, baseUrl, SOURCE)'
  ],
  [files.configuredEndpointResolver]: [
    'implements ServiceEndpointResolver',
    'ServiceEndpointRegistry',
    'serviceEndpointRegistry.findEndpoint(serviceId)',
    'joinUrl'
  ],
  [files.callPolicy]: [
    'interface RemoteCallPolicy',
    'beforeCall(RemoteCallPolicyContext context)',
    'afterSuccess(RemoteCallPolicyContext context)',
    'afterFailure(RemoteCallPolicyContext context, RuntimeException exception)'
  ],
  [files.callPolicyContext]: ['record RemoteCallPolicyContext'],
  [files.configuredCallPolicy]: [
    'implements RemoteCallPolicy',
    'properties.getPolicy()',
    'REMOTE_CALL_PERMISSION_DENIED',
    'getBlockedServiceIds()',
    'getAllowedCallerIdsByService()'
  ],
  [files.noopCallPolicy]: ['implements RemoteCallPolicy'],
  [files.callObserver]: ['interface RemoteCallObserver', 'onComplete(RemoteCallObservation observation)'],
  [files.callObservation]: [
    'record RemoteCallObservation',
    'String requestId',
    'String traceId',
    'long durationMs',
    'String errorCode'
  ],
  [files.noopCallObserver]: ['implements RemoteCallObserver'],
  [files.httpClientConfig]: [
    '@ConditionalOnMissingBean(RemoteCallPolicy.class)',
    'new ConfiguredRemoteCallPolicy(properties)',
    '@ConditionalOnMissingBean(RemoteCallObserver.class)',
    'new NoopRemoteCallObserver()',
    '@ConditionalOnMissingBean(RemoteCallerResolver.class)',
    'new DefaultRemoteCallerResolver(properties)',
    '@ConditionalOnMissingBean(ServiceEndpointRegistry.class)',
    'new ConfiguredServiceEndpointRegistry(properties)'
  ],
  [files.embeddingService]: [
    'RemoteHttpClient',
    'RemoteHttpRequest.builder()',
    '.targetService("embedding-provider")',
    '.checkResponse(false)'
  ],
  [files.llmService]: [
    'RemoteHttpClient',
    'RemoteHttpRequest.builder()',
    '.targetService("llm-provider")',
    '.checkResponse(false)'
  ]
})) {
  for (const token of tokens) {
    requireToken(relativeFile, token)
  }
}

for (const file of [files.embeddingService, files.llmService]) {
  requireAbsent(file, 'org.springframework.web.client.RestTemplate')
  requireAbsent(file, 'restTemplate.exchange')
}

for (const token of [
  'service-base-urls:',
  `${applicationId}:`,
  'agent-doc-parser:',
  'REMOTE_HTTP_POLICY_ENABLED',
  'blocked-service-ids:',
  'allowed-caller-ids:'
]) {
  requireToken(files.application, token)
}

for (const token of [
  'BACKEND_PROPAGATED_HEADER_KEYS',
  'PlatformContractConstants.BACKEND_PROPAGATED_HEADER_KEYS',
  'appendContextHeader'
]) {
  requireToken(token === 'BACKEND_PROPAGATED_HEADER_KEYS' ? files.platform : files.remoteWrapper, token)
}

for (const token of [
  'doc-parser',
  'RemoteHttpClient',
  'EmbeddingService',
  'LLMService',
  'embedding-provider',
  'llm-provider',
  'service-base-urls:',
  'agent-doc-parser',
  'serviceId + path',
  'X-Request-Id',
  'X-Trace-Id',
  'X-Tenant-Id',
  'X-User-Id',
  'X-Caller-Id',
  'X-Time-Zone',
  'Accept-Language'
]) {
  requireToken(files.guide, token)
}

const guideSource = read(files.guide)
if (guideSource.includes('.url("http://infra-auth')) {
  fail('REMOTE_CALL_GUIDE should demonstrate serviceId + path for internal service calls')
}

const backendPropagatedHeaders = platformContract.backendPropagatedHeaders || []
const requestHeaders = platformContract.requestHeaders || {}
if (!backendPropagatedHeaders.length) {
  fail('contracts/platform-contract.json must define backendPropagatedHeaders')
}

for (const key of backendPropagatedHeaders) {
  if (!requestHeaders[key]) {
    fail(`backendPropagatedHeaders contains unknown request header key: ${key}`)
  }
  requireToken(files.remoteWrapper, `case "${key}"`)
  requireToken(files.guide, requestHeaders[key])
}

console.log('check-remote-http-contract: ok')
