import { Flex, Tooltip } from 'antd'
import { MOOD_OPTIONS } from '@/types/diary'

interface Props {
  value?: number
  onChange?: (value?: number) => void
}

/**
 * 心情选择器，作为受控组件可直接放进 antd 的 Form.Item。
 * 再次点击已选中项可取消选择。
 */
export default function MoodPicker({ value, onChange }: Props) {
  return (
    <Flex gap={8} wrap>
      {MOOD_OPTIONS.map((option) => {
        const active = value === option.value
        return (
          <Tooltip key={option.value} title={option.label}>
            <Flex
              align="center"
              justify="center"
              onClick={() => onChange?.(active ? undefined : option.value)}
              style={{
                width: 44,
                height: 44,
                fontSize: 22,
                borderRadius: 8,
                cursor: 'pointer',
                transition: 'all 0.2s',
                border: `2px solid ${active ? option.color : '#f0f0f0'}`,
                background: active ? `${option.color}14` : '#fafafa',
              }}
            >
              {option.emoji}
            </Flex>
          </Tooltip>
        )
      })}
    </Flex>
  )
}
