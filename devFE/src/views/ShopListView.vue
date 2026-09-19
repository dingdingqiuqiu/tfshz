<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Search, SlidersHorizontal, MapPin } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import ShopCard from '../components/ShopCard.vue'
import { api } from '../services/api'
import { demoShops, demoTypes } from '../data/demo'
import type { Shop, ShopType } from '../types/domain'
const route = useRoute(); const router = useRouter()
const query = ref(String(route.query.q || '')); const activeType = ref(Number(route.query.type || 0)); const activeSort = ref('综合排序'); const loading = ref(false)
const shops = ref<Shop[]>(demoShops); const types = ref<ShopType[]>(demoTypes)
const sorts = ['综合排序', '离我最近', '评分最高', '人气最高']
const title = computed(() => String(route.query.name || (activeType.value ? types.value.find(t => t.id === activeType.value)?.name : '全部商户') || '全部商户'))
async function load() { loading.value = true; try { shops.value = activeType.value ? ((await api.shopsByType(activeType.value)) || demoShops) : query.value ? ((await api.shopsByName(query.value)) || demoShops) : demoShops } catch { shops.value = demoShops } finally { loading.value = false } }
function doSearch() { router.replace({ path: '/shops', query: { ...(activeType.value ? { type: activeType.value } : {}), ...(query.value ? { q: query.value } : {}) } }); load() }
onMounted(async () => { try { types.value = (await api.shopTypes()) || demoTypes } catch {} load() })
</script>
<template>
  <section class="list-head"><div><span class="eyebrow">LOCAL GUIDE</span><h1>找一家<br /><em>刚刚好的店</em></h1></div><div class="location-note"><MapPin :size="17" />{{ title }}<small>杭州 · 为你推荐</small></div></section>
  <div class="shop-search"><Search :size="19" /><input v-model="query" placeholder="搜商户名称" @keyup.enter="doSearch" /><button type="button" @click="doSearch">搜索</button></div>
  <div class="shop-filters"><div class="type-scroll"><button type="button" :class="{ active: !activeType }" @click="activeType = 0; load()">全部</button><button v-for="type in types" :key="type.id" type="button" :class="{ active: activeType === type.id }" @click="activeType = type.id; load()">{{ type.name }}</button></div><button class="filter-button" type="button"><SlidersHorizontal :size="16" />筛选</button></div>
  <div class="sort-row"><span>为你找到 <b>{{ shops.length }}</b> 家好店</span><div class="sorts"><button v-for="sort in sorts" :key="sort" type="button" :class="{ active: activeSort === sort }" @click="activeSort = sort">{{ sort }}</button></div></div>
  <div v-if="loading" class="empty-state">正在寻找附近好店...</div><div v-else class="shops-list"><ShopCard v-for="shop in shops" :key="shop.id" :shop="shop" /></div>
</template>
