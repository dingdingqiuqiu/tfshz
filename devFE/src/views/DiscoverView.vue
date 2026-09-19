<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Flame, SlidersHorizontal } from 'lucide-vue-next'
import BlogCard from '../components/BlogCard.vue'
import { api } from '../services/api'
import { demoBlogs } from '../data/demo'
import type { Blog } from '../types/domain'
const blogs = ref<Blog[]>(demoBlogs)
const active = ref('推荐')
const tabs = ['推荐', '美食', '周末玩乐', '城市漫步']
async function load() { try { blogs.value = (await api.hotBlogs()) || demoBlogs } catch { /* fallback */ } }
async function like(blog: Blog) { try { await api.likeBlog(blog.id) } catch { blog.isLike = !blog.isLike; blog.liked = (blog.liked || 0) + (blog.isLike ? 1 : -1) } }
onMounted(load)
</script>
<template>
  <section class="discover-head"><div><span class="eyebrow">CITY NOTES</span><h1>发现城市的<br /><em>另一面</em></h1><p>真实、有趣、值得收藏的本地生活灵感。</p></div><div class="discover-mark"><Flame :size="32" /><b>今日热度<br /><strong>98.6</strong></b></div></section>
  <div class="filter-bar"><div class="tabs"><button v-for="tab in tabs" :key="tab" type="button" :class="{ active: active === tab }" @click="active = tab">{{ tab }}</button></div><button class="filter-button" type="button"><SlidersHorizontal :size="16" />筛选</button></div>
  <section class="discover-grid"><BlogCard v-for="blog in blogs" :key="blog.id" :blog="blog" @like="like" /></section>
</template>
