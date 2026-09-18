<script setup lang="ts">
import { ArrowLeft } from '@element-plus/icons-vue';
withDefaults(defineProps<{ title: string; subtitle?: string; back?: boolean; backLabel?: string }>(), { backLabel: '返回订单列表' });
defineEmits<{ back: [] }>();
</script>
<template>
  <div class="page-title">
    <div class="heading-main">
      <el-tooltip v-if="back" :content="backLabel"><el-button class="icon-button" :icon="ArrowLeft" :aria-label="backLabel" @click="$emit('back')" /></el-tooltip>
      <div><h1>{{ title }}</h1><p v-if="subtitle" class="muted">{{ subtitle }}</p><slot name="status" /></div>
    </div>
    <div class="heading-actions"><slot /></div>
  </div>
</template>
