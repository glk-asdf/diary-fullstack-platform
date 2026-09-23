import { useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeftOutlined,
  CalendarOutlined,
  CloudOutlined,
  DeleteOutlined,
  EditOutlined,
} from '@ant-design/icons'
import { Button, Card, Empty, Flex, Modal, Skeleton, Tag, Typography } from 'antd'
import ReactMarkdown from 'react-markdown'
import rehypeSanitize from 'rehype-sanitize'
import { useDeleteDiary, useDiaryDetail } from '@/hooks/useDiaryList'
import { moodOf } from '@/types/diary'

const { Title, Text } = Typography

export default function DiaryDetail() {
  const { id } = useParams()
  const diaryId = Number(id)
  const navigate = useNavigate()

  const { data, isPending } = useDiaryDetail(diaryId)
  const deleteMutation = useDeleteDiary()

  const handleDelete = () => {
    Modal.confirm({
      title: '确认删除这篇日记？',
      content: '日记将被逻辑删除，列表中不再显示。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await deleteMutation.mutateAsync(diaryId)
        navigate('/diaries', { replace: true })
      },
    })
  }

  if (isPending) {
    return (
      <Card>
        <Skeleton active paragraph={{ rows: 10 }} />
      </Card>
    )
  }

  if (!data) {
    return (
      <Card>
        <Empty description="日记不存在或已被删除">
          <Button type="primary" onClick={() => navigate('/diaries')}>
            返回列表
          </Button>
        </Empty>
      </Card>
    )
  }

  const mood = moodOf(data.mood)

  return (
    <Flex vertical gap={16}>
      <Card>
        <Flex justify="space-between" align="flex-start" gap={16} wrap>
          <div style={{ flex: 1, minWidth: 240 }}>
            <Flex align="center" gap={4} style={{ marginBottom: 8 }}>
              <Button
                type="text"
                aria-label="返回列表"
                icon={<ArrowLeftOutlined />}
                onClick={() => navigate('/diaries')}
              />
              <Title level={3} style={{ margin: 0 }}>
                {data.title}
              </Title>
            </Flex>

            <Flex gap={16} wrap align="center">
              <Text type="secondary">
                <CalendarOutlined /> {data.diaryDate}
              </Text>
              {data.weather && (
                <Text type="secondary">
                  <CloudOutlined /> {data.weather}
                </Text>
              )}
              {mood && (
                <Text>
                  {mood.emoji} {mood.label}
                </Text>
              )}
              {data.isPublic === 1 && <Tag color="blue">公开</Tag>}
              {data.tags?.map((tag) => (
                <Tag key={tag.id} color={tag.color}>
                  {tag.name}
                </Tag>
              ))}
            </Flex>
          </div>

          <Flex gap={8}>
            <Button icon={<EditOutlined />} onClick={() => navigate(`/diaries/${diaryId}/edit`)}>
              编辑
            </Button>
            <Button danger icon={<DeleteOutlined />} onClick={handleDelete}>
              删除
            </Button>
          </Flex>
        </Flex>
      </Card>

      <Card>
        {data.content ? (
          <div className="markdown-body">
            <ReactMarkdown rehypePlugins={[rehypeSanitize]}>{data.content}</ReactMarkdown>
          </div>
        ) : (
          <Text type="secondary">（这篇日记还没有正文）</Text>
        )}
      </Card>
    </Flex>
  )
}
