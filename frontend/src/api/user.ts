import { openApiRequest } from './openapiClient'
import type { LoginFormParams, LoginResponse, UserInfo } from './model/authModel'

export async function login(params: LoginFormParams): Promise<LoginResponse> {
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

export function logout(): Promise<void> {
  return openApiRequest('logout').then(() => undefined)
}

export function getCurrentUser(): Promise<UserInfo> {
  return openApiRequest('getCurrentUser')
}
