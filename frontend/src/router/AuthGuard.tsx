import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useUserStore } from '@/store/useUserStore'

/**
 * 路由守卫：未登录时跳转登录页，并记录来源路径以便登录后回跳。
 */
export default function AuthGuard({ children }: { children: ReactNode }) {
  const accessToken = useUserStore((state) => state.accessToken)
  const location = useLocation()

  if (!accessToken) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }

  return <>{children}</>
}
