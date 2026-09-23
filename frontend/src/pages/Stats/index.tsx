import { useMemo, useState } from 'react'
import {
  CalendarOutlined,
  FileTextOutlined,
  FireOutlined,
  RiseOutlined,
} from '@ant-design/icons'
import { Card, Col, Empty, Flex, Row, Select, Skeleton, Statistic, Typography } from 'antd'
import dayjs from 'dayjs'
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import { useCalendar, useOverview } from '@/hooks/useStats'
import { moodOf } from '@/types/diary'

const { Title, Text } = Typography

/** 篇数 → 5 级色阶 */
const levelOf = (count: number) => {
  if (count <= 0) return 0
  if (count === 1) return 1
  if (count <= 3) return 2
  if (count <= 6) return 3
  return 4
}

const UNRECORDED_COLOR = '#bfbfbf'

export default function Stats() {
  const thisYear = dayjs().year()
  const [year, setYear] = useState(thisYear)

  const { data: overview, isPending: overviewPending } = useOverview()
  const { data: calendar, isPending: calendarPending } = useCalendar(year)

  // 年份候选：从最早一篇所在年到今年
  const yearOptions = useMemo(() => {
    const first = overview?.firstDate ? dayjs(overview.firstDate).year() : thisYear
    const start = Math.min(first, thisYear)
    return Array.from({ length: thisYear - start + 1 }, (_, index) => thisYear - index)
  }, [overview?.firstDate, thisYear])

  // 按周切列（zh-cn locale 下周一为一周之首），前后补齐整周以保证行对齐
  const weeks = useMemo(() => {
    const end = dayjs(`${year}-12-31`)
    const cells: string[] = []
    let cursor = dayjs(`${year}-01-01`).startOf('week')
    while (cursor.isBefore(end) || cursor.isSame(end, 'day')) {
      cells.push(cursor.format('YYYY-MM-DD'))
      cursor = cursor.add(1, 'day')
    }
    const result: string[][] = []
    for (let i = 0; i < cells.length; i += 7) {
      result.push(cells.slice(i, i + 7))
    }
    return result
  }, [year])

  const totalInYear = useMemo(
    () => Array.from(calendar?.values() ?? []).reduce((sum, count) => sum + count, 0),
    [calendar],
  )

  const moodData = useMemo(
    () =>
      (overview?.moodDistribution ?? []).map((item) => {
        const mood = moodOf(item.mood)
        return {
          key: item.mood,
          name: mood ? `${mood.emoji} ${mood.label}` : '未记录',
          value: item.total,
          color: mood?.color ?? UNRECORDED_COLOR,
        }
      }),
    [overview?.moodDistribution],
  )

  return (
    <Flex vertical gap={16}>
      <Card>
        <Title level={4} style={{ marginTop: 0 }}>
          统计
        </Title>
        <Text type="secondary">
          {overview?.firstDate
            ? `从 ${dayjs(overview.firstDate).format('YYYY年MM月DD日')} 开始记录`
            : '还没有写作记录'}
        </Text>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={12} lg={6}>
          <Card>
            <Statistic
              title="总篇数"
              value={overview?.totalCount ?? 0}
              prefix={<FileTextOutlined />}
              loading={overviewPending}
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card>
            <Statistic
              title="本月"
              value={overview?.monthCount ?? 0}
              prefix={<CalendarOutlined />}
              loading={overviewPending}
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card>
            <Statistic
              title="当前连续"
              value={overview?.currentStreak ?? 0}
              suffix="天"
              prefix={<FireOutlined />}
              loading={overviewPending}
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card>
            <Statistic
              title="最长连续"
              value={overview?.longestStreak ?? 0}
              suffix="天"
              prefix={<RiseOutlined />}
              loading={overviewPending}
            />
          </Card>
        </Col>
      </Row>

      <Card
        title={`${year} 年写作热力图`}
        extra={
          <Select
            value={year}
            style={{ width: 116 }}
            onChange={setYear}
            options={yearOptions.map((item) => ({ value: item, label: `${item} 年` }))}
          />
        }
      >
        {calendarPending ? (
          <Skeleton active paragraph={{ rows: 4 }} />
        ) : (
          <>
            <Flex gap={4} style={{ overflowX: 'auto', paddingBottom: 8 }}>
              {weeks.map((week, weekIndex) => (
                <Flex key={weekIndex} vertical gap={4}>
                  {week.map((date) => {
                    const inYear = dayjs(date).year() === year
                    const count = calendar?.get(date) ?? 0
                    return (
                      <div
                        key={date}
                        className={`heat-cell heat-${inYear ? levelOf(count) : 0}${inYear ? '' : ' heat-out'}`}
                        title={inYear ? `${date}：${count} 篇` : undefined}
                      />
                    )
                  })}
                </Flex>
              ))}
            </Flex>

            <Flex justify="space-between" align="center" gap={8} wrap style={{ marginTop: 12 }}>
              <Text type="secondary">
                {year} 年共 {totalInYear} 篇
              </Text>
              <Flex align="center" gap={6}>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  少
                </Text>
                {[0, 1, 2, 3, 4].map((level) => (
                  <div key={level} className={`heat-cell heat-${level}`} />
                ))}
                <Text type="secondary" style={{ fontSize: 12 }}>
                  多
                </Text>
              </Flex>
            </Flex>
          </>
        )}
      </Card>

      <Card title="心情分布">
        {overviewPending ? (
          <Skeleton active paragraph={{ rows: 4 }} />
        ) : moodData.length === 0 ? (
          <Empty description="还没有数据" />
        ) : (
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={moodData}
                dataKey="value"
                nameKey="name"
                innerRadius={64}
                outerRadius={100}
                paddingAngle={2}
              >
                {moodData.map((item) => (
                  <Cell key={item.key} fill={item.color} />
                ))}
              </Pie>
              <Tooltip />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        )}
      </Card>
    </Flex>
  )
}
