import { useEffect, useRef, useState } from 'react'
import { useBlocker, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeftOutlined, SaveOutlined } from '@ant-design/icons'
import {
  Button,
  Card,
  DatePicker,
  Flex,
  Form,
  Input,
  Modal,
  Skeleton,
  Space,
  Switch,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import MarkdownEditor from '@/components/MarkdownEditor'
import MoodPicker from '@/components/MoodPicker'
import TagSelect from '@/components/TagSelect'
import { useCreateDiary, useDiaryDetail, useUpdateDiary } from '@/hooks/useDiaryList'

const { Title } = Typography

interface DiaryForm {
  title: string
  diaryDate: dayjs.Dayjs
  mood?: number
  weather?: string
  tagIds?: number[]
  content?: string
  isPublic: boolean
}

export default function DiaryEdit() {
  const { id } = useParams()
  const diaryId = id ? Number(id) : undefined
  const isEdit = typeof diaryId === 'number' && diaryId > 0

  const navigate = useNavigate()
  const [form] = Form.useForm<DiaryForm>()

  const { data, isPending } = useDiaryDetail(diaryId)
  const createMutation = useCreateDiary()
  const updateMutation = useUpdateDiary()
  const saving = createMutation.isPending || updateMutation.isPending

  const [isDirty, setIsDirty] = useState(false)
  /** 保存成功后主动跳转时置位，避免被草稿保护拦截 */
  const allowNavigateRef = useRef(false)

  useEffect(() => {
    if (!isEdit) {
      form.setFieldsValue({ diaryDate: dayjs(), isPublic: false, content: '', tagIds: [] })
      return
    }
    if (data) {
      form.setFieldsValue({
        title: data.title,
        diaryDate: dayjs(data.diaryDate),
        mood: data.mood,
        weather: data.weather,
        tagIds: data.tags?.map((tag) => tag.id) ?? [],
        content: data.content ?? '',
        isPublic: data.isPublic === 1,
      })
      // 回填数据不算用户修改
      setIsDirty(false)
    }
  }, [isEdit, data, form])

  // 拦截站内路由跳转
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      !allowNavigateRef.current &&
      isDirty &&
      !saving &&
      currentLocation.pathname !== nextLocation.pathname,
  )

  // 拦截刷新 / 关闭标签页
  useEffect(() => {
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      if (isDirty && !saving) {
        event.preventDefault()
        event.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [isDirty, saving])

  const handleSubmit = async (values: DiaryForm) => {
    const payload = {
      title: values.title,
      content: values.content,
      mood: values.mood,
      weather: values.weather,
      diaryDate: values.diaryDate.format('YYYY-MM-DD'),
      tagIds: values.tagIds ?? [],
      isPublic: values.isPublic ? 1 : 0,
    }

    allowNavigateRef.current = true
    setIsDirty(false)

    if (isEdit && diaryId) {
      await updateMutation.mutateAsync({ id: diaryId, payload })
      navigate(`/diaries/${diaryId}`, { replace: true })
      return
    }

    const created = await createMutation.mutateAsync(payload)
    navigate(`/diaries/${created.id}`, { replace: true })
  }

  if (isEdit && isPending) {
    return (
      <Card>
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    )
  }

  return (
    <>
      <Form<DiaryForm>
        form={form}
        layout="vertical"
        requiredMark={false}
        onFinish={handleSubmit}
        onValuesChange={() => setIsDirty(true)}
      >
        <Flex vertical gap={16}>
          <Card>
            <Flex justify="space-between" align="center" gap={16} wrap>
              <Flex align="center" gap={4}>
                <Button
                  type="text"
                  aria-label="返回"
                  icon={<ArrowLeftOutlined />}
                  onClick={() => navigate(-1)}
                />
                <Title level={4} style={{ margin: 0 }}>
                  {isEdit ? '编辑日记' : '写日记'}
                </Title>
              </Flex>

              <Space>
                <Button onClick={() => navigate(-1)}>取消</Button>
                <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saving}>
                  保存
                </Button>
              </Space>
            </Flex>
          </Card>

          <Card>
            <Form.Item
              name="title"
              label="标题"
              rules={[
                { required: true, message: '请输入标题' },
                { max: 200, message: '标题最长 200 个字符' },
              ]}
            >
              <Input size="large" placeholder="给今天起个标题" maxLength={200} showCount />
            </Form.Item>

            <Flex gap={24} wrap align="flex-start">
              <Form.Item
                name="diaryDate"
                label="日期"
                rules={[{ required: true, message: '请选择日期' }]}
              >
                <DatePicker style={{ width: 180 }} />
              </Form.Item>

              <Form.Item name="weather" label="天气">
                <Input style={{ width: 160 }} placeholder="晴 / 多云 / 雨" maxLength={20} />
              </Form.Item>

              <Form.Item name="isPublic" label="公开" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Flex>

            <Form.Item name="mood" label="心情">
              <MoodPicker />
            </Form.Item>

            <Form.Item name="tagIds" label="标签" style={{ marginBottom: 0 }}>
              <TagSelect />
            </Form.Item>
          </Card>

          <Card>
            <Form.Item name="content" label="正文" style={{ marginBottom: 0 }}>
              <MarkdownEditor />
            </Form.Item>
          </Card>
        </Flex>
      </Form>

      <Modal
        open={blocker.state === 'blocked'}
        title="放弃未保存的修改？"
        okText="放弃并离开"
        cancelText="继续编辑"
        okButtonProps={{ danger: true }}
        onOk={() => blocker.proceed?.()}
        onCancel={() => blocker.reset?.()}
      >
        <p style={{ margin: 0 }}>当前编辑内容尚未保存，离开后将无法恢复。</p>
      </Modal>
    </>
  )
}
