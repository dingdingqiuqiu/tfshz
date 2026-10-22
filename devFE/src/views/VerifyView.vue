<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ArrowLeft, CheckCircle2, ScanLine } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import QrCode from '../components/QrCode.vue'
import { api } from '../services/api'
import { useAppStore } from '../stores/app'
import type { ProductOrder } from '../types/product'

const route = useRoute()
const router = useRouter()
const app = useAppStore()

const code = ref(String(route.query.code || ''))
const result = ref<ProductOrder | null>(null)
const error = ref('')
const busy = ref(false)

onMounted(() => {
  // 从订单页跳过来时带上核销码，直接核销一次，模拟“商家扫码”
  if (!app.loggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (code.value) verify()
})

async function verify() {
  const value = code.value.trim().toUpperCase()
  if (!value || busy.value) return
  busy.value = true
  error.value = ''
  result.value = null
  try {
    result.value = await api.verifyOrder(value)
  } catch (cause) {
    error.value = cause instanceof Error && cause.message ? cause.message : '核销失败'
  } finally {
    busy.value = false
  }
}

const money = (cents?: number) => ((cents || 0) / 100).toFixed(2)
</script>

<template>
  <div class="verify-top">
    <button type="button" class="icon-button" title="返回" @click="router.back()"><ArrowLeft :size="20" /></button>
    <span>核销台（商家）</span>
  </div>

  <section class="verify-card">
    <div class="verify-head">
      <ScanLine :size="26" />
      <div>
        <strong>扫码 / 输入核销码</strong>
        <span>顾客到店后出示订单二维码，商家在这里核销</span>
      </div>
    </div>

    <div class="verify-input">
      <input
        v-model="code"
        maxlength="16"
        placeholder="输入 10 位核销码，例如 WHWC3ALUUG"
        @keyup.enter="verify"
      />
      <button type="button" :disabled="busy || !code.trim()" @click="verify">
        {{ busy ? '核销中...' : '核销' }}
      </button>
    </div>

    <p v-if="error" class="verify-error">{{ error }}</p>

    <div v-if="result" class="verify-result">
      <p class="ok"><CheckCircle2 :size="18" /> 核销成功</p>
      <div class="verify-row"><span>店铺</span><b>{{ result.shopName }}</b></div>
      <div class="verify-row"><span>订单号</span><b>{{ result.orderId }}</b></div>
      <div class="verify-row"><span>金额</span><b>¥{{ money(result.totalAmount) }}</b></div>
      <div class="verify-row"><span>状态</span><b>{{ result.statusText }}</b></div>
      <ul class="verify-items">
        <li v-for="item in result.items" :key="item.productName">
          {{ item.productName }} x{{ item.quantity }}
        </li>
      </ul>
      <QrCode :value="result.verifyCode || ''" :size="120" />
    </div>

    <p class="verify-note">
      同一个核销码只能核销一次：后端用「版本号 CAS + 状态机」保证并发扫码时也只有一次成功。
    </p>
  </section>
</template>

<style scoped>
.verify-top {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 4px;
  border-bottom: 1px solid var(--line, #e7e2d9);
  font-size: 13px;
}

.verify-card {
  max-width: 560px;
  margin: 26px auto 0;
  padding: 22px;
  background: #fff;
  border: 1px solid var(--line, #e7e2d9);
  border-radius: 10px;
}

.verify-head {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #2f3d36;
}

.verify-head strong {
  display: block;
  font-size: 15px;
}

.verify-head span {
  display: block;
  margin-top: 4px;
  color: #9a958b;
  font-size: 11px;
}

.verify-input {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

.verify-input input {
  flex: 1;
  padding: 11px 14px;
  border: 1px solid var(--line, #e7e2d9);
  border-radius: 8px;
  font-size: 14px;
  letter-spacing: 1px;
  text-transform: uppercase;
}

.verify-input button {
  padding: 0 22px;
  border-radius: 8px;
  background: var(--orange, #e8622c);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.verify-input button:disabled {
  background: #d9d4cb;
  cursor: not-allowed;
}

.verify-error {
  margin: 14px 0 0;
  padding: 9px 12px;
  border-radius: 6px;
  background: #fdeff1;
  color: #c02b3c;
  font-size: 12px;
}

.verify-result {
  margin-top: 18px;
  padding: 18px;
  border: 1px solid #d6ecdf;
  border-radius: 10px;
  background: #f4fbf7;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.verify-result .ok {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 4px;
  color: #1f7a4d;
  font-size: 13px;
  font-weight: 700;
}

.verify-row {
  display: flex;
  gap: 10px;
  font-size: 12px;
}

.verify-row span {
  color: #8a857c;
  min-width: 48px;
}

.verify-items {
  margin: 4px 0 0;
  padding-left: 16px;
  color: #5c574f;
  font-size: 12px;
}

.verify-note {
  margin: 18px 0 0;
  color: #b0a89c;
  font-size: 10px;
  line-height: 1.7;
}
</style>
