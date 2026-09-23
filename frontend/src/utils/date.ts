import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import relativeTime from 'dayjs/plugin/relativeTime'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

/** 2026-09-20 */
export const formatDate = (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD') : '')

/** 2026-09-20 14:30 */
export const formatDateTime = (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '')

/** 3 小时前 / 2 天前 */
export const fromNow = (value?: string) => (value ? dayjs(value).fromNow() : '')

/** 今天 / 昨天 / 05月01日 / 2025年05月01日，用于时间轴分组与列表展示 */
export const friendlyDate = (value?: string) => {
  if (!value) {
    return ''
  }
  const target = dayjs(value)
  const today = dayjs()

  if (target.isSame(today, 'day')) {
    return '今天'
  }
  if (target.isSame(today.subtract(1, 'day'), 'day')) {
    return '昨天'
  }
  return target.isSame(today, 'year')
    ? target.format('MM月DD日')
    : target.format('YYYY年MM月DD日')
}

export default dayjs
