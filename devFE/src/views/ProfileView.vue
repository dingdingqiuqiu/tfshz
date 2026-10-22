<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ChevronRight,
  Heart,
  LogOut,
  ScanLine,
  ShieldCheck,
  ShoppingBag,
  Ticket,
  UserRound,
} from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import ShopCard from '../components/ShopCard.vue'
import { api } from '../services/api'
import { useAppStore } from '../stores/app'
import type { MyVoucherOrder, Shop } from '../types/domain'
import type { ProductOrder } from '../types/product'
import { FALLBACK_AVATAR, onImageError } from '../utils/images'

const app = useAppStore()
const router = useRouter()
const activeTab = ref('收藏')

const displayUser = computed(
  () => app.user || { nickName: '游客', icon: '/assets/avatar.png' }
)

/** 我的优惠券（秒杀订单） */
const coupons = ref<MyVoucherOrder[]>([])
/** 我收藏的店铺 */
const favorites = ref<Shop[]>([])
/** 我的商品订单（下单 → 支付 → 核销） */
const orders = ref<ProductOrder[]>([])
const loadingCoupons = ref(false)
const loadingFavorites = ref(false)
const loadingOrders = ref(false)
const couponsError = ref('')
const favoritesError = ref('')
const ordersError = ref('')

onMounted(async () => {
  await app.loadUser()
  if (!app.loggedIn) return
  await Promise.all([loadCoupons(), loadFavorites(), loadOrders()])
})

async function loadOrders() {
  loadingOrders.value = true
  ordersError.value = ''
  try {
    orders.value = (await api.myOrders()) || []
  } catch {
    orders.value = []
    ordersError.value = '订单加载失败，稍后重试'
  } finally {
    loadingOrders.value = false
  }
}

/** 商品单 + 秒杀券单合并成一条订单流（两者生命周期已经统一） */
interface UnifiedOrderItem {
  kind: 'product' | 'voucher'
  orderId: string
  title: string
  summary: string
  amount: number
  status: number
  statusText: string
  createTime?: string
}

const unifiedOrders = computed<UnifiedOrderItem[]>(() => {
  const productItems: UnifiedOrderItem[] = orders.value.map((order) => ({
    kind: 'product',
    orderId: order.orderId,
    title: order.shopName || '商品订单',
    summary: (order.items || []).map((item) => `${item.productName}x${item.quantity}`).join('、'),
    amount: Number(order.totalAmount || 0),
    status: Number(order.status || 1),
    statusText: order.statusText || '',
    createTime: order.createTime,
  }))
  const voucherItems: UnifiedOrderItem[] = coupons.value.map((coupon) => ({
    kind: 'voucher',
    orderId: coupon.orderId,
    title: coupon.title || '秒杀券',
    summary: `${coupon.shopName || '听风生活志'} · 抵扣 ¥${((coupon.actualValue || 0) / 100).toFixed(0)}`,
    amount: Number(coupon.payValue || 0),
    status: Number(coupon.status || 1),
    statusText: coupon.statusText || '',
    createTime: coupon.createTime,
  }))
  return [...productItems, ...voucherItems].sort((a, b) =>
    String(b.createTime || '').localeCompare(String(a.createTime || ''))
  )
})

async function loadCoupons() {
  loadingCoupons.value = true
  couponsError.value = ''
  try {
    coupons.value = (await api.myCoupons()) || []
  } catch {
    coupons.value = []
    couponsError.value = '优惠券加载失败，稍后重试'
  } finally {
    loadingCoupons.value = false
  }
}

async function loadFavorites() {
  loadingFavorites.value = true
  favoritesError.value = ''
  try {
    favorites.value = (await api.myFavorites()) || []
  } catch {
    favorites.value = []
    favoritesError.value = '收藏加载失败，稍后重试'
  } finally {
    loadingFavorites.value = false
  }
}

function formatDate(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleDateString()
}

function enter() {
  if (!app.loggedIn) router.push({ path: '/login', query: { redirect: '/profile' } })
}

