<script setup lang="ts">
defineProps<{ eyebrow?: string; title: string; action?: string }>()
defineEmits<{ action: [] }>()
</script>

<template>
  <div class="section-heading">
    <div><span v-if="eyebrow" class="eyebrow">{{ eyebrow }}</span><h2>{{ title }}</h2></div>
    <button v-if="action" type="button" class="text-button" @click="$emit('action')">{{ action }} <span>→</span></button>
  </div>
</template>
