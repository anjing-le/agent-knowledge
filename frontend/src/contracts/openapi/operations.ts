/* eslint-disable */
// Generated from OpenAPI JSON. Do not edit manually.
// Run: node scripts/generate-openapi-frontend-types.js <openapi-json-file>

import type * as Schemas from './schemas'

export type OpenApiHttpMethod = 'DELETE' | 'GET' | 'PATCH' | 'POST' | 'PUT'

export interface OpenApiOperationMeta {
  method: OpenApiHttpMethod
  path: string
  operationId: string
}

export const OPENAPI_OPERATIONS = {
  batchDeleteDocuments: {
    method: "POST",
    path: "/api/knowledge/documents/batch-delete",
    operationId: "batchDeleteDocuments"
  },
  batchUploadDocuments: {
    method: "POST",
    path: "/api/knowledge/bases/{kbId}/documents",
    operationId: "batchUploadDocuments"
  },
  createConversation: {
    method: "POST",
    path: "/api/chat/conversations",
    operationId: "createConversation"
  },
  createItem: {
    method: "POST",
    path: "/api/test/items",
    operationId: "createItem"
  },
  createKnowledgeBase: {
    method: "POST",
    path: "/api/knowledge/bases",
    operationId: "createKnowledgeBase"
  },
  deleteConversation: {
    method: "DELETE",
    path: "/api/chat/conversations/{conversationId}",
    operationId: "deleteConversation"
  },
  deleteDocument: {
    method: "DELETE",
    path: "/api/knowledge/documents/{docId}",
    operationId: "deleteDocument"
  },
  deleteItem: {
    method: "DELETE",
    path: "/api/test/items/{id}",
    operationId: "deleteItem"
  },
  deleteKnowledgeBase: {
    method: "DELETE",
    path: "/api/knowledge/bases/{kbId}",
    operationId: "deleteKnowledgeBase"
  },
  features: {
    method: "GET",
    path: "/api/test/features",
    operationId: "features"
  },
  getChunk: {
    method: "GET",
    path: "/api/knowledge/chunks/{chunkId}",
    operationId: "getChunk"
  },
  getChunkCount: {
    method: "GET",
    path: "/api/knowledge/documents/{docId}/chunks/count",
    operationId: "getChunkCount"
  },
  getConversation: {
    method: "GET",
    path: "/api/chat/conversations/{conversationId}",
    operationId: "getConversation"
  },
  getCurrentUser: {
    method: "GET",
    path: "/api/auth/me",
    operationId: "getCurrentUser"
  },
  getDocument: {
    method: "GET",
    path: "/api/knowledge/documents/{docId}",
    operationId: "getDocument"
  },
  getItem: {
    method: "GET",
    path: "/api/test/items/{id}",
    operationId: "getItem"
  },
  getKnowledgeBase: {
    method: "GET",
    path: "/api/knowledge/bases/{kbId}",
    operationId: "getKnowledgeBase"
  },
  getMessages: {
    method: "GET",
    path: "/api/chat/conversations/{conversationId}/messages",
    operationId: "getMessages"
  },
  health: {
    method: "GET",
    path: "/api/test/health",
    operationId: "health"
  },
  listAllKnowledgeBases: {
    method: "GET",
    path: "/api/knowledge/bases/all",
    operationId: "listAllKnowledgeBases"
  },
  listChunks: {
    method: "GET",
    path: "/api/knowledge/documents/{docId}/chunks",
    operationId: "listChunks"
  },
  listConversations: {
    method: "GET",
    path: "/api/chat/conversations",
    operationId: "listConversations"
  },
  listDocuments: {
    method: "GET",
    path: "/api/knowledge/bases/{kbId}/documents",
    operationId: "listDocuments"
  },
  listDocumentTasks: {
    method: "GET",
    path: "/api/knowledge/documents/{docId}/tasks",
    operationId: "listDocumentTasks"
  },
  listItems: {
    method: "GET",
    path: "/api/test/items",
    operationId: "listItems"
  },
  listKnowledgeBases: {
    method: "GET",
    path: "/api/knowledge/bases",
    operationId: "listKnowledgeBases"
  },
  login: {
    method: "POST",
    path: "/api/auth/login",
    operationId: "login"
  },
  logout: {
    method: "POST",
    path: "/api/auth/logout",
    operationId: "logout"
  },
  ping: {
    method: "GET",
    path: "/api/test/ping",
    operationId: "ping"
  },
  refreshToken: {
    method: "POST",
    path: "/api/auth/refresh",
    operationId: "refreshToken"
  },
  reprocessDocument: {
    method: "POST",
    path: "/api/knowledge/documents/{docId}/reprocess",
    operationId: "reprocessDocument"
  },
  search: {
    method: "POST",
    path: "/api/retrieval/search",
    operationId: "search"
  },
  sendMessage: {
    method: "POST",
    path: "/api/chat/conversations/{conversationId}/messages",
    operationId: "sendMessage"
  },
  setDocumentEnabled: {
    method: "PUT",
    path: "/api/knowledge/documents/{docId}/enabled",
    operationId: "setDocumentEnabled"
  },
  simpleSearch: {
    method: "GET",
    path: "/api/retrieval/simple",
    operationId: "simpleSearch"
  },
  testBizException: {
    method: "GET",
    path: "/api/test/exception/biz",
    operationId: "testBizException"
  },
  testSystemException: {
    method: "GET",
    path: "/api/test/exception/system",
    operationId: "testSystemException"
  },
  updateChunkStatus: {
    method: "PUT",
    path: "/api/knowledge/chunks/{chunkId}/enabled",
    operationId: "updateChunkStatus"
  },
  updateConversationTitle: {
    method: "PUT",
    path: "/api/chat/conversations/{conversationId}/title",
    operationId: "updateConversationTitle"
  },
  updateItem: {
    method: "PUT",
    path: "/api/test/items/{id}",
    operationId: "updateItem"
  },
  updateKnowledgeBase: {
    method: "PUT",
    path: "/api/knowledge/bases/{kbId}",
    operationId: "updateKnowledgeBase"
  },
} as const satisfies Record<string, OpenApiOperationMeta>

