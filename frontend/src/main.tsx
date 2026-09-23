import { StrictMode, useEffect } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntdApp, ConfigProvider, theme } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { RouterProvider } from 'react-router-dom'
import '@ant-design/v5-patch-for-react-19'
import '@uiw/react-md-editor/markdown-editor.css'
import ErrorBoundary from '@/components/ErrorBoundary'
import { router } from '@/router'
import { useThemeStore } from '@/store/useThemeStore'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 30_000,
    },
  },
})

function Root() {
  const mode = useThemeStore((state) => state.mode)

  // 同步到 :root，供自定义 CSS（Markdown 渲染、登录页背景）适配暗色
  useEffect(() => {
    document.documentElement.dataset.theme = mode
  }, [mode])

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: mode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 6,
        },
      }}
    >
      <AntdApp>
        <ErrorBoundary>
          <RouterProvider router={router} />
        </ErrorBoundary>
      </AntdApp>
    </ConfigProvider>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <Root />
    </QueryClientProvider>
  </StrictMode>,
)
