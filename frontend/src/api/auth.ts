import request from '@/utils/http'
import { ApiPaths } from '@/api/paths'
import type { LoginParams, LoginResponse, UserInfo } from './model/authModel'

interface AuthTokenPayload {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

/**
 * 登录
 * @param params 登录参数
 * @returns 登录响应
 */
export function fetchLogin(params: LoginParams) {
  return request
    .post<AuthTokenPayload>({
      url: ApiPaths.auth.login,
      data: {
        username: params.userName,
        password: params.password
      }
    })
    .then((data): LoginResponse => ({
      token: data.accessToken,
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      tokenType: data.tokenType,
      expiresIn: data.expiresIn
    }))
}

/**
 * 获取用户信息
 * @returns 用户信息
 */
export function fetchGetUserInfo() {
  return request.get<UserInfo>({
    url: ApiPaths.auth.me
  })
}
