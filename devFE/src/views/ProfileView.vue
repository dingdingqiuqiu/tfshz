<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ChevronRight,
  Clock3,
  LogOut,
  ShieldCheck,
  Ticket,
  UserRound,
} from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { useAppStore } from '../stores/app'

const app = useAppStore()
const router = useRouter()
const activeTab = ref('笔记')

const displayUser = computed(
  () => app.user || { nickName: '游客', icon: '/assets/avatar.png' }
)

onMounted(() => app.loadUser())

function enter() {
  if (!app.loggedIn) router.push('/login')
}

async function handleLogout() {
  await app.logout()
  router.push('/home')
}
</script>

<template>
  <section class="profile-banner">
    <div class="profile-user">
      <img :src="displayUser.icon || '/assets/avatar.png'" alt="头像" />
      <div>
        <span class="eyebrow light">WELCOME BACK</span>
        <h1>{{ displayUser.nickName }}</h1>
        <p>
          {{ app.loggedIn
            ? '今天也去发现一点新鲜事吧'
            : '登录后同步你的收藏与足迹' }}
        </p>
      </div>
    </div>

    <!-- 右上角固定位置：未登录显示「立即登录」，已登录显示「设置 + 退出登录」 -->
    <button
      v-if="!app.loggedIn"
      type="button"
      class="light-button"
      @click="enter"
    >
      立即登录 <ChevronRight :size="16" />
    </button>

    <div v-else style="display:flex; align-items:center; gap:8px">
      <button type="button" class="light-button" @click="handleLogout">
        退出登录 <LogOut :size="16" />
      </button>
    </div>
  </section>

  <section class="profile-stats">
    <div><b>0</b><span>关注</span></div>
    <div><b>0</b><span>粉丝</span></div>
    <div><b>0</b><span>获赞</span></div>
    <div><b>0</b><span>收藏</span></div>
  </section>

  <section class="profile-content">
    <div class="profile-tabs">
      <button
        v-for="tab in ['笔记', '收藏', '足迹']"
        :key="tab"
        type="button"
        :class="{ active: activeTab === tab }"
        @click="activeTab = tab"
      >
        {{ tab }}
      </button>
    </div>
    <div class="profile-empty">
      <div class="empty-icon"><UserRound :size="24" /></div>
      <h3>{{ activeTab === '笔记' ? '还没有发布笔记' : `暂无${activeTab}` }}</h3>
      <p>去发现页记录你的城市生活吧</p>
      <button type="button" @click="router.push('/discover')">去发现</button>
    </div>
  </section>

  <section class="quick-links">
    <button type="button" @click="enter">
      <Ticket :size="19" /><span>我的优惠券</span><ChevronRight :size="17" />
    </button>
    <button type="button" @click="enter">
      <Clock3 :size="19" /><span>浏览记录</span><ChevronRight :size="17" />
    </button>
    <button type="button" @click="enter">
      <ShieldCheck :size="19" /><span>账号与安全</span><ChevronRight :size="17" />
    </button>
  </section>
</template>