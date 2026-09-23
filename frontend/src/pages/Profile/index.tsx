import { useEffect, useState } from 'react'
import { SaveOutlined, UserOutlined } from '@ant-design/icons'
import { Avatar, Button, Card, Flex, Form, Input, Typography, message } from 'antd'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/store/useUserStore'

const { Title, Text } = Typography

interface ProfileForm {
  nickname?: string
  email?: string
  avatar?: string
}

export default function Profile() {
  const user = useUserStore((state) => state.user)
  const setUser = useUserStore((state) => state.setUser)
  const [form] = Form.useForm<ProfileForm>()
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    form.setFieldsValue({
      nickname: user?.nickname,
      email: user?.email,
      avatar: user?.avatar,
    })
  }, [user, form])

  const handleSubmit = async (values: ProfileForm) => {
    setSaving(true)
    try {
      const updated = await authApi.updateMe(values)
      setUser(updated)
      message.success('资料已更新')
    } catch {
      // 错误提示由 axios 响应拦截器统一处理
    } finally {
      setSaving(false)
    }
  }

  return (
    <Flex vertical gap={16}>
      <Card>
        <Title level={4} style={{ marginTop: 0 }}>
          个人中心
        </Title>
        <Text type="secondary">修改昵称、邮箱与头像地址</Text>
      </Card>

      <Card>
        <Flex gap={32} align="flex-start" wrap>
          <Flex vertical align="center" gap={8} style={{ width: 96 }}>
            <Avatar size={72} src={user?.avatar} icon={<UserOutlined />} />
            <Text type="secondary">{user?.username}</Text>
          </Flex>

          <Form<ProfileForm>
            form={form}
            layout="vertical"
            onFinish={handleSubmit}
            style={{ flex: 1, minWidth: 280 }}
          >
            <Form.Item
              name="nickname"
              label="昵称"
              rules={[{ max: 20, message: '昵称最长 20 个字符' }]}
            >
              <Input placeholder="昵称" maxLength={20} />
            </Form.Item>

            <Form.Item
              name="email"
              label="邮箱"
              rules={[{ type: 'email', message: '邮箱格式不正确' }]}
            >
              <Input placeholder="you@example.com" maxLength={100} />
            </Form.Item>

            <Form.Item name="avatar" label="头像地址">
              <Input placeholder="https://..." maxLength={255} />
            </Form.Item>

            <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saving}>
              保存
            </Button>
          </Form>
        </Flex>
      </Card>
    </Flex>
  )
}
