<script setup lang="ts">
import { onMounted, ref } from 'vue'
import BlogCard from '../components/BlogCard.vue'
import { api } from '../services/api'
import type { Blog } from '../types/domain'
const blogs = ref<Blog[]>([])
const error = ref('')
const active = ref('推荐')
const tabs = ['推荐', '美食', '周末玩乐', '城市漫步']
async function load() { try { blogs.value = (await api.hotBlogs()) || [] } catch { error.value = '内容加载失败，请稍后重试' } }
async function like(blog: Blog) { try { await api.likeBlog(blog.id) } catch { blog.isLike = !blog.isLike; blog.liked = (blog.liked || 0) + (blog.isLike ? 1 : -1) } }
onMounted(load)
</script>
<template>
  <section class="discover-head"><div><span class="eyebrow">CITY NOTES</span><h1>发现城市的<br /><em>另一面</em></h1><p>真实、有趣、值得收藏的本地生活灵感。</p></div><div class="discover-mark"><svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 22c4.4 0 8-3.6 8-8 0-3.3-2.1-5.7-5-9-.2 2.4-1.1 3.8-2.3 4.8C12.5 6.4 10.5 3.8 8 2c.3 4-1 6-3 8.2A8 8 0 0 0 12 22Z"/><path d="M12 22a4 4 0 0 0 4-4c0-1.7-1.1-2.8-2.5-4.5-.2 1.4-.8 2.1-1.5 2.5-.1-1.6-1-2.8-2.5-3.8.2 2-.5 3-1.5 4.1A4 4 0 0 0 12 22Z"/></svg><b>今日热度<br /><strong>98.6</strong></b></div></section>
  <div class="filter-bar"><div class="tabs"><button v-for="tab in tabs" :key="tab" type="button" :class="{ active: active === tab }" @click="active = tab">{{ tab }}</button></div><button class="filter-button" type="button"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 6h16M7 12h10m-7 6h4"/><circle cx="8" cy="6" r="2" fill="currentColor"/><circle cx="15" cy="12" r="2" fill="currentColor"/><circle cx="11" cy="18" r="2" fill="currentColor"/></svg>筛选</button></div>
  <div v-if="error" class="empty-state">{{ error }}</div><div v-else-if="!blogs.length" class="empty-state">暂无分享内容</div><section v-else class="discover-grid"><BlogCard v-for="blog in blogs" :key="blog.id" :blog="blog" @like="like" /></section>
</template>
