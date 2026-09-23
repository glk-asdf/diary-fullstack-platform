import { Flex, Spin, Typography } from 'antd'

const { Text } = Typography

/** 路由懒加载 / 首次进入时的占位，避免切换过程出现空白 */
export default function PageLoading() {
  return (
    <Flex vertical align="center" justify="center" gap={12} style={{ minHeight: '60vh' }}>
      <Spin size="large" />
      <Text type="secondary">加载中…</Text>
    </Flex>
  )
}
