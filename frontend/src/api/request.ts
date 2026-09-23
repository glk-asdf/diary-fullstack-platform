import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'
import { message } from 'antd'
import type { TokenVO } from './auth'
import { useUserStore } from '@/store/useUserStore'

const BASE_URL = '/api/v1'

/** 后端统一响应体 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 带重试标记的请求配置 */
interface RetriableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

const instance = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
})

/**
 * 独立实例专用于刷新令牌，避免刷新请求再次进入下面的响应拦截器造成递归。
 * 这里刻意不 import authApi，否则会与 auth.ts 形成运行时循环依赖。
 */
const rawInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
})

instance.interceptors.request.use((config) => {
  const token = useUserStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 并发去重：多个请求同时 401 时只触发一次刷新 */
let refreshing: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  const { refreshToken } = useUserStore.getState()
  if (!refreshToken) {
    throw new Error('缺少 refreshToken')
  }

  const res = await rawInstance.post<ApiResult<TokenVO>>('/auth/refresh', { refreshToken })
  if (res.data.code !== 0) {
    throw new Error(res.data.message)
  }

  const tokens = res.data.data
  useUserStore.getState().setTokens({
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
  })
  return tokens.accessToken
}

function forceLogout() {
  useUserStore.getState().clear()
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = '/login'
  }
}

instance.interceptors.response.use(
  (res) => {
    const { code, message: msg, data } = res.data as ApiResult<unknown>
    if (code !== 0) {
      message.error(msg)
      return Promise.reject(new Error(msg))
    }
    return data as never
  },
  async (error: AxiosError) => {
    const { response, config } = error

    // 401：静默刷新 token 并重放原请求
    if (response?.status === 401 && config && !(config as RetriableConfig)._retry) {
      const original = config as RetriableConfig
      original._retry = true

      try {
        refreshing ??= refreshAccessToken().finally(() => {
          refreshing = null
        })
        const newToken = await refreshing
        original.headers.Authorization = `Bearer ${newToken}`
        return instance(original)
      } catch {
        forceLogout()
        return Promise.reject(error)
      }
    }

    // 刷新失败或刷新接口本身返回 401：直接登出
    if (response?.status === 401) {
      forceLogout()
      return Promise.reject(error)
    }

    const msg =
      (response?.data as ApiResult<unknown> | undefined)?.message ??
      error.message ??
      '网络异常，请稍后重试'
    message.error(msg)
    return Promise.reject(error)
  },
)

/** 类型安全的请求方法，返回解包后的业务数据 */
export const http = {
  get<T>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
    return instance.get(url, { params, ...config }) as unknown as Promise<T>
  },
  post<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.post(url, body, config) as unknown as Promise<T>
  },
  put<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.put(url, body, config) as unknown as Promise<T>
  },
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.delete(url, config) as unknown as Promise<T>
  },
}

export default instance
