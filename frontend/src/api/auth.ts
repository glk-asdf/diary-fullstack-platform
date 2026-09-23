import { http } from './request'

export interface UserVO {
  id: number
  username: string
  nickname?: string
  avatar?: string
  email?: string
}

export interface TokenVO {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserVO
}

export interface RegisterPayload {
  username: string
  password: string
  nickname?: string
  email?: string
}

export interface LoginPayload {
  username: string
  password: string
}

export const authApi = {
  register: (payload: RegisterPayload) => http.post<UserVO>('/auth/register', payload),
  login: (payload: LoginPayload) => http.post<TokenVO>('/auth/login', payload),
  refresh: (refreshToken: string) => http.post<TokenVO>('/auth/refresh', { refreshToken }),
  logout: (refreshToken: string) => http.post<void>('/auth/logout', { refreshToken }),
  me: () => http.get<UserVO>('/users/me'),
  updateMe: (payload: Partial<Pick<UserVO, 'nickname' | 'avatar' | 'email'>>) =>
    http.put<UserVO>('/users/me', payload),
}
