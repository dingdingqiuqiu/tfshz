<script setup lang="ts">
import { Heart, MessageCircle } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import type { Blog } from '../types/domain'
import { FALLBACK_AVATAR, firstImage, onImageError } from '../utils/images'

const router = useRouter()
const props = defineProps<{ blog: Blog }>()
const emit = defineEmits<{ like: [Blog] }>()

function goToBlogDetail() {
  router.push(`/blogs/${props.blog.id}`)
}
</script>

<template>
  <article class="blog-card" @click="goToBlogDetail">
    <div class="blog-image-wrap"><img :src="firstImage(blog.images)" :alt="blog.title" @error="onImageError" /><span class="image-badge">探店</span></div>
    <div class="blog-content"><h3>{{ blog.title }}</h3><p>{{ blog.content }}</p></div>
    <div class="blog-meta"><div class="author"><img :src="firstImage(blog.icon, FALLBACK_AVATAR)" alt="" @error="(e) => onImageError(e, FALLBACK_AVATAR)" /><span>{{ blog.name || '匿名用户' }}</span></div><div class="engagement"><button type="button" :class="{ liked: blog.isLike }" @click.stop="emit('like', blog)"><Heart :size="15" :fill="blog.isLike ? 'currentColor' : 'none'" />{{ blog.liked || 0 }}</button><span><MessageCircle :size="15" />{{ blog.comments || 0 }}</span></div></div>
  </article>
</template>

<style scoped>
.blog-card {
  cursor: pointer;
}
</style>
