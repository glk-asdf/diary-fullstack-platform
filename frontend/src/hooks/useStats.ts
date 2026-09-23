import { useQuery } from '@tanstack/react-query'
import { statsApi } from '@/api/stats'

export const statsKeys = {
  calendar: (year: number) => ['stats', 'calendar', year] as const,
  overview: () => ['stats', 'overview'] as const,
}

/** 热力图数据，直接转成「日期 → 篇数」的 Map 供格子查表 */
export const useCalendar = (year: number) =>
  useQuery({
    queryKey: statsKeys.calendar(year),
    queryFn: () => statsApi.calendar(year),
    select: (data) => {
      const map = new Map<string, number>()
      data.forEach((item) => map.set(item.diaryDate, item.total))
      return map
    },
  })

export const useOverview = () =>
  useQuery({
    queryKey: statsKeys.overview(),
    queryFn: statsApi.overview,
  })
