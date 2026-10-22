<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, Check, Heart, MapPin, Phone, Share2, ShoppingBag, Star, Ticket } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../services/api'
import { useAppStore } from '../stores/app'
import type { Shop, Voucher } from '../types/domain'
import type { Product } from '../types/product'
import { FALLBACK_SHOP_IMAGE, firstImage, onImageError } from '../utils/images'

const route = useRoute(); const router = useRouter()
const app = useAppStore()
const shop = ref<Shop | null>(null); const vouchers = ref<Voucher[]>([])
const products = ref<Product[]>([])
const loading = ref(true); const error = ref('')

/** 是否已收藏该店铺（后端持久化，刷新后仍在） */
const favorite = ref(false)
const favoriteBusy = ref(false)
/** 正在抢购的券 id */
const buyingId = ref<number | null>(null)
/** 已经抢到的券 id（来自“我的优惠券”，用来把按钮置灰成“已抢到”） */
const purchased = ref<Set<number>>(new Set())
/** 顶部提示条 */
const notice = ref<{ type: 'success' | 'error'; text: string } | null>(null)

const shopId = Number(route.params.id)

/** 购物车：productId -> 数量（美团式的“选商品 → 结算”） */
const cart = ref<Record<number, number>>({})
const ordering = ref(false)

const cartCount = computed(() => Object.values(cart.value).reduce((sum, n) => sum + n, 0))
const cartTotal = computed(() => products.value.reduce((sum, p) => sum + (cart.value[p.id] || 0) * p.price, 0))

