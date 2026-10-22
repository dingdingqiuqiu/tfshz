<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, CheckCircle2, Clock3, QrCode as QrIcon, Store, XCircle } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import QrCode from '../components/QrCode.vue'
import { api } from '../services/api'
import type { MyVoucherOrder } from '../types/domain'
import type { ProductOrder } from '../types/product'

/**
 * 商品订单与秒杀券订单共用这一个页面：
 * 后端虽然分两张表，但「支付 → 核销码 → 核销 → 超时关单」的生命周期完全同构，
 * 前端只需要知道 type 决定调哪组接口，展示与交互就统一了。
 */
const route = useRoute()
const router = useRouter()

const kind: 'product' | 'voucher' = String(route.params.type) === 'voucher' ? 'voucher' : 'product'
const orderId = String(route.params.id)

interface OrderLine {
  name: string
  quantity: number
  amount: number
}

interface OrderView {
  kind: 'product' | 'voucher'
  orderId: string
  title: string
  shopName?: string
  totalAmount: number
  status: number
  statusText: string
  verifyCode?: string
  expireTime?: string
  payTime?: string
  verifyTime?: string
  createTime?: string
  lines: OrderLine[]
}

const order = ref<OrderView | null>(null)
const loading = ref(true)
const error = ref('')
const busy = ref(false)
const notice = ref<{ type: 'success' | 'error'; text: string } | null>(null)

const money = (cents?: number) => ((cents || 0) / 100).toFixed(2)
const timeText = (value?: string) => {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString()
}

const waitPay = computed(() => order.value?.status === 1)
const paid = computed(() => order.value?.status === 2)
const verified = computed(() => order.value?.status === 3)
const closed = computed(() => order.value?.status === 4)

onMounted(load)

function fromProduct(source: ProductOrder): OrderView {
  return {
    kind: 'product',
    orderId: source.orderId,
    title: source.shopName || '商品订单',
    shopName: source.shopName,
    totalAmount: source.totalAmount,
    status: source.status,
    statusText: source.statusText || '',
    verifyCode: source.verifyCode,
    expireTime: source.expireTime,
    payTime: source.payTime,
    verifyTime: source.verifyTime,
    createTime: source.createTime,
    lines: (source.items || []).map((item) => ({
      name: item.productName,
      quantity: item.quantity,
      amount: item.amount,
    })),
  }
}

function fromVoucher(source: MyVoucherOrder): OrderView {
  const amount = Number(source.payValue || 0)
  return {
    kind: 'voucher',
    orderId: source.orderId,
    title: source.title || '秒杀券',
    shopName: source.shopName,
    totalAmount: amount,
    status: Number(source.status || 1),
    statusText: source.statusText || '',
    verifyCode: source.verifyCode,
    expireTime: source.expireTime,
    payTime: source.payTime,
    verifyTime: source.verifyTime,
    createTime: source.createTime,
    lines: [{ name: source.title || '秒杀券', quantity: 1, amount }],
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (kind === 'voucher') {
      order.value = fromVoucher(await api.voucherOrderDetail(orderId))
    } else {
      order.value = fromProduct(await api.orderDetail(orderId))
    }
  } catch (cause) {
    error.value = cause instanceof Error && cause.message ? cause.message : '订单加载失败'
  } finally {
    loading.value = false
  }
}

/** 支付：两种订单共用 Mock 支付通道（真实第三方支付已在后端 PayService 预留） */
async function pay() {
  if (!order.value || busy.value) return
  busy.value = true
  notice.value = null
  try {
    if (kind === 'voucher') {
      order.value = fromVoucher(await api.payVoucherOrder(orderId))
    } else {
      order.value = fromProduct(await api.payOrder(orderId))
    }
    notice.value = { type: 'success', text: '支付成功，凭二维码到店核销' }
  } catch (cause) {
    notice.value = {
      type: 'error',
      text: cause instanceof Error && cause.message ? cause.message : '支付失败，请重试',
    }
    // 支付失败可能是订单刚被关单，刷新一次拿到最新状态
    await load()
  } finally {
    busy.value = false
  }
}

