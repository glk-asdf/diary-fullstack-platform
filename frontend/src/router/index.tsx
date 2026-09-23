import { Navigate, createBrowserRouter } from 'react-router-dom'
import MainLayout from '@/layouts/MainLayout'
import DiaryList from '@/pages/DiaryList'
import Login from '@/pages/Login'
import NotFound from '@/pages/NotFound'
import Register from '@/pages/Register'
import AuthGuard from './AuthGuard'

export const router = createBrowserRouter([
  { path: '/login', element: <Login /> },
  { path: '/register', element: <Register /> },
  {
    path: '/',
    element: (
      <AuthGuard>
        <MainLayout />
      </AuthGuard>
    ),
    children: [
      { index: true, element: <Navigate to="/diaries" replace /> },
      { path: 'diaries', element: <DiaryList /> },
      { path: '*', element: <NotFound /> },
    ],
  },
])