onMounted(async () => {
  try {
    shop.value = (await api.shop(shopId)) || null
    vouchers.value = (await api.vouchers(shopId)) || []
    products.value = (await api.products(shopId)) || []
  } catch {
    error.value = '商户信息加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
  await loadFavorite()
  await loadPurchased()
})

function stockOf(product: Product) {
  return typeof product.stock === 'number' ? product.stock : 0
}

function addToCart(product: Product) {
  if (stockOf(product) <= 0) return
  const current = cart.value[product.id] || 0
  if (current >= stockOf(product)) return
  cart.value = { ...cart.value, [product.id]: current + 1 }
}

function removeFromCart(product: Product) {
  const current = cart.value[product.id] || 0
  if (current <= 1) {
    const next = { ...cart.value }
    delete next[product.id]
    cart.value = next
    return
  }
  cart.value = { ...cart.value, [product.id]: current - 1 }
}

/** 结算：创建待支付订单，然后跳到订单页去支付/看二维码 */
async function checkout() {
  if (!cartCount.value || ordering.value) return
  if (!requireLogin()) return
  ordering.value = true
  try {
    const items = Object.entries(cart.value).map(([productId, quantity]) => ({
      productId: Number(productId),
      quantity,
    }))
    const order = await api.createOrder({ shopId, items })
    cart.value = {}
    router.push(`/orders/product/${order.orderId}`)
  } catch (cause) {
    notice.value = {
      type: 'error',
      text: cause instanceof Error && cause.message ? cause.message : '下单失败，请稍后重试',
    }
  } finally {
    ordering.value = false
  }
}

/** 未登录时先去登录页，登录后自动回到当前店铺 */
function requireLogin() {
  if (app.loggedIn) return true
  notice.value = { type: 'error', text: '请先登录' }
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

async function loadFavorite() {
  if (!app.loggedIn) return
  try {
    favorite.value = Boolean(await api.isShopFavorite(shopId))
  } catch {
    // 收藏状态拿不到不影响页面主体
  }
}

async function loadPurchased() {
  if (!app.loggedIn) return
  try {
    const orders = (await api.myCoupons()) || []
    purchased.value = new Set(orders.map((order) => order.voucherId))
  } catch {
    // 忽略：抢购时后端还会再校验一次一人一单
  }
}

async function toggleFavorite() {
  if (!requireLogin() || favoriteBusy.value) return
  favoriteBusy.value = true
  try {
    const next = !favorite.value
    await api.favoriteShop(shopId, next)
    favorite.value = next
    notice.value = {
      type: 'success',
      text: next ? '已收藏，可在「我的 → 收藏」查看' : '已取消收藏',
    }
  } catch {
    notice.value = { type: 'error', text: '收藏操作失败，请稍后重试' }
  } finally {
    favoriteBusy.value = false
  }
}

/** 抢券：只有秒杀券（type === 1）可以抢 */
async function buy(voucher: Voucher) {
  if (voucher.type !== 1 || buyingId.value !== null || purchased.value.has(voucher.id)) return
  if (!requireLogin()) return
  buyingId.value = voucher.id
  notice.value = null
  try {
    await api.seckill(voucher.id)
    purchased.value = new Set([...purchased.value, voucher.id])
    if (typeof voucher.stock === 'number' && voucher.stock > 0) {
      voucher.stock -= 1
    }
    notice.value = { type: 'success', text: '抢购成功！优惠券已放进「我的 → 我的优惠券」' }
  } catch (cause) {
    const text = cause instanceof Error && cause.message ? cause.message : '抢购失败，请稍后重试'
    notice.value = { type: 'error', text }
  } finally {
    buyingId.value = null
  }
}

function isBought(voucher: Voucher) {
  return purchased.value.has(voucher.id)
}

function buttonText(voucher: Voucher) {
  if (voucher.type !== 1) return '到店购买'
  if (isBought(voucher)) return '已抢到'
  if (buyingId.value === voucher.id) return '抢购中...'
  return '抢购'
}

function soldOut(voucher: Voucher) {
  return voucher.type === 1 && typeof voucher.stock === 'number' && voucher.stock <= 0
}
</script>

<template>
  <div class="detail-top">
    <button type="button" class="icon-button" title="返回" @click="router.back()"><ArrowLeft :size="20" /></button>
    <span>商户详情</span>
    <div class="detail-actions">
      <button type="button" class="icon-button" title="分享"><Share2 :size="18" /></button>
      <button
        type="button"
        class="icon-button"
        :class="{ liked: favorite, busy: favoriteBusy }"
        :title="favorite ? '取消收藏' : '收藏店铺'"
        @click="toggleFavorite"
      >
        <Heart :size="19" :fill="favorite ? 'currentColor' : 'none'" />
      </button>
    </div>
  </div>

  <div v-if="loading" class="empty-state">正在加载商户信息...</div>
  <div v-else-if="error" class="empty-state">{{ error }}</div>
  <div v-else-if="!shop" class="empty-state">未找到该商户</div>

  <section v-if="shop" class="detail-hero">
    <img :src="firstImage(shop.images, FALLBACK_SHOP_IMAGE)" :alt="shop.name" @error="(e) => onImageError(e, FALLBACK_SHOP_IMAGE)" />
    <div class="detail-hero-caption">
      <span>杭州 · {{ shop.area || '热门商圈' }}</span>
      <h1>{{ shop.name }}</h1>
    </div>
  </section>

  <section v-if="shop" class="detail-body">
    <div class="detail-main">
      <div class="detail-rating">
        <strong>{{ ((shop.score || 48) / 10).toFixed(1) }}</strong>
        <div>
          <div class="stars"><Star v-for="i in 5" :key="i" :size="18" fill="currentColor" /></div>
          <span>{{ shop.comments || 0 }} 条评价 · 人气很高</span>
        </div>
      </div>
      <div class="detail-info">
        <p><MapPin :size="18" />{{ shop.address || '杭州市热门商圈' }}<button type="button">地图</button></p>
        <p><Phone :size="18" />{{ shop.openHours || '营业中 · 10:00-22:00' }}<button type="button">电话</button></p>
      </div>
      <div class="detail-divider"></div>
      <h2>店内商品</h2>
      <div class="product-list">
        <article v-for="product in products" :key="product.id" class="product-card">
          <img :src="product.image || '/assets/food.jpg'" :alt="product.name" @error="(e) => onImageError(e, '/assets/food.jpg')" />
          <div class="product-info">
            <strong>{{ product.name }}</strong>
            <span>{{ product.description || '招牌好味' }}</span>
            <small>剩 {{ stockOf(product) }} 份</small>
          </div>
          <div class="product-buy">
            <b>¥{{ (product.price / 100).toFixed(0) }}</b>
            <div v-if="cart[product.id]" class="stepper">
              <button type="button" @click="removeFromCart(product)">−</button>
              <span>{{ cart[product.id] }}</span>
              <button type="button" @click="addToCart(product)">+</button>
            </div>
            <button
              v-else
              type="button"
              class="add-btn"
              :disabled="stockOf(product) <= 0"
              @click="addToCart(product)"
            >
              +
            </button>
          </div>
        </article>
        <div v-if="!products.length" class="empty-state">该商户还没有上架商品</div>
      </div>

      <div class="detail-divider"></div>
      <h2>精选优惠</h2>

      <p v-if="notice" class="voucher-notice" :class="notice.type">
        <Check v-if="notice.type === 'success'" :size="15" />
        <span>{{ notice.text }}</span>
        <button v-if="notice.type === 'success'" type="button" @click="router.push('/profile')">去我的优惠券 →</button>
      </p>

      <div class="voucher-list">
        <div v-for="voucher in vouchers" :key="voucher.id" class="voucher">
          <div class="voucher-icon"><Ticket :size="20" /></div>
          <div class="voucher-copy">
            <strong>
              {{ voucher.title }}
              <em v-if="voucher.type === 1" class="seckill-badge">秒杀</em>
            </strong>
            <span>{{ voucher.subTitle || '到店可用，详情以商家为准' }}</span>
            <small v-if="voucher.type === 1">
              {{ soldOut(voucher) ? '已抢光' : `仅剩 ${voucher.stock ?? 0} 张` }}
            </small>
            <small v-else>周一至周日可用</small>
          </div>
          <div class="voucher-price">
            <b>¥{{ ((voucher.payValue || 0) / 100).toFixed(0) }}</b>
            <span>¥{{ ((voucher.actualValue || 0) / 100).toFixed(0) }}</span>
            <button
              type="button"
              :disabled="voucher.type !== 1 || buyingId !== null || isBought(voucher) || soldOut(voucher)"
              @click="buy(voucher)"
            >
              {{ buttonText(voucher) }}
            </button>
          </div>
        </div>
        <div v-if="!vouchers.length" class="empty-state">该商户暂无可用优惠</div>
      </div>
    </div>

    <aside class="detail-aside">
      <div class="aside-card">
        <span class="eyebrow">店铺评分</span>
        <b class="big-score">{{ ((shop.score || 48) / 10).toFixed(1) }}</b>
        <p>超过 {{ Math.min(98, shop.score || 98) }}% 的同类商户</p>
        <div class="score-lines">
          <span>环境 <i style="width: 92%"></i></span>
          <span>服务 <i style="width: 95%"></i></span>
          <span>口味 <i style="width: 94%"></i></span>
        </div>
      </div>
    </aside>
  </section>

  <!-- 购物车结算条：选了商品才出现 -->
  <div v-if="cartCount" class="cart-bar">
    <ShoppingBag :size="18" />
    <span>已选 {{ cartCount }} 件</span>
    <b>¥{{ (cartTotal / 100).toFixed(2) }}</b>
    <button type="button" :disabled="ordering" @click="checkout">
      {{ ordering ? '下单中...' : '去结算' }}
    </button>
  </div>
</template>

<style scoped>
.detail-actions .icon-button.liked {
  color: #ff4458;
}

.detail-actions .icon-button.busy {
  opacity: .5;
}

.voucher-notice {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 12px 0 0;
  padding: 9px 12px;
  border-radius: 6px;
  font-size: 12px;
}

.voucher-notice.success {
  color: #1f7a4d;
  background: #eaf7f0;
}

.voucher-notice.error {
  color: #c02b3c;
  background: #fdeff1;
}

.voucher-notice button {
  margin-left: auto;
  background: none;
  color: inherit;
  font-size: 12px;
  font-weight: 700;
}

.seckill-badge {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 5px;
  border-radius: 3px;
  background: #ffeadc;
  color: #e8622c;
  font-size: 10px;
  font-style: normal;
  vertical-align: 1px;
}

.voucher-price button:disabled {
  background: #d9d4cb;
  color: #fff;
  cursor: not-allowed;
}

/* ---------- 店内商品 ---------- */
.product-list {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.product-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px;
  border: 1px solid var(--line, #e7e2d9);
  border-radius: 8px;
  background: #fff;
}

.product-card img {
  width: 74px;
  height: 74px;
  object-fit: cover;
  border-radius: 6px;
  flex: 0 0 74px;
}

.product-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.product-info strong {
  font-size: 14px;
}

.product-info span {
  color: #8a857c;
  font-size: 11px;
}

.product-info small {
  color: #b0a89c;
  font-size: 10px;
}

.product-buy {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  margin-left: auto;
}

.product-buy b {
  color: var(--orange, #e8622c);
  font-size: 17px;
}

.add-btn,
.stepper button {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--orange, #e8622c);
  color: #fff;
  font-size: 16px;
  line-height: 1;
}

.add-btn:disabled {
  background: #d9d4cb;
  cursor: not-allowed;
}

.stepper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stepper span {
  min-width: 16px;
  text-align: center;
  font-size: 13px;
}

/* ---------- 结算条 ---------- */
.cart-bar {
  position: sticky;
  bottom: 16px;
  z-index: 15;
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 22px auto 0;
  max-width: 560px;
  padding: 12px 18px;
  background: #2f3d36;
  color: #fff;
  border-radius: 40px;
  box-shadow: 0 10px 26px rgba(20, 28, 24, .28);
  font-size: 12px;
}

.cart-bar b {
  margin-left: auto;
  font-size: 18px;
}

.cart-bar button {
  padding: 8px 20px;
  border-radius: 30px;
  background: var(--orange, #e8622c);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.cart-bar button:disabled {
  background: #6d7a73;
  cursor: not-allowed;
}
</style>
