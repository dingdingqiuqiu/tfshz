<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Compass, Heart, Home, Search, UserRound, MapPin, LogIn } from 'lucide-vue-next'
import { useAppStore } from '../stores/app'

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const nav = [
  { to: '/home', label: '首页', icon: Home },
  { to: '/discover', label: '发现', icon: Compass },
  { to: '/shops', label: '找店', icon: Search },
  { to: '/profile', label: '我的', icon: UserRound },
]
const title = computed(() => String(route.meta.title || '首页'))
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="topbar-inner">
        <RouterLink to="/home" class="brand" aria-label="返回首页">
          <span class="brand-mark">听</span><span class="brand-name">听风生活志</span>
        </RouterLink>
        <nav class="desktop-nav" aria-label="主导航">
          <RouterLink v-for="item in nav" :key="item.to" :to="item.to" :class="{ active: route.path.startsWith(item.to) }">{{ item.label }}</RouterLink>
        </nav>
        <div class="topbar-actions">
          <button class="city-switch" type="button"><MapPin :size="16" />{{ app.city }}<span class="chevron">⌄</span></button>
          <RouterLink v-if="!app.loggedIn" to="/login" class="login-link"><LogIn :size="16" />登录</RouterLink>
          <RouterLink v-else to="/profile" class="avatar-link"><img :src="app.user?.icon || '/assets/avatar.png'" alt="用户头像" /></RouterLink>
        </div>
      </div>
    </header>
    <main class="page-main">
      <div class="route-label">{{ title }}</div>
      <slot />
    </main>
    <footer class="mobile-nav">
      <RouterLink v-for="item in nav" :key="item.to" :to="item.to" :class="{ active: route.path.startsWith(item.to) }">
        <component :is="item.icon" :size="20" /><span>{{ item.label }}</span>
      </RouterLink>
    </footer>
  </div>
</template>
