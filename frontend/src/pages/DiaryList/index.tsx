import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  AppstoreOutlined,
  BarsOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import {
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Flex,
  Input,
  Pagination,
  Row,
  Segmented,
  Select,
  Skeleton,
  Space,
  Typography,
} from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import DiaryCard from '@/components/DiaryCard'
import { useDebounce } from '@/hooks/useDebounce'
import { useDiaryList } from '@/hooks/useDiaryList'
import { useTags } from '@/hooks/useTags'
import { MOOD_OPTIONS } from '@/types/diary'

const { RangePicker } = DatePicker
const { Text } = Typography

type ViewMode = 'card' | 'timeline'

export default function DiaryList() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { data: tags = [] } = useTags()

  // URL 是筛选条件的唯一数据源，刷新或分享链接后状态可复原
  const keyword = searchParams.get('keyword') ?? ''
  const mood = searchParams.get('mood') ? Number(searchParams.get('mood')) : undefined
  const tagId = searchParams.get('tagId') ? Number(searchParams.get('tagId')) : undefined
  const startDate = searchParams.get('startDate') ?? undefined
  const endDate = searchParams.get('endDate') ?? undefined
  const page = Number(searchParams.get('page') ?? 1)
  const size = Number(searchParams.get('size') ?? 9)
  const view = (searchParams.get('view') as ViewMode) ?? 'card'

  const patchParams = useCallback(
    (patch: Record<string, string | number | undefined>, resetPage = true) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          Object.entries(patch).forEach(([key, value]) => {
            if (value === undefined || value === '') {
              next.delete(key)
            } else {
              next.set(key, String(value))
            }
          })
          if (resetPage) {
            next.delete('page')
          }
          return next
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )

  // 搜索框本地态 + 300ms 防抖后回写 URL，避免逐字触发请求
  const [keywordInput, setKeywordInput] = useState(keyword)
  const debouncedKeyword = useDebounce(keywordInput, 300)

  useEffect(() => {
    const next = debouncedKeyword.trim()
    if (next !== (searchParams.get('keyword') ?? '')) {
      patchParams({ keyword: next || undefined })
    }
  }, [debouncedKeyword, searchParams, patchParams])

  const { data, isPending, isFetching } = useDiaryList({
    page,
    size,
    keyword: keyword || undefined,
    mood,
    tagId,
    startDate,
    endDate,
  })

  const records = data?.records ?? []
  const dateRange: [Dayjs, Dayjs] | null =
    startDate && endDate ? [dayjs(startDate), dayjs(endDate)] : null
  const hasFilter = Boolean(keyword || mood || tagId || startDate || endDate)

  const resetAll = () => {
    setKeywordInput('')
    setSearchParams({}, { replace: true })
  }

  return (
    <Flex vertical gap={16}>
      <Card>
        <Flex gap={12} wrap align="center" justify="space-between">
          <Space wrap>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="搜索标题或摘要"
              style={{ width: 220 }}
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
            />
            <Select
              allowClear
              placeholder="标签"
              style={{ width: 150 }}
              value={tagId}
              onChange={(value) => patchParams({ tagId: value })}
              options={tags.map((tag) => ({ value: tag.id, label: tag.name }))}
            />
            <Select
              allowClear
              placeholder="心情"
              style={{ width: 130 }}
              value={mood}
              onChange={(value) => patchParams({ mood: value })}
              options={MOOD_OPTIONS.map((item) => ({
                value: item.value,
                label: `${item.emoji} ${item.label}`,
              }))}
            />
            <RangePicker
              value={dateRange}
              onChange={(value) => {
                const range = value as [Dayjs, Dayjs] | null
                patchParams({
                  startDate: range?.[0]?.format('YYYY-MM-DD'),
                  endDate: range?.[1]?.format('YYYY-MM-DD'),
                })
              }}
            />
            {hasFilter && (
              <Button icon={<ReloadOutlined />} onClick={resetAll}>
                重置
              </Button>
            )}
          </Space>

          <Space>
            <Segmented
              value={view}
              onChange={(value) => patchParams({ view: value as string }, false)}
              options={[
                { value: 'card', icon: <AppstoreOutlined />, label: '卡片' },
                { value: 'timeline', icon: <BarsOutlined />, label: '时间轴' },
              ]}
            />
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/diaries/new')}>
              写日记
            </Button>
          </Space>
        </Flex>
      </Card>

      {isPending ? (
        <Card>
          <Skeleton active paragraph={{ rows: 6 }} />
        </Card>
      ) : records.length === 0 ? (
        <Card>
          <Empty description={hasFilter ? '没有符合条件的日记' : '还没有日记，开始记录吧'}>
            {hasFilter ? (
              <Button onClick={resetAll}>清除筛选条件</Button>
            ) : (
              <Button type="primary" onClick={() => navigate('/diaries/new')}>
                写第一篇
              </Button>
            )}
          </Empty>
        </Card>
      ) : view === 'card' ? (
        <Row gutter={[16, 16]}>
          {records.map((diary) => (
            <Col key={diary.id} xs={24} md={12} xl={8}>
              <DiaryCard diary={diary} onClick={() => navigate(`/diaries/${diary.id}`)} />
            </Col>
          ))}
        </Row>
      ) : (
        <Flex vertical gap={12}>
          {records.map((diary) => (
            <Flex key={diary.id} gap={12}>
              <Flex vertical align="center" style={{ width: 84, flexShrink: 0, paddingTop: 18 }}>
                <Text strong>{diary.diaryDate.slice(5)}</Text>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {diary.diaryDate.slice(0, 4)}
                </Text>
              </Flex>
              <div style={{ flex: 1, minWidth: 0 }}>
                <DiaryCard diary={diary} onClick={() => navigate(`/diaries/${diary.id}`)} />
              </div>
            </Flex>
          ))}
        </Flex>
      )}

      {data && data.total > 0 && (
        <Flex justify="center">
          <Pagination
            current={page}
            pageSize={size}
            total={Number(data.total)}
            showSizeChanger
            pageSizeOptions={[9, 18, 27]}
            showTotal={(total) => `共 ${total} 篇`}
            disabled={isFetching}
            onChange={(nextPage, nextSize) =>
              patchParams(
                { page: nextPage === 1 ? undefined : nextPage, size: nextSize },
                false,
              )
            }
          />
        </Flex>
      )}
    </Flex>
  )
}