function goOrders() {
  if (!app.loggedIn) return enter()
  document.getElementById('my-orders')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function handleLogout() {
  await app.logout()
  coupons.value = []
  favorites.value = []
  orders.value = []
  router.push('/home')
}
</script>

<template>
  <section class="profile-banner">
    <div class="profile-user">
      <img :src="displayUser.icon || FALLBACK_AVATAR" alt="头像" @error="(e) => onImageError(e, FALLBACK_AVATAR)" />
      <div>
        <span class="eyebrow light">WELCOME BACK</span>
        <h1>{{ displayUser.nickName }}</h1>
        <p>
          {{ app.loggedIn
            ? '今天也去发现一点新鲜事吧'
            : '登录后同步你的收藏与足迹' }}
        </p>
      </div>
    </div>

    <button v-if="!app.loggedIn" type="button" class="light-button" @click="enter">
      立即登录 <ChevronRight :size="16" />
    </button>

    <div v-else style="display:flex; align-items:center; gap:8px">
      <button type="button" class="light-button" @click="handleLogout">
        退出登录 <LogOut :size="16" />
      </button>
    </div>
  </section>

  <section class="profile-stats">
    <div><b>0</b><span>关注</span></div>
    <div><b>0</b><span>粉丝</span></div>
    <div><b>{{ coupons.length }}</b><span>优惠券</span></div>
    <div><b>{{ favorites.length }}</b><span>收藏</span></div>
  </section>

  <!-- 我的订单：商品单与秒杀券单已统一，同一套支付 / 核销码 / 关单 -->
  <section id="my-orders" class="coupon-section">
    <div class="coupon-head">
      <h2>我的订单</h2>
      <span>{{ app.loggedIn ? `${unifiedOrders.length} 单` : '登录后查看' }}</span>
    </div>

    <p v-if="!app.loggedIn" class="coupon-empty">
      <ShoppingBag :size="16" /> 登录后可以查看订单与核销二维码
      <button type="button" @click="enter">去登录</button>
    </p>
    <p v-else-if="loadingOrders || loadingCoupons" class="coupon-empty"><ShoppingBag :size="16" /> 正在加载...</p>
    <p v-else-if="ordersError || couponsError" class="coupon-empty error">{{ ordersError || couponsError }}</p>
    <div v-else-if="!unifiedOrders.length" class="coupon-empty">
      <ShoppingBag :size="16" /> 还没有订单，去
      <RouterLink to="/shops/1" class="link-strong">103茶餐厅</RouterLink>
      点一份或抢一张券试试
    </div>

    <div v-else class="order-list">
      <article
        v-for="item in unifiedOrders"
        :key="`${item.kind}-${item.orderId}`"
        class="order-row"
        @click="router.push(`/orders/${item.kind}/${item.orderId}`)"
      >
        <div class="order-row-main">
          <strong>
            <em class="order-kind" :class="item.kind">{{ item.kind === 'voucher' ? '秒杀券' : '商品' }}</em>
            {{ item.title }}
          </strong>
          <span>{{ item.summary }}</span>
          <small>{{ formatDate(item.createTime) }} · 订单号 {{ item.orderId }}</small>
        </div>
        <div class="order-row-right">
          <b>¥{{ (item.amount / 100).toFixed(2) }}</b>
          <em class="order-badge" :class="`status-${item.status}`">{{ item.statusText }}</em>
          <span class="order-link">
            {{ item.status === 1 ? '去支付' : item.status === 2 ? '看核销码' : '查看' }}
            <ChevronRight :size="13" />
          </span>
        </div>
      </article>
    </div>
  </section>

  <section class="profile-content">
    <div class="profile-tabs">
      <button
        v-for="tab in ['笔记', '收藏', '足迹']"
        :key="tab"
        type="button"
        :class="{ active: activeTab === tab }"
        @click="activeTab = tab"
      >
        {{ tab }}
      </button>
    </div>

    <!-- 收藏：真实数据，来自 tb_shop_favorite -->
    <template v-if="activeTab === '收藏'">
      <div v-if="!app.loggedIn" class="profile-empty">
        <div class="empty-icon"><Heart :size="22" /></div>
        <h3>登录后查看收藏</h3>
        <button type="button" @click="enter">去登录</button>
      </div>
      <div v-else-if="loadingFavorites" class="profile-empty">正在加载收藏...</div>
      <div v-else-if="favoritesError" class="profile-empty">{{ favoritesError }}</div>
      <div v-else-if="favorites.length" class="favorite-list">
        <ShopCard v-for="shop in favorites" :key="shop.id" :shop="shop" />
      </div>
      <div v-else class="profile-empty">
        <div class="empty-icon"><Heart :size="24" /></div>
        <h3>还没有收藏的店铺</h3>
        <p>在店铺详情页点右上角的爱心就能收藏</p>
        <button type="button" @click="router.push('/shops')">去逛逛</button>
      </div>
    </template>

    <div v-else class="profile-empty">
      <div class="empty-icon"><UserRound :size="24" /></div>
      <h3>{{ activeTab === '笔记' ? '还没有发布笔记' : '暂无浏览足迹' }}</h3>
      <p>去发现页记录你的城市生活吧</p>
      <button type="button" @click="router.push('/discover')">去发现</button>
    </div>
  </section>

  <section class="quick-links">
    <button type="button" @click="goOrders">
      <Ticket :size="19" /><span>我的订单</span><ChevronRight :size="17" />
    </button>
    <button type="button" @click="goOrders">
      <ShoppingBag :size="19" /><span>优惠券订单</span><ChevronRight :size="17" />
    </button>
    <button type="button" @click="app.loggedIn ? router.push('/verify') : enter()">
      <ScanLine :size="19" /><span>核销台</span><ChevronRight :size="17" />
    </button>
    <button type="button" @click="enter">
      <ShieldCheck :size="19" /><span>账号与安全</span><ChevronRight :size="17" />
    </button>
  </section>
</template>

<style scoped>
.coupon-section {
  margin-top: 26px;
  padding: 20px 22px 24px;
  background: #fff;
  border: 1px solid var(--line, #e7e2d9);
  border-radius: 7px;
}

.coupon-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.coupon-head h2 {
  font-size: 17px;
}

.coupon-head span {
  color: #aaa69f;
  font-size: 11px;
}

.coupon-empty {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 14px 0 6px;
  color: #aaa69f;
  font-size: 12px;
}

.coupon-empty.error {
  color: #c02b3c;
}

.coupon-empty button {
  background: none;
  color: var(--orange, #e8622c);
  font-size: 12px;
}

.coupon-empty .link-strong {
  color: var(--orange, #e8622c);
  font-weight: 700;
}

.coupon-list {
  display: grid;
  gap: 10px;
}

.coupon-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid #f0e3d4;
  border-radius: 6px;
  background: #fffaf3;
}

.coupon-value {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 74px;
  padding-right: 14px;
  border-right: 1px dashed #ecd9c4;
}

.coupon-value b {
  color: var(--orange, #e8622c);
  font-size: 22px;
  line-height: 1.1;
}

.coupon-value span {
  color: #b0a89c;
  font-size: 10px;
}

.coupon-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.coupon-main strong {
  font-size: 13px;
}

.coupon-main span {
  color: #8a857c;
  font-size: 11px;
}

.coupon-main small {
  color: #b0a89c;
  font-size: 10px;
}

.coupon-use {
  margin-left: auto;
  padding: 6px 13px;
  border-radius: 4px;
  background: var(--orange, #e8622c);
  color: #fff;
  font-size: 11px;
  white-space: nowrap;
}

.favorite-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 15px;
  padding: 18px;
}

/* ---------- 我的订单 ---------- */
.order-list {
  display: grid;
  gap: 10px;
}

.order-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 15px;
  border: 1px solid #f0e3d4;
  border-radius: 6px;
  background: #fffaf3;
  cursor: pointer;
  transition: box-shadow .18s;
}

.order-row:hover {
  box-shadow: 0 6px 18px rgba(42, 38, 29, .07);
}

.order-row-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.order-row-main strong {
  font-size: 13px;
}

/* 订单类型徽标：商品 / 秒杀券 */
.order-kind {
  display: inline-block;
  margin-right: 6px;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 10px;
  font-style: normal;
  background: #eef1f6;
  color: #4a5b74;
}

.order-kind.voucher {
  background: #ffeadc;
  color: #e8622c;
}

.order-row-main span {
  color: #8a857c;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-row-main small {
  color: #b0a89c;
  font-size: 10px;
}

.order-row-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  margin-left: auto;
}

.order-row-right b {
  color: var(--orange, #e8622c);
  font-size: 15px;
}

.order-badge {
  padding: 1px 7px;
  border-radius: 10px;
  font-size: 10px;
  font-style: normal;
  background: #efebe3;
  color: #7a746a;
}

.order-badge.status-1 {
  background: #fff1e6;
  color: #d9711f;
}

.order-badge.status-2 {
  background: #e9f6ef;
  color: #1f7a4d;
}

.order-badge.status-3 {
  background: #eef1f6;
  color: #4a5b74;
}

.order-link {
  display: inline-flex;
  align-items: center;
  color: var(--orange, #e8622c);
  font-size: 11px;
}

@media (max-width: 800px) {
  .favorite-list {
    grid-template-columns: 1fr;
  }

  .coupon-card {
    flex-wrap: wrap;
  }

  .coupon-use {
    margin-left: 0;
  }
}
</style>
