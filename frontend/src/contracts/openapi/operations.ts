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
  createItem: {
    method: "POST",
    path: "/api/test/items",
    operationId: "createItem"
  },
  deleteItem: {
    method: "DELETE",
    path: "/api/test/items/{id}",
    operationId: "deleteItem"
  },
  features: {
    method: "GET",
    path: "/api/test/features",
    operationId: "features"
  },
  getCurrentUser: {
    method: "GET",
    path: "/api/auth/me",
    operationId: "getCurrentUser"
  },
  getItem: {
    method: "GET",
    path: "/api/test/items/{id}",
    operationId: "getItem"
  },
  health: {
    method: "GET",
    path: "/api/test/health",
    operationId: "health"
  },
  listItems: {
    method: "GET",
    path: "/api/test/items",
    operationId: "listItems"
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
  updateItem: {
    method: "PUT",
    path: "/api/test/items/{id}",
    operationId: "updateItem"
  },
} as const satisfies Record<string, OpenApiOperationMeta>

export type OpenApiOperationId = keyof typeof OPENAPI_OPERATIONS

export interface OpenApiOperationTypes {
  createItem: {
    pathParams: undefined
    query: undefined
    request: Record<string, unknown>
    response: Schemas.APIResponseMapStringObject
    data: NonNullable<Schemas.APIResponseMapStringObject['data']>
  }
  deleteItem: {
    pathParams: { id: number }
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
  getCurrentUser: {
    pathParams: undefined
    query: undefined
    request: undefined
    response: Schemas.APIResponseCurrentUserResponse
    data: NonNullable<Schemas.APIResponseCurrentUserResponse['data']>
  }
  getItem: {
    pathParams: { id: number }
    query: undefined
    request: undefined
    response: Schemas.APIResponseMapStringObject
    data: NonNullable<Schemas.APIResponseMapStringObject['data']>
  }
  health: {
    pathParams: undefined
    query: undefined
    request: undefined
    response: Schemas.APIResponseMapStringObject
    data: NonNullable<Schemas.APIResponseMapStringObject['data']>
  }
  listItems: {
    pathParams: undefined
    query: { keyword?: string }
    request: undefined
    response: Schemas.APIResponseMapStringObject
    data: NonNullable<Schemas.APIResponseMapStringObject['data']>
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
  updateItem: {
    pathParams: { id: number }
    query: undefined
    request: Record<string, unknown>
    response: Schemas.APIResponseMapStringObject
    data: NonNullable<Schemas.APIResponseMapStringObject['data']>
  }
}

export type OpenApiOperationPathParams<T extends OpenApiOperationId> = OpenApiOperationTypes[T]['pathParams']
export type OpenApiOperationQuery<T extends OpenApiOperationId> = OpenApiOperationTypes[T]['query']
export type OpenApiOperationRequest<T extends OpenApiOperationId> = OpenApiOperationTypes[T]['request']
export type OpenApiOperationResponse<T extends OpenApiOperationId> = OpenApiOperationTypes[T]['response']
export type OpenApiOperationData<T extends OpenApiOperationId> = OpenApiOperationTypes[T]['data']
