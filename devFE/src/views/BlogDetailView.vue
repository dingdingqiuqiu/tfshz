<script setup lang="ts">
import { useRouter } from 'vue-router'
import { onMounted, ref, computed } from 'vue'
import { api } from '../services/api'
import type { Blog, BlogComment } from '../types/domain'
import { FALLBACK_AVATAR, onImageError, splitImages } from '../utils/images'
import { useAppStore } from '../stores/app'
import { Heart, MessageCircle, ArrowLeft, ChevronLeft, ChevronRight } from 'lucide-vue-next'

const router = useRouter()
const app = useAppStore()
const blog = ref<Blog | null>(null)
const comments = ref<BlogComment[]>([])
const loading = ref(true)
/** 只用于整页加载失败，不能复用给“评论成功/失败”，否则会把整篇文章替换掉 */
const loadError = ref('')
const commentError = ref('')
const commentSuccess = ref('')
const currentImageIndex = ref(0)
const commentInput = ref('')
const isSubmitting = ref(false)

const blogImages = computed(() => splitImages(blog.value?.images))

async function load() {
  try {
    const id = Number(router.currentRoute.value.params.id)
    blog.value = await api.blog(id)
    comments.value = (await api.blogComments(id)) || []
    currentImageIndex.value = 0
  } catch {
    loadError.value = '内容加载失败'
  } finally {
    loading.value = false
  }
}

async function like() {
  if (!blog.value) return
  try {
    await api.likeBlog(blog.value.id)
    blog.value.isLike = !blog.value.isLike
    blog.value.liked = (blog.value.liked || 0) + (blog.value.isLike ? 1 : -1)
  } catch (cause) {
    // 点赞失败不影响文章展示，只在控制台留痕，避免把整页替换成错误文案
    console.warn('[blog] 点赞失败', cause)
  }
}

function goToUser() {
  if (blog.value?.userId) {
    router.push(`/users/${blog.value.userId}`)
  }
}

function prevImage() {
  if (currentImageIndex.value > 0) {
    currentImageIndex.value--
  }
}

function nextImage() {
  if (currentImageIndex.value < blogImages.value.length - 1) {
    currentImageIndex.value++
  }
}

function goToImage(index: number) {
  currentImageIndex.value = index
}

async function submitComment() {
  const content = commentInput.value.trim()
  if (!content) {
    commentError.value = '请输入评论内容'
    return
  }
  if (!blog.value) return

  // 未登录就不要发这个注定 401 的请求，直接带去登录页（登录后回到当前文章）
  if (!app.loggedIn) {
    commentError.value = '请先登录后再评论'
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }

  isSubmitting.value = true
  commentError.value = ''
  commentSuccess.value = ''
  try {
    await api.addComment({ blogId: blog.value.id, content })
    commentInput.value = ''
    commentSuccess.value = '评论成功'
    comments.value = (await api.blogComments(blog.value.id)) || []
    blog.value.comments = comments.value.length
    setTimeout(() => { commentSuccess.value = '' }, 2000)
  } catch (cause) {
    commentError.value = commentFailMessage(cause)
  } finally {
    isSubmitting.value = false
  }
}

