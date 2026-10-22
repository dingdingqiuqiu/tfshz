import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../services/api'
import { ACCESS_KEY, clearTokens, saveTokens } from '../services/session'
import type { User } from '../types/domain'

export const useAppStore = defineStore('app', () => {
  const city = ref('杭州')
  const user = ref<User | null>(null)

  const token = ref(sessionStorage.getItem(ACCESS_KEY))
  const loggedIn = computed(() => Boolean(user.value || token.value))

  async function loadUser() {
    if (!token.value) return null
    try {
      const currentUser = await api.me()
      if (currentUser) user.value = currentUser
      return currentUser || user.value
    } catch {
      return user.value
    }
  }

  function setTokens(access: string, refresh: string) {
    token.value = access
    saveTokens(access, refresh)
  }

  /** 只清本地登录态，不发请求：会话失效（refresh 也过期）时由 http 层通知调用 */
  function clearSession() {
    user.value = null
    token.value = null
    clearTokens()
  }

  /**
   * 登出：先通知后端让 refreshToken 失效，再无条件清本地。
   * 后端失败也不影响本地登出。
   */
  async function logout() {
    try {
      await api.logout()
    } catch (e) {
      // 网络问题等，仅记录，不阻断本地清理
      console.warn('[logout] 后端通知失败，仍执行本地清理', e)
    } finally {
      clearSession()
    }
  }

  return { city, user, loggedIn, loadUser, setTokens, clearSession, logout }
})