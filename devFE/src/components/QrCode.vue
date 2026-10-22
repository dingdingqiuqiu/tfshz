<script setup lang="ts">
import { computed } from 'vue'
import qrcode from '../vendor/qrcode-generator.js'

const props = withDefaults(defineProps<{ value: string; size?: number }>(), {
  size: 190,
})

/** 直接把核销码画成二维码（纯前端生成，不依赖任何外部图片接口） */
const svg = computed(() => {
  const text = (props.value || '').trim()
  if (!text) return ''
  try {
    const qr = qrcode(0, 'M')
    qr.addData(text)
    qr.make()
    return qr.createSvgTag({ cellSize: 4, margin: 2, scalable: true })
  } catch {
    return ''
  }
})
</script>

<template>
  <div class="qr-code" :style="{ width: `${size}px`, height: `${size}px` }" v-html="svg" />
</template>

<style scoped>
.qr-code {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px;
  background: #fff;
  border-radius: 8px;
  box-sizing: content-box;
}

.qr-code :deep(svg) {
  width: 100%;
  height: 100%;
}
</style>
