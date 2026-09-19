import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import type { Result } from '../types/domain'

export const http = axios.create({ baseURL: '/api', timeout: 6000 })

http.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('token')
  if (token) config.headers.authorization = token
  return config
})

http.interceptors.response.use((response) => {
  const body = response.data
  if (body && body.success === false) return Promise.reject(new Error(body.errorMsg || '请求失败'))
  return body
}, (error) => Promise.reject(error))

// 拦截器把后端 Result 提前解包，统一以泛型返回业务 data。
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<Result<T>>(config)
  const body = response as unknown as Result<T>
  return body.data
}
