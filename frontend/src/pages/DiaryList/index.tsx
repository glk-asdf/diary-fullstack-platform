import { Card, Empty, Flex, Typography } from 'antd'
import { useUserStore } from '@/store/useUserStore'

const { Title, Paragraph } = Typography

/**
 * 日记列表。P2 阶段仅作为登录后的落地页，日记功能在 P3 实现。
 */
export default function DiaryList() {
  const user = useUserStore((state) => state.user)

  return (
    <Flex vertical gap={16}>
      <Card>
        <Title level={4} style={{ marginTop: 0 }}>
          欢迎回来，{user?.nickname ?? user?.username}
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0 }}>
          认证链路已打通（P2）。日记的增删改查将在 P3 阶段实现。
        </Paragraph>
      </Card>

      <Card>
        <Empty description="日记功能开发中" />
      </Card>
    </Flex>
  )
}
