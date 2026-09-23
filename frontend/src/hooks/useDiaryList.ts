import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { message } from 'antd'
import { diaryApi } from '@/api/diary'
import type { DiaryQuery, DiarySavePayload } from '@/types/diary'

export const diaryKeys = {
  all: ['diaries'] as const,
  list: (params: DiaryQuery) => [...diaryKeys.all, 'list', params] as const,
  detail: (id: number) => [...diaryKeys.all, 'detail', id] as const,
}

/** 列表查询，翻页时保留上一页数据避免闪烁 */
export const useDiaryList = (params: DiaryQuery) =>
  useQuery({
    queryKey: diaryKeys.list(params),
    queryFn: () => diaryApi.page(params),
    placeholderData: keepPreviousData,
  })

export const useDiaryDetail = (id?: number) =>
  useQuery({
    queryKey: diaryKeys.detail(id ?? 0),
    queryFn: () => diaryApi.detail(id as number),
    enabled: typeof id === 'number' && id > 0,
  })

export const useCreateDiary = () => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: DiarySavePayload) => diaryApi.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: diaryKeys.all })
      message.success('保存成功')
    },
  })
}

export const useUpdateDiary = () => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: DiarySavePayload }) =>
      diaryApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: diaryKeys.all })
      message.success('保存成功')
    },
  })
}

export const useDeleteDiary = () => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: number) => diaryApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: diaryKeys.all })
      message.success('已删除')
    },
  })
}
