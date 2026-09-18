<script setup lang="ts">
import FeedbackState from './FeedbackState.vue';
defineProps<{ busy: boolean; error: string; count: number; empty: string }>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <div v-if="error" class="query-error" role="alert">
    <el-alert :title="error" :description="count ? '当前仍显示上次查询结果。' : undefined" type="error" :closable="false" show-icon />
    <el-button :loading="busy" @click="$emit('retry')">重试查询</el-button>
  </div>
  <FeedbackState v-else-if="!count" :loading="busy" :title="busy ? '正在查询' : empty" />
</template>
