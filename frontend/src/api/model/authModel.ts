/**
 * 认证模块类型定义
 *
 * @module api/model/authModel
 */

import type {
  OpenApiOperationData,
  OpenApiOperationRequest
} from '@/contracts/openapi/operations'

/** 登录参数 */
export type LoginParams = OpenApiOperationRequest<'login'>

/** 登录表单兼容参数 */
export type LoginFormParams = Omit<LoginParams, 'username'> & {
  username?: string
  userName?: string
}

/** 登录响应 */
export type LoginResponse = OpenApiOperationData<'login'> & {
  token: string
}

/** 用户信息 */
export type UserInfo = OpenApiOperationData<'getCurrentUser'> & {
  buttons?: string[]
}

/** 刷新 Token 参数 */
export type RefreshTokenParams = OpenApiOperationRequest<'refreshToken'>