async function cancel() {
  if (!order.value || busy.value) return
  busy.value = true
  notice.value = null
  try {
    if (kind === 'voucher') {
      order.value = fromVoucher(await api.cancelVoucherOrder(orderId))
    } else {
      order.value = fromProduct(await api.cancelOrder(orderId))
    }
    notice.value = { type: 'success', text: '订单已取消，库存已退回' }
  } catch (cause) {
    notice.value = {
      type: 'error',
      text: cause instanceof Error && cause.message ? cause.message : '取消失败，请重试',
    }
    await load()
  } finally {
    busy.value = false
  }
}

function goVerify() {
  if (!order.value?.verifyCode) return
  router.push({ path: '/verify', query: { code: order.value.verifyCode } })
}
</script>

<template>
  <div class="order-top">
    <button type="button" class="icon-button" title="返回" @click="router.back()"><ArrowLeft :size="20" /></button>
    <span>{{ kind === 'voucher' ? '秒杀券订单' : '商品订单' }}</span>
    <button type="button" class="icon-button" title="我的订单" @click="router.push('/profile')">我的</button>
  </div>

  <div v-if="loading" class="empty-state">正在加载订单...</div>
  <div v-else-if="error" class="empty-state">{{ error }}</div>

  <section v-else-if="order" class="order-card">
    <header class="order-status" :class="{ paid: paid || verified, closed }">
      <CheckCircle2 v-if="paid || verified" :size="26" />
      <Clock3 v-else-if="waitPay" :size="26" />
      <XCircle v-else :size="26" />
      <div>
        <strong>{{ order.statusText || (waitPay ? '待支付' : paid ? '待核销' : verified ? '已核销' : '已关闭') }}</strong>
        <span v-if="waitPay">请在 {{ timeText(order.expireTime) }} 前完成支付，超时自动关闭并退回库存</span>
        <span v-else-if="paid">到店出示下面的二维码，商家扫码即可核销</span>
        <span v-else-if="verified">已于 {{ timeText(order.verifyTime) }} 核销</span>
        <span v-else>订单未支付，已超时关闭</span>
      </div>
    </header>

    <p v-if="notice" class="order-notice" :class="notice.type">{{ notice.text }}</p>

    <div class="order-shop">
      <Store :size="16" />
      <strong>{{ order.shopName || order.title }}</strong>
      <small>订单号 {{ order.orderId }}</small>
    </div>

    <ul class="order-items">
      <li v-for="(line, index) in order.lines" :key="index">
        <span class="item-name">
          {{ line.name }}
          <em v-if="order.kind === 'voucher'" class="kind-badge">秒杀券</em>
        </span>
        <span class="item-qty">x{{ line.quantity }}</span>
        <span class="item-amount">¥{{ money(line.amount) }}</span>
      </li>
    </ul>

    <div class="order-total">
      <span>合计</span>
      <b>¥{{ money(order.totalAmount) }}</b>
    </div>

    <!-- 待核销：二维码（商品单与券单一致） -->
    <div v-if="paid && order.verifyCode" class="order-qr">
      <QrCode :value="order.verifyCode" :size="200" />
      <p>核销码<em>{{ order.verifyCode }}</em></p>
      <button type="button" class="ghost-btn" @click="goVerify">
        <QrIcon :size="15" /> 商家核销（演示）
      </button>
    </div>

    <div class="order-actions">
      <template v-if="waitPay">
        <button type="button" class="ghost-btn" :disabled="busy" @click="cancel">取消订单</button>
        <button type="button" class="primary-btn" :disabled="busy" @click="pay">
          {{ busy ? '支付中...' : `立即支付 ¥${money(order.totalAmount)}` }}
        </button>
      </template>
      <template v-else-if="paid">
        <span class="hint">支付时间：{{ timeText(order.payTime) }}</span>
      </template>
      <template v-else-if="verified">
        <span class="hint">核销时间：{{ timeText(order.verifyTime) }}</span>
      </template>
      <router-link v-else class="primary-btn" to="/shops/1">去店里再买一单</router-link>
    </div>

    <p class="order-note">
      演示环境使用 Mock 支付通道：不会真的扣款。商品订单与秒杀券订单共用同一套支付/核销逻辑，真实支付接口已在后端
      <code>PayService</code> 预留。
    </p>
  </section>
