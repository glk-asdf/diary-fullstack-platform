export interface TagVO {
  id: number
  name: string
  color?: string
}

/** 日记列表项（不含正文） */
export interface DiaryVO {
  id: number
  title: string
  summary?: string
  mood?: number
  weather?: string
  /** ISO 日期，如 2026-09-20 */
  diaryDate: string
  isPublic?: number
  createdAt?: string
  updatedAt?: string
  tags: TagVO[]
}

/** 日记详情，比列表项多出 Markdown 正文 */
export interface DiaryDetailVO extends DiaryVO {
  content?: string
}

export interface DiarySavePayload {
  title: string
  content?: string
  mood?: number
  weather?: string
  diaryDate: string
  tagIds?: number[]
  isPublic?: number
}

export interface DiaryQuery {
  page?: number
  size?: number
  keyword?: string
  tagId?: number
  mood?: number
  startDate?: string
  endDate?: string
}

/** 心情枚举，与后端约定一致 */
export const MOOD_OPTIONS: { value: number; label: string; emoji: string; color: string }[] = [
  { value: 1, label: '开心', emoji: '😄', color: '#faad14' },
  { value: 2, label: '平静', emoji: '🙂', color: '#52c41a' },
  { value: 3, label: '难过', emoji: '😔', color: '#1677ff' },
  { value: 4, label: '焦虑', emoji: '😰', color: '#722ed1' },
  { value: 5, label: '生气', emoji: '😠', color: '#f5222d' },
]

export const moodOf = (value?: number) => MOOD_OPTIONS.find((item) => item.value === value)
