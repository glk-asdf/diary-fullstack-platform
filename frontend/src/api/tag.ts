import { http } from './request'
import type { TagVO } from '@/types/diary'

export interface TagSavePayload {
  name: string
  color?: string
}

export const tagApi = {
  list: () => http.get<TagVO[]>('/tags'),
  create: (payload: TagSavePayload) => http.post<TagVO>('/tags', payload),
  update: (id: number, payload: TagSavePayload) => http.put<TagVO>(`/tags/${id}`, payload),
  remove: (id: number) => http.delete<void>(`/tags/${id}`),
}
