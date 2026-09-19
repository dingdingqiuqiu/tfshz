<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { ArrowLeft, LockKeyhole, Smartphone } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { useAppStore } from '../stores/app'
import { http } from '../services/http'

type ApiResult<T = unknown> = {
  success: boolean
  errorMsg?: string
  data?: T
}

const router = useRouter()
const app = useAppStore()
const mode = ref<'code' | 'password'>('code')
const phone = ref('')
const code = ref('')
const password = ref('')
const error = ref('')
const sending = ref(false)
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | undefined

function validPhone() {
  if (!/^1\d{10}$/.test(phone.value)) {
    error.value = '请输入正确的手机号'
    return false
  }
  return true
}

async function sendCode() {
  error.value = ''
  if (!validPhone() || sending.value) return

  sending.value = true
  try {
    const result = await http.post<ApiResult>('/user/code', undefined, {
      params: { phone: phone.value },
    }) as unknown as ApiResult

    if (!result.success) throw new Error(result.errorMsg || '验证码发送失败')

    countdown.value = 60
    countdownTimer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) {
        if (countdownTimer) clearInterval(countdownTimer)
        countdownTimer = undefined
        sending.value = false
      }
    }, 1000)
  } catch (cause) {
    sending.value = false
    error.value = cause instanceof Error ? cause.message : '验证码发送失败，请稍后重试'
  }
}

async function submit() {
  error.value = ''
  if (!validPhone()) return
  if (mode.value === 'password') {
    error.value = '当前后端登录接口仅支持短信验证码登录'
    return
  }
  if (!/^\d{6}$/.test(code.value)) {
    error.value = '请输入 6 位短信验证码'
    return
  }

  try {
    const result = await http.post<ApiResult<string>>('/user/login', {
      phone: phone.value,
      code: code.value,
    }) as unknown as ApiResult<string>

    if (!result.success || !result.data) throw new Error(result.errorMsg || '登录失败，请检查验证码')

    // 兼容开发服务器热更新期间仍缓存旧版 store 的情况。
    if (typeof app.setToken === 'function') {
      app.setToken(result.data)
    } else {
      sessionStorage.setItem('token', result.data)
    }
    await app.loadUser()
    router.push('/home')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败，请稍后重试'
  }
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>
<template>
  <div class="login-page"><button type="button" class="back-link" @click="router.back()">
      <ArrowLeft :size="18" />返回
    </button>
    <div class="login-panel">
      <div class="login-heading"><span class="brand-mark">听</span><span class="eyebrow">听风生活志</span>
        <h1>欢迎回来</h1>
        <p>登录后，收藏你的每一份城市灵感。</p>
      </div>
      <div class="login-tabs"><button type="button" :class="{ active: mode === 'code' }"
          @click="mode = 'code'">验证码登录</button><button type="button" :class="{ active: mode === 'password' }"
          @click="mode = 'password'">密码登录</button></div><label class="field">
        <Smartphone :size="18" /><input v-model="phone" inputmode="numeric" maxlength="11" placeholder="手机号" />
      </label><label v-if="mode === 'code'" class="field">
        <LockKeyhole :size="18" /><input v-model="code" inputmode="numeric" maxlength="6" placeholder="短信验证码" /><button
          type="button" class="send-code" :disabled="sending || countdown > 0" @click="sendCode">{{ countdown > 0 ? `${countdown}s 后重试` : '获取验证码' }}</button>
      </label><label v-else class="field">
        <LockKeyhole :size="18" /><input v-model="password" type="password" placeholder="登录密码" />
      </label>
      <p v-if="error" class="form-error">{{ error }}</p><button type="button" class="primary-button login-submit"
        @click="submit">登录 / 注册</button>
      <p class="login-note">登录即代表同意《用户协议》和《隐私政策》</p>
    </div>
  </div>
</template>
