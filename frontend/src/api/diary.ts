import { http } from './request'
import type { PageResult } from '@/types/api'
import type { DiaryDetailVO, DiaryQuery, DiarySavePayload, DiaryVO } from '@/types/diary'

export const diaryApi = {
  page: (params: DiaryQuery) => http.get<PageResult<DiaryVO>>('/diaries', params),
  detail: (id: number) => http.get<DiaryDetailVO>(`/diaries/${id}`),
  create: (payload: DiarySavePayload) => http.post<DiaryDetailVO>('/diaries', payload),
  update: (id: number, payload: DiarySavePayload) => http.put<DiaryDetailVO>(`/diaries/${id}`, payload),
  remove: (id: number) => http.delete<void>(`/diaries/${id}`),
}
