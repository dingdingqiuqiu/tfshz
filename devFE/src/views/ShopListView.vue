<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Search, SlidersHorizontal, MapPin } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import ShopCard from '../components/ShopCard.vue'
import { api } from '../services/api'
import type { Shop, ShopType } from '../types/domain'

const route = useRoute(); const router = useRouter()
const query = ref(String(route.query.q || '')); const activeType = ref(Number(route.query.type || 0)); const activeSort = ref('综合排序'); const loading = ref(false); const error = ref('')
const shops = ref<Shop[]>([]); const types = ref<ShopType[]>([])
const sorts = ['综合排序', '离我最近', '评分最高', '人气最高']
const title = computed(() => {
  if (route.query.name) return String(route.query.name)
  if (activeType.value) return types.value.find(t => t.id === activeType.value)?.name || '全部商户'
  if (query.value) return `搜索：${query.value}`
  return '全部商户'
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (activeType.value) {
      shops.value = (await api.shopsByType(activeType.value)) || []
    } else if (query.value) {
      shops.value = (await api.shopsByName(query.value)) || []
    } else {
      // “全部”必须真的去后端取全量店铺：以前这里直接 shops.value = []，所以永远显示 0 家
      shops.value = (await api.shopList()) || []
    }
  } catch {
    shops.value = []
    error.value = '店铺列表加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

/** 切换分类时同步 URL，刷新/分享链接后仍停在同一分类 */
function selectType(id: number, name?: string) {
  activeType.value = id
  router.replace({
    path: '/shops',
    query: {
      ...(id ? { type: id, ...(name ? { name } : {}) } : {}),
      ...(query.value ? { q: query.value } : {}),
    },
  })
  load()
}

function doSearch() {
  const keyword = query.value.trim()
  query.value = keyword
  router.replace({
    path: '/shops',
    query: {
      ...(activeType.value ? { type: activeType.value } : {}),
      ...(keyword ? { q: keyword } : {}),
    },
  })
  load()
}

onMounted(async () => { try { types.value = (await api.shopTypes()) || [] } catch { types.value = [] } load() })
</script>
<template>
  <section class="list-head"><div><span class="eyebrow">LOCAL GUIDE</span><h1>找一家<br /><em>刚刚好的店</em></h1></div><div class="location-note"><MapPin :size="17" />{{ title }}<small>杭州 · 为你推荐</small></div></section>
  <div class="shop-search"><Search :size="19" /><input v-model="query" placeholder="搜商户名称" @keyup.enter="doSearch" /><button type="button" @click="doSearch">搜索</button></div>
  <div class="shop-filters"><div class="type-scroll"><button type="button" :class="{ active: !activeType }" @click="selectType(0)">全部</button><button v-for="type in types" :key="type.id" type="button" :class="{ active: activeType === type.id }" @click="selectType(type.id, type.name)">{{ type.name }}</button></div><button class="filter-button" type="button"><SlidersHorizontal :size="16" />筛选</button></div>
  <div class="sort-row"><span>为你找到 <b>{{ shops.length }}</b> 家好店</span><div class="sorts"><button v-for="sort in sorts" :key="sort" type="button" :class="{ active: activeSort === sort }" @click="activeSort = sort">{{ sort }}</button></div></div>
  <div v-if="loading" class="empty-state">正在寻找附近好店...</div><div v-else-if="error" class="empty-state">{{ error }}</div><div v-else-if="!shops.length" class="empty-state">暂无符合条件的商户，换个分类或关键词试试</div><div v-else class="shops-list"><ShopCard v-for="shop in shops" :key="shop.id" :shop="shop" /></div>
</template>
