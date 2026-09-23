import { http } from './request'

export interface FileVO {
  id: number
  url: string
  filename: string
  size: number
  mimeType: string
}

export const fileApi = {
  /** 上传图片。FormData 由 axios 自动设置 multipart 边界，不要手动指定 Content-Type */
  upload: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<FileVO>('/files/upload', formData)
  },
}
