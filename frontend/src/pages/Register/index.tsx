import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { IdcardOutlined, LockOutlined, MailOutlined, UserOutlined } from '@ant-design/icons'
import { Button, Card, Flex, Form, Input, Typography, message } from 'antd'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/store/useUserStore'

const { Title, Text } = Typography

interface RegisterForm {
  username: string
  password: string
  confirmPassword: string
  nickname?: string
  email?: string
}

export default function Register() {
  const navigate = useNavigate()
  const clear = useUserStore((state) => state.clear)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (values: RegisterForm) => {
    setLoading(true)
    try {
      await authApi.register({
        username: values.username,
        password: values.password,
        nickname: values.nickname,
        email: values.email || undefined,
      })
      clear()
      message.success('注册成功，请登录')
      navigate('/login', { replace: true })
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
            创建账号
          </Title>
          <Text type="secondary">开始记录你的每一天</Text>
        </Flex>

        <Form<RegisterForm>
          layout="vertical"
          size="large"
          requiredMark={false}
          onFinish={handleSubmit}
        >
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              { required: true, message: '请输入用户名' },
              {
                pattern: /^[a-zA-Z0-9_]{4,20}$/,
                message: '4-20 位字母、数字或下划线',
              },
            ]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名" autoComplete="username" />
          </Form.Item>

          <Form.Item
            name="nickname"
            label="昵称（选填）"
            rules={[{ max: 20, message: '昵称最长 20 个字符' }]}
          >
            <Input prefix={<IdcardOutlined />} placeholder="不填则默认与用户名相同" />
          </Form.Item>

          <Form.Item
            name="email"
            label="邮箱（选填）"
            rules={[{ type: 'email', message: '邮箱格式不正确' }]}
          >
            <Input prefix={<MailOutlined />} placeholder="tester@example.com" />
          </Form.Item>

          <Form.Item
            name="password"
            label="密码"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, max: 32, message: '密码长度需为 6-32 位' },
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="6-32 位"
              autoComplete="new-password"
            />
          </Form.Item>

          <Form.Item
            name="confirmPassword"
            label="确认密码"
            dependencies={['password']}
            rules={[
              { required: true, message: '请再次输入密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('password') === value) {
                    return Promise.resolve()
                  }
                  return Promise.reject(new Error('两次输入的密码不一致'))
                },
              }),
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="再次输入密码"
              autoComplete="new-password"
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 12 }}>
            <Button type="primary" htmlType="submit" block loading={loading}>
              注册
            </Button>
          </Form.Item>
        </Form>

        <Flex justify="center">
          <Text type="secondary">
            已有账号？<Link to="/login">返回登录</Link>
          </Text>
        </Flex>
      </Card>
    </Flex>
  )
}