export type OpenApiOperationId = keyof typeof OPENAPI_OPERATIONS

export interface OpenApiOperationTypes {
  batchDeleteDocuments: {
    pathParams: undefined
    query: undefined
    request: Schemas.BatchDeleteDocumentsRequest
    response: Schemas.APIResponseVoid
    data: NonNullable<Schemas.APIResponseVoid['data']>
  }
  batchUploadDocuments: {
    pathParams: { kbId: string }
    query: { chunkStrategyId?: string; files: string[]; parserStrategyId?: string }
    request: undefined
    response: Schemas.APIResponseDocumentResponse | Schemas.APIResponseListDocumentResponse
    data: NonNullable<Schemas.APIResponseDocumentResponse | Schemas.APIResponseListDocumentResponse['data']>
  }
  createConversation: {
    pathParams: undefined
    query: undefined
    request: Schemas.CreateConversationRequest
    response: Schemas.APIResponseConversationResponse
    data: NonNullable<Schemas.APIResponseConversationResponse['data']>
  }
  createItem: {
    pathParams: undefined
    query: undefined
    request: Record<string, unknown>
    response: Schemas.APIResponseMapStringObject
    data: NonNullable<Schemas.APIResponseMapStringObject['data']>
  }
  createKnowledgeBase: {
    pathParams: undefined
    query: undefined
    request: Schemas.CreateKnowledgeBaseRequest
    response: Schemas.APIResponseKnowledgeBaseResponse
    data: NonNullable<Schemas.APIResponseKnowledgeBaseResponse['data']>
  }
  deleteConversation: {
    pathParams: { conversationId: string }
    query: undefined
    request: undefined
    response: Schemas.APIResponseVoid
    data: NonNullable<Schemas.APIResponseVoid['data']>
  }
  deleteDocument: {
    pathParams: { docId: string }
    query: undefined
    request: undefined
    response: Schemas.APIResponseVoid
    data: NonNullable<Schemas.APIResponseVoid['data']>
  }
  deleteItem: {
    pathParams: { id: number }
    query: undefined
    request: undefined
    response: Schemas.APIResponseVoid
    data: NonNullable<Schemas.APIResponseVoid['data']>
  }
  deleteKnowledgeBase: {
    pathParams: { kbId: string }
    query: undefined
    request: undefined
    response: Schemas.APIResponseVoid
    data: NonNullable<Schemas.APIResponseVoid['data']>
  }
  features: {
    pathParams: undefined
    query: undefined
    request: undefined
    response: Schemas.APIResponseMiddlewareStatusReport
    data: NonNullable<Schemas.APIResponseMiddlewareStatusReport['data']>
  }
  getChunk: {
    pathParams: { chunkId: string }
    query: undefined
    request: undefined
    response: Schemas.APIResponseChunkResponse
    data: NonNullable<Schemas.APIResponseChunkResponse['data']>
  }
  getChunkCount: {
    pathParams: { docId: string }
    query: undefined
    request: undefined
    response: Schemas.APIResponseLong
    data: NonNullable<Schemas.APIResponseLong['data']>
  }
  getConversation: {
    pathParams: { conversationId: string }
    query: undefined
    request: undefined
    response: Schemas.APIResponseConversationResponse
    data: NonNullable<Schemas.APIResponseConversationResponse['data']>
  }
  getCurrentUser: {
    pathParams: undefined
    query: undefined
    request: undefined
    response: Schemas.APIResponseCurrentUserResponse
    data: NonNullable<Schemas.APIResponseCurrentUserResponse['data']>
  }
  getDocument: {
    pathParams: { docId: string }
    query: undefined
    request: undefined
    response: Schemas.APIResponseDocumentResponse
    data: NonNullable<Schemas.APIResponseDocumentResponse['data']>
  }
  getItem: {
    pathParams: { id: number }
    query: undefined
    request: undefined
    response: Schemas.APIResponseMapStringObject
    data: NonNullable<Schemas.APIResponseMapStringObject['data']>
  }
  getKnowledgeBase: {
    pathParams: { kbId: string }
    query: undefined
    request: undefined
    response: Schemas.APIResponseKnowledgeBaseResponse
    data: NonNullable<Schemas.APIResponseKnowledgeBaseResponse['data']>
  }
  getMessages: {
    pathParams: { conversationId: string }
    query: undefined
    request: undefined
    response: Schemas.APIResponseListMessageResponse
    data: NonNullable<Schemas.APIResponseListMessageResponse['data']>
  }
  health: {
    pathParams: undefined
    query: undefined
    request: undefined
    response: Schemas.APIResponseMapStringObject
    data: NonNullable<Schemas.APIResponseMapStringObject['data']>
  }
  listAllKnowledgeBases: {
    pathParams: undefined
    query: undefined
    request: undefined
    response: Schemas.APIResponseListKnowledgeBaseResponse
    data: NonNullable<Schemas.APIResponseListKnowledgeBaseResponse['data']>
  }
  listChunks: {
    pathParams: { docId: string }
    query: { page?: number; size?: number }
    request: undefined
    response: Schemas.APIResponsePageResultChunkResponse
    data: NonNullable<Schemas.APIResponsePageResultChunkResponse['data']>
  }
  listConversations: {
    pathParams: undefined
    query: { page?: number; size?: number }
    request: undefined
    response: Schemas.APIResponsePageResultConversationResponse
    data: NonNullable<Schemas.APIResponsePageResultConversationResponse['data']>
  }
  listDocuments: {
    pathParams: { kbId: string }
    query: { keyword?: string; page?: number; size?: number }
    request: undefined
    response: Schemas.APIResponsePageResultDocumentResponse
    data: NonNullable<Schemas.APIResponsePageResultDocumentResponse['data']>
  }
  listDocumentTasks: {
    pathParams: { docId: string }
    query: undefined
    request: undefined
    response: Schemas.APIResponseListDocumentProcessingTaskResponse
    data: NonNullable<Schemas.APIResponseListDocumentProcessingTaskResponse['data']>
  }
  listItems: {
    pathParams: undefined
    query: { keyword?: string }
    request: undefined
    response: Schemas.APIResponseMapStringObject
    data: NonNullable<Schemas.APIResponseMapStringObject['data']>
  }
  listKnowledgeBases: {
    pathParams: undefined
    query: { keyword?: string; page?: number; size?: number }
    request: undefined
    response: Schemas.APIResponsePageResultKnowledgeBaseResponse
    data: NonNullable<Schemas.APIResponsePageResultKnowledgeBaseResponse['data']>
  }
  login: {
    pathParams: undefined
    query: undefined
    request: Schemas.LoginRequest
    response: Schemas.APIResponseAuthTokenResponse
    data: NonNullable<Schemas.APIResponseAuthTokenResponse['data']>
  }
  logout: {
    pathParams: undefined
    query: undefined
    request: undefined
    response: Schemas.APIResponseVoid
    data: NonNullable<Schemas.APIResponseVoid['data']>
  }
  ping: {
    pathParams: undefined
    query: undefined
    request: undefined
    response: Schemas.APIResponseString
    data: NonNullable<Schemas.APIResponseString['data']>
  }
  refreshToken: {
    pathParams: undefined
    query: undefined
    request: Schemas.RefreshTokenRequest
    response: Schemas.APIResponseAuthTokenResponse
    data: NonNullable<Schemas.APIResponseAuthTokenResponse['data']>
  }
  reprocessDocument: {
    pathParams: { docId: string }
    query: undefined
    request: undefined
    response: Schemas.APIResponseVoid
    data: NonNullable<Schemas.APIResponseVoid['data']>
  }
  search: {
    pathParams: undefined
    query: undefined
    request: Schemas.SearchRequest
    response: Schemas.APIResponseListSearchResult
    data: NonNullable<Schemas.APIResponseListSearchResult['data']>
  }
  sendMessage: {
    pathParams: { conversationId: string }
    query: undefined
    request: Schemas.SendMessageRequest
    response: Schemas.APIResponseMessageResponse
    data: NonNullable<Schemas.APIResponseMessageResponse['data']>
  }
  setDocumentEnabled: {
    pathParams: { docId: string }
    query: undefined
    request: Schemas.UpdateEnabledRequest
    response: Schemas.APIResponseVoid
    data: NonNullable<Schemas.APIResponseVoid['data']>
  }
  simpleSearch: {
    pathParams: undefined
    query: { kbId: string; query: string; topK?: number }
    request: undefined
    response: Schemas.APIResponseListSearchResult
    data: NonNullable<Schemas.APIResponseListSearchResult['data']>
  }
  testBizException: {
    pathParams: undefined
    query: undefined
    request: undefined
    response: Schemas.APIResponseVoid
    data: NonNullable<Schemas.APIResponseVoid['data']>
  }
  testSystemException: {
    pathParams: undefined
    query: undefined
    request: undefined
    response: Schemas.APIResponseVoid
    data: NonNullable<Schemas.APIResponseVoid['data']>
  }
  updateChunkStatus: {
    pathParams: { chunkId: string }
    query: undefined
    request: Schemas.UpdateEnabledRequest
    response: Schemas.APIResponseVoid
    data: NonNullable<Schemas.APIResponseVoid['data']>
  }
  updateConversationTitle: {
    pathParams: { conversationId: string }
    query: { title: string }
    request: undefined
    response: Schemas.APIResponseConversationResponse
    data: NonNullable<Schemas.APIResponseConversationResponse['data']>
  }
  updateItem: {
    pathParams: { id: number }
    query: undefined
    request: Record<string, unknown>
    response: Schemas.APIResponseMapStringObject
    data: NonNullable<Schemas.APIResponseMapStringObject['data']>
  }
  updateKnowledgeBase: {
    pathParams: { kbId: string }
    query: undefined
    request: Schemas.UpdateKnowledgeBaseRequest
    response: Schemas.APIResponseKnowledgeBaseResponse
    data: NonNullable<Schemas.APIResponseKnowledgeBaseResponse['data']>
  }
}

export type OpenApiOperationPathParams<T extends OpenApiOperationId> = OpenApiOperationTypes[T]['pathParams']
export type OpenApiOperationQuery<T extends OpenApiOperationId> = OpenApiOperationTypes[T]['query']
export type OpenApiOperationRequest<T extends OpenApiOperationId> = OpenApiOperationTypes[T]['request']
export type OpenApiOperationResponse<T extends OpenApiOperationId> = OpenApiOperationTypes[T]['response']
export type OpenApiOperationData<T extends OpenApiOperationId> = OpenApiOperationTypes[T]['data']
