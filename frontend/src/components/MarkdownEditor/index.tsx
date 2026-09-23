import { useCallback, useState } from 'react'
import { UploadOutlined } from '@ant-design/icons'
import { Button, Flex, Typography, Upload, message } from 'antd'
import MDEditor from '@uiw/react-md-editor'
import { fileApi } from '@/api/file'

const { Text } = Typography

interface Props {
  value?: string
  onChange?: (value?: string) => void
}

/**
 * Markdown 编辑器，支持三种插图方式：工具栏按钮、拖拽到编辑区、直接粘贴截图。
 * 作为受控组件可直接放进 antd 的 Form.Item。
 */
export default function MarkdownEditor({ value = '', onChange }: Props) {
  const [uploading, setUploading] = useState(false)

  const uploadAndInsert = useCallback(
    async (file: File) => {
      setUploading(true)
      try {
        const result = await fileApi.upload(file)
        const merged = `${value}${value.endsWith('\n') || value === '' ? '' : '\n'}\n![${result.filename}](${result.url})\n`
        onChange?.(merged)
        message.success('图片已插入')
      } catch {
        // 失败提示由 axios 响应拦截器统一处理
      } finally {
        setUploading(false)
      }
    },
    [value, onChange],
  )

  const handlePaste = useCallback(
    (event: React.ClipboardEvent<HTMLTextAreaElement>) => {
      const items = event.clipboardData?.items
      if (!items) {
        return
      }
      for (const item of items) {
        if (item.type.startsWith('image/')) {
          const file = item.getAsFile()
          if (file) {
            event.preventDefault()
            void uploadAndInsert(file)
          }
          return
        }
      }
    },
    [uploadAndInsert],
  )

  const handleDrop = useCallback(
    (event: React.DragEvent<HTMLDivElement>) => {
      event.preventDefault()
      const file = event.dataTransfer?.files?.[0]
      if (file && file.type.startsWith('image/')) {
        void uploadAndInsert(file)
      }
    },
    [uploadAndInsert],
  )

  return (
    <div
      data-color-mode="light"
      onDrop={handleDrop}
      onDragOver={(event) => event.preventDefault()}
    >
      <MDEditor
        value={value}
        onChange={(next) => onChange?.(next)}
        height={420}
        preview="live"
        textareaProps={{ onPaste: handlePaste }}
      />

      <Flex align="center" gap={12} style={{ marginTop: 8 }} wrap>
        <Upload
          accept="image/jpeg,image/png,image/gif,image/webp"
          showUploadList={false}
          beforeUpload={(file) => {
            void uploadAndInsert(file)
            // 返回 false 阻止 antd 自身的上传行为，改由业务接口处理
            return false
          }}
        >
          <Button icon={<UploadOutlined />} loading={uploading}>
            上传图片
          </Button>
        </Upload>
        <Text type="secondary" style={{ fontSize: 12 }}>
          支持拖拽图片到编辑区，或直接粘贴截图；格式 jpg / png / gif / webp，单张不超过 5MB
        </Text>
      </Flex>
    </div>
  )
}
