import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import {
  Button,
  Card,
  ColorPicker,
  Empty,
  Flex,
  Form,
  Input,
  List,
  Modal,
  Popconfirm,
  Skeleton,
  Space,
  Tag as AntTag,
  Typography,
} from 'antd'
import type { TagVO } from '@/types/diary'
import { TAG_PRESET_COLORS } from '@/components/TagSelect'
import { useCreateTag, useDeleteTag, useTags, useUpdateTag } from '@/hooks/useTags'

const { Title, Text } = Typography

interface TagForm {
  name: string
  color: string
}

export default function TagManage() {
  const { data: tags = [], isPending } = useTags()
  const createTag = useCreateTag()
  const updateTag = useUpdateTag()
  const deleteTag = useDeleteTag()

  const [form] = Form.useForm<TagForm>()

  const openCreate = () => {
    form.setFieldsValue({ name: '', color: TAG_PRESET_COLORS[0] })
    Modal.confirm({
      title: '新建标签',
      icon: null,
      width: 420,
      content: <TagFormFields form={form} />,
      okText: '创建',
      cancelText: '取消',
      onOk: async () => {
        const values = await form.validateFields()
        await createTag.mutateAsync(values)
      },
    })
  }

  const openEdit = (tag: TagVO) => {
    form.setFieldsValue({ name: tag.name, color: tag.color ?? TAG_PRESET_COLORS[0] })
    Modal.confirm({
      title: '编辑标签',
      icon: null,
      width: 420,
      content: <TagFormFields form={form} />,
      okText: '保存',
      cancelText: '取消',
      onOk: async () => {
        const values = await form.validateFields()
        await updateTag.mutateAsync({ id: tag.id, payload: values })
      },
    })
  }

  return (
    <Flex vertical gap={16}>
      <Card>
        <Flex justify="space-between" align="center" gap={16} wrap>
          <div>
            <Title level={4} style={{ margin: 0 }}>
              标签管理
            </Title>
            <Text type="secondary">标签按用户隔离，同一用户下名称不可重复</Text>
          </div>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建标签
          </Button>
        </Flex>
      </Card>

      <Card>
        {isPending ? (
          <Skeleton active paragraph={{ rows: 5 }} />
        ) : tags.length === 0 ? (
          <Empty description="还没有标签，创建一个用于归类日记">
            <Button type="primary" onClick={openCreate}>
              新建标签
            </Button>
          </Empty>
        ) : (
          <List
            dataSource={tags}
            renderItem={(tag) => (
              <List.Item
                actions={[
                  <Button
                    key="edit"
                    type="text"
                    icon={<EditOutlined />}
                    onClick={() => openEdit(tag)}
                  >
                    编辑
                  </Button>,
                  <Popconfirm
                    key="delete"
                    title="删除这个标签？"
                    description="删除后会同时解除它与所有日记的关联。"
                    okText="删除"
                    cancelText="取消"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => deleteTag.mutateAsync(tag.id)}
                  >
                    <Button type="text" danger icon={<DeleteOutlined />}>
                      删除
                    </Button>
                  </Popconfirm>,
                ]}
              >
                <Space>
                  <AntTag color={tag.color}>{tag.name}</AntTag>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {tag.color}
                  </Text>
                </Space>
              </List.Item>
            )}
          />
        )}
      </Card>
    </Flex>
  )
}

/** 抽出表单字段，供 Modal.confirm 的 content 复用 */
function TagFormFields({ form }: { form: ReturnType<typeof Form.useForm<TagForm>>[0] }) {
  return (
    <Form<TagForm> form={form} layout="vertical" style={{ marginTop: 16 }}>
      <Form.Item
        name="name"
        label="标签名"
        rules={[
          { required: true, message: '请输入标签名' },
          { max: 30, message: '标签名最长 30 个字符' },
        ]}
      >
        <Input placeholder="例如：旅行" maxLength={30} />
      </Form.Item>
      <Form.Item name="color" label="颜色">
        <ColorPicker
          presets={[{ label: '推荐', colors: TAG_PRESET_COLORS }]}
          format="hex"
          showText
        />
      </Form.Item>
    </Form>
  )
}
