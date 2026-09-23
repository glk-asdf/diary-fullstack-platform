import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Flex, Form, Input, Typography, message } from 'antd'
import { authApi, type LoginPayload } from '@/api/auth'
import { useUserStore } from '@/store/useUserStore'

const { Title, Text } = Typography

export default function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const setAuth = useUserStore((state) => state.setAuth)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (values: LoginPayload) => {
    setLoading(true)
    try {
      const data = await authApi.login(values)
      setAuth({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        user: data.user,
      })
      message.success('登录成功')
      const from = (location.state as { from?: string } | null)?.from
      navigate(from ?? '/diaries', { replace: true })
    } catch {
      // 统一错误提示由 axios 响应拦截器处理
    } finally {
      setLoading(false)
    }
  }

  return (
    <Flex
      align="center"
      justify="center"
      className="auth-page-bg"
      style={{ minHeight: '100vh', padding: 24 }}
    >
      <Card style={{ width: 400, boxShadow: '0 8px 24px rgba(0, 0, 0, 0.08)' }}>
        <Flex vertical align="center" style={{ marginBottom: 24 }}>
          <Title level={3} style={{ marginBottom: 4 }}>
            日记全栈平台
          </Title>
          <Text type="secondary">记录每一天的心情</Text>
        </Flex>

        {import.meta.env.DEV && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="测试账号：tester / 123456"
          />
        )}

        <Form<LoginPayload>
          layout="vertical"
          size="large"
          requiredMark={false}
          onFinish={handleSubmit}
        >
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名" autoComplete="username" />
          </Form.Item>

          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码"
              autoComplete="current-password"
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 12 }}>
            <Button type="primary" htmlType="submit" block loading={loading}>
              登录
            </Button>
          </Form.Item>
        </Form>

        <Flex justify="center">
          <Text type="secondary">
            还没有账号？<Link to="/register">立即注册</Link>
          </Text>
        </Flex>
      </Card>
    </Flex>
  )
}
