/* eslint-disable */
// Generated from OpenAPI JSON. Do not edit manually.
// Run: node scripts/generate-openapi-frontend-types.js <openapi-json-file>

export type JsonObject = Record<string, unknown>

export interface APIResponseAuthTokenResponse {
  code?: string
  data?: AuthTokenResponse
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseChunk {
  code?: string
  data?: Chunk
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseConversationResponse {
  code?: string
  data?: ConversationResponse
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseCurrentUserResponse {
  code?: string
  data?: CurrentUserResponse
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseDocumentResponse {
  code?: string
  data?: DocumentResponse
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseKnowledgeBaseResponse {
  code?: string
  data?: KnowledgeBaseResponse
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseListDocumentProcessingTaskResponse {
  code?: string
  data?: DocumentProcessingTaskResponse[]
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseListDocumentResponse {
  code?: string
  data?: DocumentResponse[]
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseListKnowledgeBaseResponse {
  code?: string
  data?: KnowledgeBaseResponse[]
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseListMessageResponse {
  code?: string
  data?: MessageResponse[]
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseListSearchResult {
  code?: string
  data?: SearchResult[]
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseLong {
  code?: string
  data?: number
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseMapStringObject {
  code?: string
  data?: Record<string, unknown>
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseMessageResponse {
  code?: string
  data?: MessageResponse
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseMiddlewareStatusReport {
  code?: string
  data?: MiddlewareStatusReport
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponsePageResultChunk {
  code?: string
  data?: PageResultChunk
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponsePageResultConversationResponse {
  code?: string
  data?: PageResultConversationResponse
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponsePageResultDocumentResponse {
  code?: string
  data?: PageResultDocumentResponse
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponsePageResultKnowledgeBaseResponse {
  code?: string
  data?: PageResultKnowledgeBaseResponse
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseString {
  code?: string
  data?: string
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

export interface APIResponseVoid {
  code?: string
  data?: unknown
  message?: string
  requestId?: string
  success?: boolean
  timestamp?: number
}

/**
 * Authentication token payload
 */
export interface AuthTokenResponse {
  /**
   * Access token used in Authorization header
   */
  accessToken: string
  /**
   * Access token lifetime in seconds
   */
  expiresIn: number
  /**
   * Refresh token used to renew access token
   */
  refreshToken: string
  /**
   * Token type
   */
  tokenType: string
}

export interface BatchDeleteDocumentsRequest {
  docIds: string[]
}

export interface Chunk {
  chunkId?: string
  chunkIndex?: number
  chunkLength?: number
  content?: string
  createdAt?: string
  docId?: string
  embeddingStatus?: number
  isEnabled?: boolean
  kbId?: string
  metadata?: string
  taskId?: string
  tokenCount?: number
  updatedAt?: string
  vectorId?: string
}

export interface ConversationConfig {
  enableRerank?: boolean
  enableRetrieval?: boolean
  maxTokens?: number
  modelId?: string
  similarityThreshold?: number
  systemPrompt?: string
  temperature?: number
  topK?: number
}

export interface ConversationResponse {
  config?: unknown
  conversationId?: string
  createdAt?: string
  description?: string
  kbIds?: string[]
  messageCount?: number
  title?: string
  updatedAt?: string
}

export interface CreateConversationRequest {
  config?: ConversationConfig
  description?: string
  kbIds?: string[]
  title?: string
}

export interface CreateKnowledgeBaseRequest {
  avatar?: string
  chunkOverlap?: number
  chunkSize?: number
  description?: string
  embeddingModel?: string
  kbType?: string
  name: string
  raptorConfig?: string
  raptorEnabled?: boolean
}

/**
 * Current authenticated user payload
 */
export interface CurrentUserResponse {
  /**
   * Avatar URL
   */
  avatar?: string
  /**
   * User creation time in ISO-8601 UTC format
   */
  createTime?: string
  /**
   * User email
   */
  email?: string
  /**
   * Display nickname
   */
  nickName?: string
  /**
   * Permission codes
   */
  permissions?: string[]
  /**
   * Role codes
   */
  roles: string[]
  /**
   * User id
   */
  userId: number
  /**
   * Login username
   */
  userName: string
}

export interface DocumentProcessingTaskResponse {
  completedAt?: string
  createdAt?: string
  docId?: string
  errorMessage?: string
  kbId?: string
  message?: string
  parserTaskId?: string
  phase?: string
  progress?: number
  retryCount?: number
  startedAt?: string
  status?: string
  taskId?: string
  taskType?: string
  updatedAt?: string
}

export interface DocumentResponse {
  chunkNum?: number
  chunkStrategyId?: string
  completedAt?: string
  createdAt?: string
  docId?: string
  docName?: string
  docSize?: number
  docType?: string
  fileId?: string
  imageNum?: number
  isEnabled?: boolean
  kbId?: string
  parserStrategyId?: string
  progress?: number
  progressMsg?: string
  status?: string
  thumbnail?: string
  tokenNum?: number
  updatedAt?: string
}

export interface KnowledgeBaseResponse {
  avatar?: string
  chunkCount?: number
  chunkOverlap?: number
  chunkSize?: number
  createdAt?: string
  description?: string
  documentCount?: number
  embeddingModel?: string
  isEnabled?: boolean
  kbId?: string
  kbType?: string
  name?: string
  raptorConfig?: string
  raptorEnabled?: boolean
  tokenCount?: number
  updatedAt?: string
}

/**
 * Login request
 */
export interface LoginRequest {
  /**
   * Captcha code when enabled
   */
  captcha?: string
  /**
   * Password
   */
  password: string
  /**
   * Whether to keep the session longer
   */
  rememberMe?: boolean
  /**
   * Username or email
   */
  username: string
}

export interface MessageResponse {
  content?: string
  conversationId?: string
  createdAt?: string
  messageId?: string
  metadata?: Record<string, unknown>
  references?: ReferenceInfo[]
  role?: string
  sequence?: number
}

export interface MiddlewareInfo {
  details?: string
  enabled?: boolean
  name?: string
  status?: "disabled" | "configured" | "ready" | "degraded"
  statusCode?: string
  statusDescription?: string
  version?: string
}

export interface MiddlewareStatusReport {
  features?: MiddlewareInfo[]
  status?: "disabled" | "configured" | "ready" | "degraded"
  statusCode?: string
  statusDescription?: string
  summary?: MiddlewareSummary
}

export interface MiddlewareSummary {
  byStatus?: Record<string, number>
  enabled?: number
  total?: number
}

export interface OverrideConfig {
  enableRetrieval?: boolean
  maxTokens?: number
  modelId?: string
  temperature?: number
  topK?: number
}

export interface PageResultChunk {
  current?: number
  records?: Chunk[]
  size?: number
  total?: number
}

export interface PageResultConversationResponse {
  current?: number
  records?: ConversationResponse[]
  size?: number
  total?: number
}

export interface PageResultDocumentResponse {
  current?: number
  records?: DocumentResponse[]
  size?: number
  total?: number
}

export interface PageResultKnowledgeBaseResponse {
  current?: number
  records?: KnowledgeBaseResponse[]
  size?: number
  total?: number
}

export interface ReferenceInfo {
  chunkId?: string
  content?: string
  docId?: string
  docName?: string
  finalScore?: number
  kbId?: string
  kbName?: string
  metadata?: Record<string, unknown>
  score?: number
  similarityScore?: number
}

/**
 * Refresh token request
 */
export interface RefreshTokenRequest {
  /**
   * Refresh token returned by login
   */
  refreshToken: string
}

export interface SearchRequest {
  candidateCount?: number
  excludeChunkIds?: string[]
  excludeDocIds?: string[]
  kbIds: string[]
  query: string
  rerank?: boolean
  rerankLlmId?: string
  similarityThreshold?: number
  topK?: number
}

export interface SearchResult {
  chunkId?: string
  content?: string
  docId?: string
  docName?: string
  finalScore?: number
  highlightContent?: string
  kbId?: string
  kbName?: string
  metadata?: Record<string, unknown>
  rerankScore?: number
  similarityScore?: number
}

export interface SendMessageRequest {
  content: string
  conversationId?: string
  fileIds?: string[]
  imageUrls?: string[]
  kbIds?: string[]
  overrideConfig?: OverrideConfig
  stream?: boolean
}

export interface UpdateEnabledRequest {
  isEnabled: boolean
}

export interface UpdateKnowledgeBaseRequest {
  avatar?: string
  chunkOverlap?: number
  chunkSize?: number
  description?: string
  embeddingModel?: string
  isEnabled?: boolean
  name?: string
  raptorConfig?: string
  raptorEnabled?: boolean
}

export interface OpenApiSchemas {
  APIResponseAuthTokenResponse: APIResponseAuthTokenResponse
  APIResponseChunk: APIResponseChunk
  APIResponseConversationResponse: APIResponseConversationResponse
  APIResponseCurrentUserResponse: APIResponseCurrentUserResponse
  APIResponseDocumentResponse: APIResponseDocumentResponse
  APIResponseKnowledgeBaseResponse: APIResponseKnowledgeBaseResponse
  APIResponseListDocumentProcessingTaskResponse: APIResponseListDocumentProcessingTaskResponse
  APIResponseListDocumentResponse: APIResponseListDocumentResponse
  APIResponseListKnowledgeBaseResponse: APIResponseListKnowledgeBaseResponse
  APIResponseListMessageResponse: APIResponseListMessageResponse
  APIResponseListSearchResult: APIResponseListSearchResult
  APIResponseLong: APIResponseLong
  APIResponseMapStringObject: APIResponseMapStringObject
  APIResponseMessageResponse: APIResponseMessageResponse
  APIResponseMiddlewareStatusReport: APIResponseMiddlewareStatusReport
  APIResponsePageResultChunk: APIResponsePageResultChunk
  APIResponsePageResultConversationResponse: APIResponsePageResultConversationResponse
  APIResponsePageResultDocumentResponse: APIResponsePageResultDocumentResponse
  APIResponsePageResultKnowledgeBaseResponse: APIResponsePageResultKnowledgeBaseResponse
  APIResponseString: APIResponseString
  APIResponseVoid: APIResponseVoid
  AuthTokenResponse: AuthTokenResponse
  BatchDeleteDocumentsRequest: BatchDeleteDocumentsRequest
  Chunk: Chunk
  ConversationConfig: ConversationConfig
  ConversationResponse: ConversationResponse
  CreateConversationRequest: CreateConversationRequest
  CreateKnowledgeBaseRequest: CreateKnowledgeBaseRequest
  CurrentUserResponse: CurrentUserResponse
  DocumentProcessingTaskResponse: DocumentProcessingTaskResponse
  DocumentResponse: DocumentResponse
  KnowledgeBaseResponse: KnowledgeBaseResponse
  LoginRequest: LoginRequest
  MessageResponse: MessageResponse
  MiddlewareInfo: MiddlewareInfo
  MiddlewareStatusReport: MiddlewareStatusReport
  MiddlewareSummary: MiddlewareSummary
  OverrideConfig: OverrideConfig
  PageResultChunk: PageResultChunk
  PageResultConversationResponse: PageResultConversationResponse
  PageResultDocumentResponse: PageResultDocumentResponse
  PageResultKnowledgeBaseResponse: PageResultKnowledgeBaseResponse
  ReferenceInfo: ReferenceInfo
  RefreshTokenRequest: RefreshTokenRequest
  SearchRequest: SearchRequest
  SearchResult: SearchResult
  SendMessageRequest: SendMessageRequest
  UpdateEnabledRequest: UpdateEnabledRequest
  UpdateKnowledgeBaseRequest: UpdateKnowledgeBaseRequest
}
