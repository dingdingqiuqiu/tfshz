import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../services/api'
import { demoUser } from '../data/demo'
import type { User } from '../types/domain'

export const useAppStore = defineStore('app', () => {
  const city = ref('杭州')
  const user = ref<User | null>(null)
  const token = ref(sessionStorage.getItem('token'))
  const isDemo = ref(false)
  // 登录接口返回的是 token，用户详情请求可能稍后才完成；两者都属于登录态。
  const loggedIn = computed(() => Boolean(user.value || token.value))

  async function loadUser() {
    if (!token.value) return null
    try {
      const currentUser = await api.me()
      // 后端未返回用户时，不覆盖已经存在的前端状态。
      if (currentUser) user.value = currentUser
      return currentUser || user.value
    } catch {
      return user.value
    }
  }
  function setToken(value: string) {
    token.value = value
    sessionStorage.setItem('token', value)
  }
  function useDemoUser() { user.value = demoUser; isDemo.value = true }
  function logout() {
    user.value = null
    token.value = null
    sessionStorage.removeItem('token')
  }
  return { city, user, isDemo, loggedIn, loadUser, setToken, useDemoUser, logout }
})
