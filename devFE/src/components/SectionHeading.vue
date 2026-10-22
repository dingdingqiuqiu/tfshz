<script setup lang="ts">
defineProps<{ eyebrow?: string; title: string; action?: string }>()
// 下面这种写法Vue 3.3.4会报错，提示“Cannot find name 'defineEmits'”，所以改成了下面的写法
// defineEmits<{ action: [] }>()
const emit = defineEmits<{
  (e: 'action'): void
}>()
</script>

<template>
  <div class="section-heading">
    <div><span v-if="eyebrow" class="eyebrow">{{ eyebrow }}</span><h2>{{ title }}</h2></div>
    <button v-if="action" type="button" class="text-button" @click="$emit('action')">{{ action }} <span>→</span></button>
  </div>
</template>
