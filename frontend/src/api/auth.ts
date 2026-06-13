import { openApiRequest } from './openapiClient'
import type {
  LoginFormParams,
  LoginResponse,
  RefreshTokenParams,
  UserInfo
} from './model/authModel'

/**
 * 登录
 * @param params 登录参数
 * @returns 登录响应
 */
export async function fetchLogin(params: LoginFormParams): Promise<LoginResponse> {
  const data = await openApiRequest('login', {
    body: {
      username: params.username || params.userName || '',
      password: params.password,
      rememberMe: params.rememberMe,
      captcha: params.captcha
    }
  })

  return {
    ...data,
    token: data.accessToken
  }
}

/**
 * 获取用户信息
 * @returns 用户信息
 */
export function fetchGetUserInfo(): Promise<UserInfo> {
  return openApiRequest('getCurrentUser')
}

/**
 * 刷新访问令牌
 */
export function fetchRefreshToken(params: RefreshTokenParams): Promise<LoginResponse> {
  return openApiRequest('refreshToken', { body: params }).then((data) => ({
    ...data,
    token: data.accessToken
  }))
}

/**
 * 退出登录
 */
export function fetchLogout(): Promise<void> {
  return openApiRequest('logout').then(() => undefined)
}
