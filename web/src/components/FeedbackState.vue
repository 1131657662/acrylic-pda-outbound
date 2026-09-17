<script setup lang="ts">
import { Document, Warning } from '@element-plus/icons-vue';
defineProps<{ title: string; description?: string; error?: boolean; loading?: boolean; action?: string }>();
defineEmits<{ action: [] }>();
</script>
<template>
  <div v-loading="loading" class="feedback-state" :role="error ? 'alert' : 'status'" :aria-busy="loading">
    <el-icon v-if="!loading"><Warning v-if="error" /><Document v-else /></el-icon>
    <h2>{{ title }}</h2><p v-if="description" class="muted">{{ description }}</p>
    <el-button v-if="action" :disabled="loading" @click="$emit('action')">{{ action }}</el-button>
  </div>
</template>
