<script setup lang="ts">
import { MapPin, Star } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
import type { Shop } from '../types/domain'
defineProps<{ shop: Shop }>()
</script>

<template>
  <RouterLink :to="`/shops/${shop.id}`" class="shop-card">
    <img class="shop-cover" :src="shop.images?.split(',')[0] || '/assets/cafe.jpg'" :alt="shop.name" />
    <div class="shop-card-body"><div class="shop-title-row"><h3>{{ shop.name }}</h3><span class="shop-distance">{{ shop.distance ? `${shop.distance}km` : '附近' }}</span></div><div class="rating"><Star :size="15" fill="currentColor" /><b>{{ ((shop.score || 46) / 10).toFixed(1) }}</b><span>{{ shop.comments || 0 }} 条评价</span></div><p class="shop-address"><MapPin :size="14" />{{ shop.area || '杭州' }} · {{ shop.address || '暂无地址' }}</p><div class="shop-foot"><span>人均 ¥{{ shop.avgPrice || 0 }}</span><span class="sold">已售 {{ shop.sold || 0 }}+</span></div></div>
  </RouterLink>
</template>
