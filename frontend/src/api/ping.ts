import { http } from './request'

export interface PingResult {
  service: string
  status: string
  timestamp: string
}

/** 服务连通性探测，用于验证前后端链路 */
export const pingApi = () => http.get<PingResult>('/ping')
