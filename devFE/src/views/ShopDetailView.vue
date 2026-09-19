<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ArrowLeft, Heart, MapPin, Phone, Share2, Star, Ticket } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../services/api'
import { demoShops, demoVouchers } from '../data/demo'
import type { Shop, Voucher } from '../types/domain'
const route = useRoute(); const router = useRouter(); const shop = ref<Shop>(demoShops[0]); const vouchers = ref<Voucher[]>(demoVouchers); const liked = ref(false)
onMounted(async () => { const id = Number(route.params.id); try { shop.value = (await api.shop(id)) || demoShops.find(s => s.id === id) || demoShops[0]; vouchers.value = (await api.vouchers(id)) || demoVouchers } catch { shop.value = demoShops.find(s => s.id === id) || demoShops[0] } })
</script>
<template>
  <div class="detail-top"><button type="button" class="icon-button" title="返回" @click="router.back()"><ArrowLeft :size="20" /></button><span>商户详情</span><div class="detail-actions"><button type="button" class="icon-button" title="分享"><Share2 :size="18" /></button><button type="button" class="icon-button" title="收藏" :class="{ liked }" @click="liked = !liked"><Heart :size="19" :fill="liked ? 'currentColor' : 'none'" /></button></div></div>
  <section class="detail-hero"><img :src="shop.images?.split(',')[0] || '/assets/cafe.jpg'" :alt="shop.name" /><div class="detail-hero-caption"><span>杭州 · {{ shop.area || '热门商圈' }}</span><h1>{{ shop.name }}</h1></div></section>
  <section class="detail-body"><div class="detail-main"><div class="detail-rating"><strong>{{ ((shop.score || 48) / 10).toFixed(1) }}</strong><div><div class="stars"><Star v-for="i in 5" :key="i" :size="18" fill="currentColor" /></div><span>{{ shop.comments || 0 }} 条评价 · 人气很高</span></div></div><div class="detail-info"><p><MapPin :size="18" />{{ shop.address || '杭州市热门商圈' }}<button type="button">地图</button></p><p><Phone :size="18" />{{ shop.openHours || '营业中 · 10:00-22:00' }}<button type="button">电话</button></p></div><div class="detail-divider"></div><h2>精选优惠</h2><div class="voucher-list"><div v-for="voucher in vouchers" :key="voucher.id" class="voucher"><div class="voucher-icon"><Ticket :size="20" /></div><div class="voucher-copy"><strong>{{ voucher.title }}</strong><span>{{ voucher.subTitle || '到店可用，详情以商家为准' }}</span><small>已售 {{ voucher.stock ? 99 - voucher.stock : 68 }}+</small></div><div class="voucher-price"><b>¥{{ (voucher.payValue / 100).toFixed(0) }}</b><span>¥{{ (voucher.actualValue / 100).toFixed(0) }}</span><button type="button">抢购</button></div></div></div></div><aside class="detail-aside"><div class="aside-card"><span class="eyebrow">店铺评分</span><b class="big-score">{{ ((shop.score || 48) / 10).toFixed(1) }}</b><p>超过 {{ Math.min(98, shop.score || 98) }}% 的同类商户</p><div class="score-lines"><span>环境 <i style="width: 92%"></i></span><span>服务 <i style="width: 95%"></i></span><span>口味 <i style="width: 94%"></i></span></div></div></aside></section>
</template>
