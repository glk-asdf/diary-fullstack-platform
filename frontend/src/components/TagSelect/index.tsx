import { useState } from 'react'
import { PlusOutlined } from '@ant-design/icons'
import {
  Button,
  ColorPicker,
  Divider,
  Flex,
  Input,
  Select,
  Tag as AntTag,
  Typography,
  message,
} from 'antd'
import { useCreateTag, useTags } from '@/hooks/useTags'

const { Text } = Typography

export const TAG_PRESET_COLORS = [
  '#1677ff',
  '#52c41a',
  '#faad14',
  '#f5222d',
  '#722ed1',
  '#13c2c2',
  '#eb2f96',
  '#fa8c16',
]

interface Props {
  value?: number[]
  onChange?: (value: number[]) => void
}

/**
 * 标签多选，支持在下拉里直接创建新标签。
 * 作为受控组件可直接放进 antd 的 Form.Item。
 */
export default function TagSelect({ value = [], onChange }: Props) {
  const { data: tags = [] } = useTags()
  const createTag = useCreateTag()

  const [newName, setNewName] = useState('')
  const [newColor, setNewColor] = useState(TAG_PRESET_COLORS[0])

  const handleCreate = async () => {
    const name = newName.trim()
    if (!name) {
      message.warning('请输入标签名')
      return
    }
    const created = await createTag.mutateAsync({ name, color: newColor })
    onChange?.([...value, created.id])
    setNewName('')
    setNewColor(TAG_PRESET_COLORS[0])
  }

  return (
    <Select
      mode="multiple"
      value={value}
      onChange={onChange}
      placeholder="选择或新建标签"
      allowClear
      options={tags.map((tag) => ({ value: tag.id, label: tag.name }))}
      tagRender={(props) => {
        const tag = tags.find((item) => item.id === props.value)
        return (
          <AntTag color={tag?.color} closable onClose={props.onClose} style={{ marginInlineEnd: 4 }}>
            {props.label}
          </AntTag>
        )
      }}
      dropdownRender={(menu) => (
        <>
          {menu}
          <Divider style={{ margin: '8px 0' }} />
          <Flex gap={8} align="center" style={{ padding: '0 8px 8px' }}>
            <Input
              value={newName}
              placeholder="新标签名"
              style={{ flex: 1 }}
              maxLength={30}
              onChange={(event) => setNewName(event.target.value)}
              onPressEnter={handleCreate}
            />
            <ColorPicker
              value={newColor}
              presets={[{ label: '推荐', colors: TAG_PRESET_COLORS }]}
              onChange={(color) => setNewColor(color.toHexString())}
            />
            <Button
              type="text"
              icon={<PlusOutlined />}
              loading={createTag.isPending}
              onClick={handleCreate}
            >
              添加
            </Button>
          </Flex>
          <Text type="secondary" style={{ padding: '0 8px 8px', display: 'block', fontSize: 12 }}>
            输入名称后回车即可创建
          </Text>
        </>
      )}
    />
  )
}
