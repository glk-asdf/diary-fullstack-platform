import { useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  BookOutlined,
  DashboardOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Avatar, Button, Dropdown, Flex, Layout, Menu, Typography, message } from 'antd'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/store/useUserStore'

const { Header, Sider, Content } = Layout
const { Text } = Typography

const MENU_ITEMS = [
  { key: '/diaries', icon: <BookOutlined />, label: '日记' },
  { key: '/stats', icon: <DashboardOutlined />, label: '统计' },
  { key: '/profile', icon: <UserOutlined />, label: '我的' },
]

export default function MainLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const user = useUserStore((state) => state.user)
  const refreshToken = useUserStore((state) => state.refreshToken)
  const clear = useUserStore((state) => state.clear)

  const selectedKey =
    MENU_ITEMS.find((item) => location.pathname.startsWith(item.key))?.key ?? '/diaries'

  const handleLogout = async () => {
    try {
      if (refreshToken) {
        await authApi.logout(refreshToken)
      }
    } catch {
      // 登出接口失败也要清掉本地状态，避免用户卡在已失效的登录态里
    } finally {
      clear()
      message.success('已退出登录')
      navigate('/login', { replace: true })
    }
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider collapsible collapsed={collapsed} trigger={null} theme="light">
        <Flex
          align="center"
          justify="center"
          style={{ height: 56, borderBottom: '1px solid #f0f0f0' }}
        >
          <Text strong style={{ fontSize: collapsed ? 16 : 18 }}>
            {collapsed ? '日' : '日记平台'}
          </Text>
        </Flex>

        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={MENU_ITEMS}
          style={{ borderInlineEnd: 'none' }}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>

      <Layout>
        <Header
          style={{
            background: '#fff',
            padding: '0 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid #f0f0f0',
          }}
        >
          <Button
            type="text"
            aria-label="切换侧边栏"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed((prev) => !prev)}
          />

          <Dropdown
            menu={{
              items: [
                { key: 'profile', icon: <UserOutlined />, label: '个人中心', onClick: () => navigate('/profile') },
                { type: 'divider' },
                { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: handleLogout },
              ],
            }}
          >
            <Flex align="center" gap={8} style={{ cursor: 'pointer', padding: '0 8px' }}>
              <Avatar src={user?.avatar} icon={<UserOutlined />} />
              <Text>{user?.nickname ?? user?.username ?? '未登录'}</Text>
            </Flex>
          </Dropdown>
        </Header>

        <Content style={{ margin: 16 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
