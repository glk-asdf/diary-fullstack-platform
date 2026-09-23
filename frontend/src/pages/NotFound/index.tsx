import { Button, Flex, Result } from 'antd'
import { useNavigate } from 'react-router-dom'

export default function NotFound() {
  const navigate = useNavigate()

  return (
    <Flex align="center" justify="center" style={{ minHeight: '60vh' }}>
      <Result
        status="404"
        title="404"
        subTitle="页面不存在或已被移除"
        extra={
          <Button type="primary" onClick={() => navigate('/diaries')}>
            返回首页
          </Button>
        }
      />
    </Flex>
  )
}
