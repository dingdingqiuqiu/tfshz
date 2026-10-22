/**
 * 登录态凭证的本地存取，以及「会话彻底失效」的统一出口。
 *
 * 这里只负责清凭证 + 发通知，具体跳哪个页面由应用入口（main.ts）注入，
 * 因为 services 层不能反向依赖 router，否则会和 views → stores → services 形成循环导入。
 */

export const ACCESS_KEY = 'access_token'
export const REFRESH_KEY = 'refresh_token'

export function saveTokens(access: string, refresh: string) {
  sessionStorage.setItem(ACCESS_KEY, access)
  sessionStorage.setItem(REFRESH_KEY, refresh)
}

export function clearTokens() {
  sessionStorage.removeItem(ACCESS_KEY)
  sessionStorage.removeItem(REFRESH_KEY)
}

type SessionExpiredHandler = () => void

let handler: SessionExpiredHandler | null = null

/** 由应用入口注入一次；注入后所有会话失效都会走 SPA 路由跳转 */
export function onSessionExpired(fn: SessionExpiredHandler) {
  handler = fn
}

/**
 * 登录态彻底失效（refresh token 也失效）时调用：清凭证后交给注入的处理器。
 * 兜底直接跳 history 模式的 SPA 路由 /login，绝不能跳 /login.html（该静态页已不存在）。
 */
export function notifySessionExpired() {
  clearTokens()
  if (handler) {
    handler()
    return
  }
  window.location.assign('/login')
}
