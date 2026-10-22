<script setup lang="ts">
import { useRouter } from 'vue-router'
import { onMounted, ref } from 'vue'
import { api } from '../services/api'
import type { User } from '../types/domain'
import { FALLBACK_AVATAR, onImageError } from '../utils/images'
import { ArrowLeft } from 'lucide-vue-next'

const router = useRouter()
const user = ref<User | null>(null)
const loading = ref(true)
const error = ref('')

async function load() {
  try {
    const id = Number(router.currentRoute.value.params.id)
    user.value = await api.user(id)
  } catch {
    error.value = '用户不存在'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="user-detail">
    <div class="user-header">
      <button type="button" class="back-btn" @click="router.back()">
        <ArrowLeft :size="24" />
      </button>
      <h1>个人资料</h1>
      <div style="width: 40px"></div>
    </div>

    <div v-if="loading" class="state">加载中...</div>
    <div v-else-if="error" class="state">{{ error }}</div>
    <div v-else-if="user" class="user-content">
      <div class="user-profile">
        <img :src="user.icon || FALLBACK_AVATAR" :alt="user.nickName" class="avatar" @error="(e) => onImageError(e, FALLBACK_AVATAR)" />
        <div>
          <h2>{{ user.nickName }}</h2>
          <p class="user-id">ID: {{ user.id }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.user-detail {
  min-height: 100vh;
  background: #fff;
}

.user-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  background: none;
  border: none;
  cursor: pointer;
  color: #333;
}

.user-header h1 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  color: #999;
  font-size: 14px;
}

.user-content {
  padding: 20px;
}

.user-profile {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 20px;
  background: #f9f9f9;
  border-radius: 8px;
}

.avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.user-profile h2 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 8px;
  color: #333;
}

.user-id {
  font-size: 12px;
  color: #999;
  margin: 0;
}
</style>