/** 后端会把 SQL 异常原文塞进 errorMsg，对用户没意义，这里做一次收敛 */
function commentFailMessage(cause: unknown) {
  const raw = cause instanceof Error ? cause.message.trim() : ''
  if (!raw || raw.length > 60 || /SQL|Field|Error updating|\[object/i.test(raw)) {
    return '评论失败，请稍后重试'
  }
  return `评论失败：${raw}`
}

/** 评论点赞：后端用 Redis 做了“一人一赞”去重，同一用户再点一次就是取消 */
async function likeComment(comment: BlogComment) {
  if (!app.loggedIn) {
    commentError.value = '请先登录后再点赞'
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  try {
    const data = await api.likeComment(comment.id)
    if (data && typeof data.liked === 'number') {
      comment.liked = data.liked
      comment.isLiked = Boolean(data.isLiked)
    } else {
      // 后端没回数据时按本地状态翻转，至少按钮不会没反应
      comment.isLiked = !comment.isLiked
      comment.liked = Math.max(0, (comment.liked || 0) + (comment.isLiked ? 1 : -1))
    }
    commentError.value = ''
  } catch {
    commentError.value = '点赞失败，请稍后重试'
  }
}

onMounted(load)
</script>

<template>
  <div class="blog-detail">
    <div class="blog-detail-header">
      <button type="button" class="back-btn" @click="router.back()">
        <ArrowLeft :size="24" />
      </button>
      <h1>{{ blog?.title }}</h1>
    </div>

    <div v-if="loading" class="state">加载中...</div>
    <div v-else-if="loadError" class="state">{{ loadError }}</div>
    <article v-else-if="blog" class="blog-detail-content">
      <!-- 图片轮播 -->
      <div v-if="blogImages.length" class="blog-carousel">
        <div class="carousel-container">
          <button v-if="currentImageIndex > 0" type="button" class="carousel-btn carousel-prev" @click="prevImage">
            <ChevronLeft :size="28" />
          </button>
          <div class="carousel-images">
            <img :src="blogImages[currentImageIndex]" :alt="blog.title" class="carousel-image" @error="onImageError" />
          </div>
          <button v-if="currentImageIndex < blogImages.length - 1" type="button" class="carousel-btn carousel-next" @click="nextImage">
            <ChevronRight :size="28" />
          </button>
        </div>

        <!-- 指示点 -->
        <div v-if="blogImages.length > 1" class="carousel-dots">
          <button
            v-for="(_, index) in blogImages"
            :key="index"
            type="button"
            :class="{ active: index === currentImageIndex }"
            class="dot"
            @click="goToImage(index)"
            :aria-label="`Image ${index + 1}`"
          />
        </div>
      </div>

      <!-- 作者信息 -->
      <div class="blog-author">
        <img :src="blog.icon || FALLBACK_AVATAR" class="author-avatar" @click="goToUser" @error="(e) => onImageError(e, FALLBACK_AVATAR)" />
        <div>
          <div class="author-name" @click="goToUser">{{ blog.name || '匿名用户' }}</div>
          <div class="author-time">{{ new Date(blog.createTime).toLocaleDateString() }}</div>
        </div>
      </div>

      <!-- 文章内容 -->
      <div class="blog-text">
        <h2>{{ blog.title }}</h2>
        <p>{{ blog.content }}</p>
      </div>

      <!-- 交互区 -->
      <div class="blog-engagement">
        <button type="button" :class="{ liked: blog.isLike }" @click="like">
          <Heart :size="20" :fill="blog.isLike ? 'currentColor' : 'none'" />
          {{ blog.liked || 0 }}
        </button>
        <span><MessageCircle :size="20" />{{ blog.comments || 0 }}</span>
      </div>

      <!-- 评论区 -->
      <div class="blog-comments">
        <h3>{{ comments.length }} 条评论</h3>

        <!-- 评论输入框 -->
        <div class="comment-input-box">
          <textarea
            v-model="commentInput"
            placeholder="写下你的评论..."
            class="comment-textarea"
            rows="3"
            maxlength="255"
          />
          <button
            type="button"
            class="submit-btn"
            :disabled="isSubmitting || !commentInput.trim()"
            @click="submitComment"
          >
            {{ isSubmitting ? '发表中...' : '发表评论' }}
          </button>
        </div>
        <p v-if="commentError" class="comment-hint error">{{ commentError }}</p>
        <p v-else-if="commentSuccess" class="comment-hint success">{{ commentSuccess }}</p>

        <div v-if="!comments.length" class="no-comments">暂无评论</div>
        <div v-else class="comments-list">
          <div v-for="comment in comments" :key="comment.id" class="comment-item">
            <img :src="comment.icon || FALLBACK_AVATAR" class="comment-avatar" @error="(e) => onImageError(e, FALLBACK_AVATAR)" />
            <div class="comment-content">
              <div class="comment-header">
                <span class="comment-author">{{ comment.userName }}</span>
                <span class="comment-time">{{ new Date(comment.createTime).toLocaleDateString() }}</span>
              </div>
              <p class="comment-text">{{ comment.content }}</p>
              <div class="comment-footer">
                <button type="button" :class="{ liked: comment.isLiked }" @click="likeComment(comment)">
                  <Heart :size="14" :fill="comment.isLiked ? 'currentColor' : 'none'" />{{ comment.liked || 0 }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </article>
  </div>
</template>

<style scoped>
.blog-detail {
  min-height: 100vh;
  background: #fff;
}

.blog-detail-header {
  position: sticky;
  top: 0;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  z-index: 10;
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

.blog-detail-header h1 {
  flex: 1;
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  color: #999;
  font-size: 14px;
}

.blog-detail-content {
  padding: 20px;
}

/* 图片轮播 */
.blog-carousel {
  margin-bottom: 24px;
}

.carousel-container {
  position: relative;
  width: 100%;
  background: #f5f5f5;
  border-radius: 12px;
  overflow: hidden;
  aspect-ratio: 1;
}

.carousel-images {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.carousel-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  animation: fadeIn 0.3s ease-in-out;
}

@keyframes fadeIn {
  from {
    opacity: 0.8;
  }
  to {
    opacity: 1;
  }
}

.carousel-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  background: rgba(0, 0, 0, 0.3);
  border: none;
  border-radius: 50%;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 5;
  transition: background 0.2s;
}

.carousel-btn:hover {
  background: rgba(0, 0, 0, 0.5);
}

.carousel-prev {
  left: 12px;
}

.carousel-next {
  right: 12px;
}

.carousel-dots {
  display: flex;
  justify-content: center;
  gap: 8px;
  padding: 12px 0;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ddd;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.dot.active {
  background: #ff4458;
  width: 24px;
  border-radius: 4px;
}

.dot:hover {
  background: #ccc;
}

/* 作者信息 */
.blog-author {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.blog-author .author-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  cursor: pointer;
  transition: opacity 0.2s;
}

.blog-author .author-avatar:hover {
  opacity: 0.8;
}

.author-name {
  font-weight: 600;
  color: #333;
  cursor: pointer;
  transition: color 0.2s;
}

.author-name:hover {
  color: #999;
}

.author-time {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

/* 文章内容 */
.blog-text h2 {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 16px;
  color: #333;
}

.blog-text p {
  color: #666;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0 0 16px;
}

/* 交互区 */
.blog-engagement {
  display: flex;
  gap: 24px;
  margin: 24px 0;
  padding: 16px 0;
  border-top: 1px solid #f0f0f0;
  border-bottom: 1px solid #f0f0f0;
}

.blog-engagement button {
  display: flex;
  align-items: center;
  gap: 8px;
  background: none;
  border: none;
  cursor: pointer;
  color: #999;
  font-size: 14px;
  transition: color 0.2s;
}

.blog-engagement button:hover {
  color: #333;
}

.blog-engagement button.liked {
  color: #ff4458;
}

.blog-engagement span {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #999;
  font-size: 14px;
}

/* 评论区 */
.blog-comments {
  margin-top: 32px;
}

.blog-comments h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 16px;
  color: #333;
}

.no-comments {
  text-align: center;
  padding: 24px;
  color: #999;
  font-size: 14px;
}

.comments-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 12px 0;
}

.comment-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.comment-content {
  flex: 1;
  min-width: 0;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.comment-author {
  font-weight: 600;
  color: #333;
  font-size: 14px;
}

.comment-time {
  font-size: 12px;
  color: #999;
}

.comment-text {
  color: #666;
  font-size: 14px;
  line-height: 1.6;
  margin: 0 0 8px;
  word-break: break-word;
}

.comment-footer {
  display: flex;
  gap: 16px;
}

.comment-footer button {
  display: flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  cursor: pointer;
  color: #999;
  font-size: 12px;
  transition: color 0.2s;
}

.comment-footer button:hover {
  color: #333;
}

.comment-footer button.liked {
  color: #ff4458;
}

/* 评论输入框 */
.comment-input-box {
  background: #f9f9f9;
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 24px;
}

.comment-textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
  font-family: inherit;
  box-sizing: border-box;
}

.comment-textarea:focus {
  outline: none;
  border-color: #ff4458;
  box-shadow: 0 0 0 2px rgba(255, 68, 88, 0.1);
}

.submit-btn {
  margin-top: 8px;
  width: 100%;
  padding: 10px;
  background: #ff4458;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.submit-btn:hover:not(:disabled) {
  background: #e63946;
}

.submit-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.comment-hint {
  margin: 0 0 16px;
  font-size: 13px;
  line-height: 1.5;
}

.comment-hint.error {
  color: #ff4458;
}

.comment-hint.success {
  color: #18a058;
}
</style>
