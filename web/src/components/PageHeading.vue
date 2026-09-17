<script setup lang="ts">
import { ArrowLeft } from '@element-plus/icons-vue';
defineProps<{ title: string; subtitle?: string; back?: boolean }>();
defineEmits<{ back: [] }>();
</script>
<template>
  <div class="page-title">
    <div class="heading-main">
      <el-tooltip v-if="back" content="返回订单列表"><el-button class="icon-button" :icon="ArrowLeft" aria-label="返回订单列表" @click="$emit('back')" /></el-tooltip>
      <div><h1>{{ title }}</h1><p v-if="subtitle" class="muted">{{ subtitle }}</p><slot name="status" /></div>
    </div>
    <div class="heading-actions"><slot /></div>
  </div>
</template>
