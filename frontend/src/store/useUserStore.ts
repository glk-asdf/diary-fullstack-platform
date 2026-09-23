import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { UserVO } from '@/api/auth'

interface AuthPayload {
  accessToken: string
  refreshToken: string
  user: UserVO
}

interface TokenPayload {
  accessToken: string
  refreshToken: string
}

interface UserState {
  accessToken: string | null
  refreshToken: string | null
  user: UserVO | null
  setAuth: (payload: AuthPayload) => void
  /** 仅更新令牌对（刷新 token 时使用） */
  setTokens: (payload: TokenPayload) => void
  setUser: (user: UserVO) => void
  clear: () => void
}

/**
 * 登录态。持久化到 localStorage，刷新页面后仍保持登录。
 * 注意：axios 拦截器通过 getState() 读取，不要改为异步取值。
 */
export const useUserStore = create<UserState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setAuth: ({ accessToken, refreshToken, user }) => set({ accessToken, refreshToken, user }),
      setTokens: ({ accessToken, refreshToken }) => set({ accessToken, refreshToken }),
      setUser: (user) => set({ user }),
      clear: () => set({ accessToken: null, refreshToken: null, user: null }),
    }),
    { name: 'diary-auth' },
  ),
)
