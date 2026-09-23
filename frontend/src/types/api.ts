/** 后端分页响应结构（对应 PageResult） */
export interface PageResult<T> {
  total: number
  pages: number
  current: number
  size: number
  records: T[]
}
