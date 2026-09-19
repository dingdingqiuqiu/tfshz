<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Search, Sparkles, Ticket, Utensils, Coffee, Waves, Baby } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import SectionHeading from '../components/SectionHeading.vue'
import BlogCard from '../components/BlogCard.vue'
import ShopCard from '../components/ShopCard.vue'
import { api } from '../services/api'
import { demoBlogs, demoShops, demoTypes } from '../data/demo'
import type { Blog, Shop, ShopType } from '../types/domain'

const router = useRouter()
const query = ref('')
const types = ref<ShopType[]>([])
const blogs = ref<Blog[]>([])
const shops = ref<Shop[]>([])
const typeIcons = [Utensils, Waves, Sparkles, Baby, Coffee, Ticket]

const blogPage = ref(1)
const blogLoading = ref(false)
const blogHasMore = ref(true)
const pageError = ref('')
const featuredType = computed(() => types.value[0])

function typeIcon(icon?: string) {
  if (!icon) return ''
  if (/^(https?:|data:|\/assets\/|\/imgs\/)/.test(icon)) return icon
  // 旧版首页使用 /imgs/ + t.icon；数据库保存的是 /types/xxx.png。
  return `/imgs/${icon.replace(/^\/+/, '')}`
}

const search = () => {
  const keyword = query.value.trim()
  if (keyword) router.push({ path: '/shops', query: { q: keyword } })
}

async function loadTypes() {
  try {
    // 对应旧版 index.html 的 /shop-type/list，成功返回空数组时也保留后端状态。
    types.value = (await api.shopTypes()) || []
  } catch {
    types.value = demoTypes
    pageError.value = '分类服务暂时不可用，当前展示演示分类'
  }
}

async function loadFeaturedShops() {
  if (!featuredType.value) return
  try {
    shops.value = (await api.shopsByType(featuredType.value.id, 1)) || []
  } catch {
    shops.value = demoShops
  }
}

async function loadBlogs(reset = false) {
  if (blogLoading.value || (!reset && !blogHasMore.value)) return
  if (reset) {
    blogPage.value = 1
    blogHasMore.value = true
  }

  blogLoading.value = true
  try {
    // 后端每页最多返回 10 条，沿用旧版首页的 current 分页逻辑。
    const page = (await api.hotBlogs(blogPage.value)) || []
    blogs.value = reset ? page : blogs.value.concat(page)
    blogHasMore.value = page.length >= 10
    if (page.length > 0) blogPage.value += 1
  } catch {
    // 仅在后端请求失败时使用演示数据；不会覆盖后端成功返回的空状态。
    if (reset && blogs.value.length === 0) {
      blogs.value = demoBlogs
      blogHasMore.value = false
    }
  } finally {
    blogLoading.value = false
  }
}

async function like(blog: Blog) {
  pageError.value = ''
  try {
    // 点赞接口只返回操作结果，必须回读详情才能同步 liked 和 isLike。
    await api.likeBlog(blog.id)
    const latest = await api.blog(blog.id)
    if (latest) Object.assign(blog, latest)
  } catch (cause) {
    pageError.value = cause instanceof Error ? cause.message : '点赞失败，请先登录后重试'
  }
}

function handleScroll() {
  const nearBottom = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 480
  if (nearBottom) loadBlogs()
}

async function load() {
  await loadTypes()
  await Promise.all([loadFeaturedShops(), loadBlogs(true)])
}

onMounted(() => {
  load()
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onUnmounted(() => window.removeEventListener('scroll', handleScroll))
</script>

<template>
  <section class="home-hero">
    <div class="hero-copy"><span class="eyebrow light">城市生活指南</span><h1>把城市的好滋味<br /><em>一网打尽</em></h1><p>发现附近值得去的店，收藏每一次心动。</p><div class="hero-search"><Search :size="20" /><input v-model="query" placeholder="搜商户、菜品或地点" @keyup.enter="search" /><button type="button" @click="search">搜索</button></div><div class="hero-trending"><span>大家都在搜</span><button type="button" @click="query = '咖啡'; search()">咖啡</button><button type="button" @click="query = '火锅'; search()">火锅</button><button type="button" @click="query = '江景'; search()">江景餐厅</button></div></div>
    <div class="hero-art"><div class="hero-orbit orbit-one"></div><div class="hero-orbit orbit-two"></div><div class="hero-sticker">今日<br /><strong>好店</strong></div><div class="hero-stat"><b>1,286</b><span>家本地好店</span></div></div>
  </section>

  <section class="home-section category-section"><SectionHeading eyebrow="EXPLORE" title="按兴趣逛逛" action="全部分类" @action="router.push('/shops')" /><div class="category-grid"><button v-for="(type, index) in types.slice(0, 6)" :key="type.id" type="button" class="category-item" @click="router.push({ path: '/shops', query: { type: type.id, name: type.name } })"><span class="category-icon"><img v-if="type.icon" :src="typeIcon(type.icon)" alt="" /><component :is="typeIcons[index % typeIcons.length]" v-else :size="24" /></span><strong>{{ type.name }}</strong><small>{{ ['本地热榜', '今晚去玩', '品质生活', '周末去处', '咖啡甜点', '亲子时光'][index] }}</small></button></div></section>

  <section class="home-section"><SectionHeading eyebrow="NEARBY PICKS" title="附近的人气好店" action="查看全部" @action="router.push('/shops')" /><div class="shop-grid"><ShopCard v-for="shop in shops.slice(0, 3)" :key="shop.id" :shop="shop" /></div></section>
  <section class="home-section"><SectionHeading eyebrow="CITY NOTES" title="大家都在分享" action="去发现" @action="router.push('/discover')" /><div class="blog-grid"><BlogCard v-for="blog in blogs" :key="blog.id" :blog="blog" @like="like" /></div><div v-if="blogLoading" class="home-load-state">正在加载更多内容...</div><div v-else-if="!blogHasMore && blogs.length" class="home-load-state">已经看到全部内容</div></section>
  <p v-if="pageError" class="home-error">{{ pageError }}</p>
</template>

<style scoped>
.home-load-state {
  padding: 18px 0 2px;
  color: #9b9891;
  font-size: 12px;
  text-align: center;
}

.home-error {
  margin: 22px 0 0;
  color: #b84a30;
  font-size: 12px;
  text-align: center;
}
</style>