</template>

<style scoped>
.order-top {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 4px;
  border-bottom: 1px solid var(--line, #e7e2d9);
  font-size: 13px;
}

.order-top .icon-button:last-child {
  margin-left: auto;
  font-size: 12px;
  color: var(--orange, #e8622c);
}

.order-card {
  max-width: 620px;
  margin: 26px auto 0;
  padding: 0 0 26px;
  background: #fff;
  border: 1px solid var(--line, #e7e2d9);
  border-radius: 10px;
  overflow: hidden;
}

.order-status {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 22px;
  background: #f6f1e7;
  color: #7a6a55;
}

.order-status.paid {
  background: #eaf7f0;
  color: #1f7a4d;
}

.order-status.closed {
  background: #f3f0ea;
  color: #8a857c;
}

.order-status strong {
  display: block;
  font-size: 16px;
}

.order-status span {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  opacity: .9;
}

.order-notice {
  margin: 14px 22px 0;
  padding: 9px 12px;
  border-radius: 6px;
  font-size: 12px;
}

.order-notice.success {
  background: #eaf7f0;
  color: #1f7a4d;
}

.order-notice.error {
  background: #fdeff1;
  color: #c02b3c;
}

.order-shop {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 18px 22px 6px;
}

.order-shop strong {
  font-size: 14px;
}

.order-shop small {
  margin-left: auto;
  color: #b0a89c;
  font-size: 10px;
}

.order-items {
  list-style: none;
  margin: 10px 22px 0;
  padding: 0;
  border-top: 1px dashed #ece5d9;
}

.order-items li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 0;
  border-bottom: 1px dashed #ece5d9;
  font-size: 13px;
}

.item-qty {
  color: #9a958b;
  font-size: 12px;
}

.item-amount {
  margin-left: auto;
  color: #5c574f;
}

.kind-badge {
  margin-left: 6px;
  padding: 1px 5px;
  border-radius: 3px;
  background: #ffeadc;
  color: #e8622c;
  font-size: 10px;
  font-style: normal;
}

.order-total {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 22px 0;
  color: #8a857c;
  font-size: 12px;
}

.order-total b {
  color: var(--orange, #e8622c);
  font-size: 20px;
}

.order-qr {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin: 18px 22px 0;
  padding: 18px;
  border: 1px dashed #e3d6c2;
  border-radius: 10px;
  background: #fffdf9;
}

.order-qr p {
  margin: 0;
  color: #8a857c;
  font-size: 11px;
}

.order-qr em {
  display: block;
  margin-top: 4px;
  color: #2f3d36;
  font-size: 15px;
  font-style: normal;
  letter-spacing: 2px;
  font-weight: 700;
}

.order-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 22px 0;
}

.order-actions .hint {
  color: #b0a89c;
  font-size: 11px;
}

.order-actions .primary-btn,
.order-actions .ghost-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border-radius: 24px;
  font-size: 13px;
}

.order-actions .primary-btn {
  margin-left: auto;
  background: var(--orange, #e8622c);
  color: #fff;
  font-weight: 700;
}

.order-actions .primary-btn:disabled {
  background: #d9d4cb;
  cursor: not-allowed;
}

.order-actions .ghost-btn,
.order-qr .ghost-btn {
  border: 1px solid var(--line, #e7e2d9);
  color: #6f6a62;
  background: #fff;
}

.order-note {
  margin: 22px 22px 0;
  padding-top: 14px;
  border-top: 1px dashed #ece5d9;
  color: #b0a89c;
  font-size: 10px;
  line-height: 1.7;
}

.order-note code {
  padding: 0 4px;
  border-radius: 3px;
  background: #f3f0ea;
  color: #7a6a55;
}
</style>
