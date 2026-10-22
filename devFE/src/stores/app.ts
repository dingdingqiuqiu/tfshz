import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../services/api'
import type { User } from '../types/domain'

export const useAppStore = defineStore('app', () => {
  const city = ref('杭州')
  const user = ref<User | null>(null)

  const token = ref(sessionStorage.getItem('access_token'))
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
    sessionStorage.setItem('access_token', access)
    sessionStorage.setItem('refresh_token', refresh)
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
      user.value = null
      token.value = null
      sessionStorage.removeItem('access_token')
      sessionStorage.removeItem('refresh_token')
    }
  }

  return { city, user, loggedIn, loadUser, setTokens, logout }
})