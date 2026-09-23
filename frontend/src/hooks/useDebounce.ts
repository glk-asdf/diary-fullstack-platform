import { useEffect, useState } from 'react'

/** 防抖：值停止变化 delay 毫秒后才更新返回值，用于搜索框等高频输入 */
export function useDebounce<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay)
    return () => window.clearTimeout(timer)
  }, [value, delay])

  return debounced
}
