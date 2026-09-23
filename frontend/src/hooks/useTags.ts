import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { message } from 'antd'
import { tagApi, type TagSavePayload } from '@/api/tag'

export const tagKeys = {
  all: ['tags'] as const,
}

export const useTags = () =>
  useQuery({
    queryKey: tagKeys.all,
    queryFn: tagApi.list,
    // 标签变动频率低，缓存久一点
    staleTime: 5 * 60_000,
  })

export const useCreateTag = () => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: TagSavePayload) => tagApi.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: tagKeys.all })
      message.success('标签已创建')
    },
  })
}

export const useUpdateTag = () => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: TagSavePayload }) =>
      tagApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: tagKeys.all })
      // 日记卡片上展示的标签名与颜色也需要同步
      queryClient.invalidateQueries({ queryKey: ['diaries'] })
      message.success('标签已更新')
    },
  })
}

export const useDeleteTag = () => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: number) => tagApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: tagKeys.all })
      queryClient.invalidateQueries({ queryKey: ['diaries'] })
      message.success('标签已删除')
    },
  })
}
