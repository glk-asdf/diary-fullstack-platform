import { Suspense, lazy, type ReactNode } from 'react'
import { Navigate, createBrowserRouter } from 'react-router-dom'
import PageLoading from '@/components/PageLoading'
import MainLayout from '@/layouts/MainLayout'
import AuthGuard from './AuthGuard'

// 路由级代码分割：各页面独立 chunk，避免首屏加载整个应用
const Login = lazy(() => import('@/pages/Login'))
const Register = lazy(() => import('@/pages/Register'))
const DiaryList = lazy(() => import('@/pages/DiaryList'))
const DiaryDetail = lazy(() => import('@/pages/DiaryDetail'))
const DiaryEdit = lazy(() => import('@/pages/DiaryEdit'))
const TagManage = lazy(() => import('@/pages/TagManage'))
const Stats = lazy(() => import('@/pages/Stats'))
const Profile = lazy(() => import('@/pages/Profile'))
const NotFound = lazy(() => import('@/pages/NotFound'))

/** MainLayout 不懒加载：它是所有受保护页面的外壳，懒加载收益小且会引入额外闪烁 */
const withSuspense = (node: ReactNode) => <Suspense fallback={<PageLoading />}>{node}</Suspense>

export const router = createBrowserRouter([
  { path: '/login', element: withSuspense(<Login />) },
  { path: '/register', element: withSuspense(<Register />) },
  {
    path: '/',
    element: (
      <AuthGuard>
        <MainLayout />
      </AuthGuard>
    ),
    children: [
      { index: true, element: <Navigate to="/diaries" replace /> },
      { path: 'diaries', element: withSuspense(<DiaryList />) },
      { path: 'diaries/new', element: withSuspense(<DiaryEdit />) },
      { path: 'diaries/:id', element: withSuspense(<DiaryDetail />) },
      { path: 'diaries/:id/edit', element: withSuspense(<DiaryEdit />) },
      { path: 'tags', element: withSuspense(<TagManage />) },
      { path: 'stats', element: withSuspense(<Stats />) },
      { path: 'profile', element: withSuspense(<Profile />) },
      { path: '*', element: withSuspense(<NotFound />) },
    ],
  },
])
