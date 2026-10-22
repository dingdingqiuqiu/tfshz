import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import type { Result } from '../types/domain'
import { ACCESS_KEY, REFRESH_KEY, notifySessionExpired, saveTokens } from './session'

/** 业务请求实例：带 token 注入与 401 静默刷新逻辑 */
export const http = axios.create({ baseURL: '/api', timeout: 6000 })

/** 裸实例：刷新请求专用，不带任何拦截器，避免“刷新请求 401 又触发刷新”的循环 */
const bare = axios.create({ baseURL: '/api', timeout: 6000 })

// ---------- 请求拦截：统一注入 access token ----------
http.interceptors.request.use((config) => {
  const access = sessionStorage.getItem(ACCESS_KEY)
  if (access) config.headers.authorization = `Bearer ${access}`
  return config
})

// ---------- 静默刷新（single-flight 单飞模式） ----------
let refreshPromise: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  const refreshToken = sessionStorage.getItem(REFRESH_KEY)
  if (!refreshToken) throw new Error('无 refresh token')
  const res = await bare.post('/user/refresh', { refreshToken })
  const body = res.data as Result<{ accessToken: string; refreshToken: string }>
  if (!body.success || !body.data) throw new Error(body.errorMsg || '刷新失败')
  // 轮换：后端签发的新 refresh 也要同步落库，旧 refresh 已经作废，留着就是定时炸弹
  saveTokens(body.data.accessToken, body.data.refreshToken)
  return body.data.accessToken
}

/** 并发 401 只发起一次刷新：所有等待的请求共享同一个 promise */
function refreshOnce(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = refreshAccessToken().finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

// ---------- 响应拦截：401 → 静默刷新 → 重放原请求 ----------
http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.success === false) return Promise.reject(new Error(body.errorMsg || '请求失败'))
    return body
  },
  async (error) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined
    // 401 且未重试过：尝试刷新后重放；重试过还 401 说明刷新也失败了，直接登出
    if (original && error.response?.status === 401 && !original._retried) {
      original._retried = true
      try {
        const access = await refreshOnce()
        const retried = {
          ...original,
          headers: { ...(original.headers ?? {}), authorization: `Bearer ${access}` },
        }
        return http.request(retried)
      } catch {
        // 刷新失败说明登录态已彻底失效：清凭证后走 SPA 路由跳 /login（由 main.ts 注入），
        // 这里不能再硬编码 /login.html —— 这是已不存在的旧版静态页地址。
        notifySessionExpired()
      }
    }
    return Promise.reject(error)
  }
)

// 拦截器把后端 Result 提前解包，统一以泛型返回业务 data。
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<Result<T>>(config)
  const body = response as unknown as Result<T>
  return body.data
}