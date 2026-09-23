import { http } from './request'

/** 热力图单日数据，后端仅返回有记录的日期 */
export interface DayCount {
  diaryDate: string
  total: number
}

/** 心情分布项，mood = 0 表示未记录心情 */
export interface MoodCount {
  mood: number
  total: number
}

export interface StatsOverview {
  totalCount: number
  monthCount: number
  currentStreak: number
  longestStreak: number
  firstDate?: string
  lastDate?: string
  moodDistribution: MoodCount[]
}

export const statsApi = {
  calendar: (year: number) => http.get<DayCount[]>('/stats/calendar', { year }),
  overview: () => http.get<StatsOverview>('/stats/overview'),
}
