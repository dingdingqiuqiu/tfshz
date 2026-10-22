import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import { routes } from './router'
import { useAppStore } from './stores/app'
import { onSessionExpired } from './services/session'
import './styles/index.css'

const router = createRouter({ history: createWebHistory(), routes })
const pinia = createPinia()
const app = createApp(App)

app.use(pinia).use(router)

/**
 * 会话失效的统一跳转：走 vue-router 的 /login 路由。
 * 之前这里（http 层）硬跳 /login.html，那是旧版静态站地址，history 模式下
 * 会得到一个匹配不到任何路由的 URL（页面空白、且浏览器地址栏是错的）。
 */
onSessionExpired(() => {
  useAppStore(pinia).clearSession()
  const current = router.currentRoute.value
  if (current.path === '/login') return
  void router.replace({
    path: '/login',
    query: current.fullPath === '/' ? {} : { redirect: current.fullPath },
  })
})

app.mount('#app')
