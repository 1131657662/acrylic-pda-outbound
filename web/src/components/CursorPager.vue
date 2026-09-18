<script setup lang="ts">
defineProps<{ page: number; size: number; busy: boolean; hasNext: boolean; label?: string }>();
defineEmits<{ first: []; previous: []; next: [] }>();
</script>
<template>
  <nav class="pagination" :aria-label="label ?? '结果分页'">
    <span class="muted">第 {{ page }} 页 · 每页最多 {{ size }} 条</span>
    <el-button :disabled="busy || page === 1" @click="$emit('first')">第一页</el-button>
    <el-button :disabled="busy || page === 1" @click="$emit('previous')">上一页</el-button>
    <el-button :disabled="busy || !hasNext" @click="$emit('next')">下一页</el-button>
  </nav>
</template>
