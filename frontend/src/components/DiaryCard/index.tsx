import { CalendarOutlined, CloudOutlined } from '@ant-design/icons'
import { Card, Flex, Tag, Typography } from 'antd'
import { moodOf, type DiaryVO } from '@/types/diary'
import { friendlyDate } from '@/utils/date'

const { Title, Paragraph, Text } = Typography

interface Props {
  diary: DiaryVO
  onClick?: () => void
}

export default function DiaryCard({ diary, onClick }: Props) {
  const mood = moodOf(diary.mood)

  return (
    <Card hoverable onClick={onClick}>
      <Flex justify="space-between" align="flex-start" gap={12} style={{ marginBottom: 8 }}>
        <Title level={5} style={{ margin: 0 }} ellipsis={{ tooltip: diary.title }}>
          {diary.title}
        </Title>
        {mood && (
          <span title={mood.label} style={{ fontSize: 20, lineHeight: 1 }}>
            {mood.emoji}
          </span>
        )}
      </Flex>

      {diary.summary && (
        <Paragraph type="secondary" ellipsis={{ rows: 2 }} style={{ marginBottom: 12 }}>
          {diary.summary}
        </Paragraph>
      )}

      <Flex justify="space-between" align="center" gap={12}>
        <Flex gap={12} align="center" wrap>
          <Text type="secondary" style={{ fontSize: 12 }}>
            <CalendarOutlined /> {friendlyDate(diary.diaryDate)}
          </Text>
          {diary.weather && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              <CloudOutlined /> {diary.weather}
            </Text>
          )}
        </Flex>

        <Flex gap={4} wrap>
          {diary.tags?.map((tag) => (
            <Tag key={tag.id} color={tag.color} style={{ marginInlineEnd: 0 }}>
              {tag.name}
            </Tag>
          ))}
        </Flex>
      </Flex>
    </Card>
  )
}
