<script setup lang="ts">
import { Heart, MessageCircle } from 'lucide-vue-next'
import type { Blog } from '../types/domain'
defineProps<{ blog: Blog }>()
const emit = defineEmits<{ like: [Blog] }>()
</script>

<template>
  <article class="blog-card">
    <div class="blog-image-wrap"><img :src="blog.images?.split(',')[0] || '/assets/blog1.jpg'" :alt="blog.title" /><span class="image-badge">探店</span></div>
    <div class="blog-content"><h3>{{ blog.title }}</h3><p>{{ blog.content }}</p></div>
    <div class="blog-meta"><div class="author"><img :src="blog.icon || '/assets/avatar.png'" alt="" /><span>{{ blog.name || '匿名用户' }}</span></div><div class="engagement"><button type="button" :class="{ liked: blog.isLike }" @click="emit('like', blog)"><Heart :size="15" :fill="blog.isLike ? 'currentColor' : 'none'" />{{ blog.liked || 0 }}</button><span><MessageCircle :size="15" />{{ blog.comments || 0 }}</span></div></div>
  </article>
</template>
