<script setup lang="ts">
import { computed } from 'vue';
import { Connection, Warning } from '@element-plus/icons-vue';
import { connection } from '../connection';
import { localTime } from '../orders/contracts';
const label = computed(() => ({ unknown: '连接状态待核对', ok: '最近连接正常', unreachable: '后台暂时无法连接', expired: '请重新登录' })[connection.state]);
</script>
<template>
  <el-popover trigger="click" width="280">
    <template #reference><el-button text class="connection-trigger" :class="{ 'is-warning': connection.state !== 'ok' }" :aria-label="label">
      <el-icon><Connection v-if="connection.state === 'ok'" /><Warning v-else /></el-icon>
      <span v-if="connection.state !== 'ok'" class="connection-text">{{ label }}</span>
    </el-button></template>
    <p>{{ label }}</p><p class="muted">最近成功请求：{{ connection.lastSuccess ? localTime(connection.lastSuccess) : '暂无记录' }}</p>
    <p v-if="connection.serviceMode && connection.serviceMode !== 'NORMAL'" class="muted">后台服务模式：{{ connection.serviceMode }}</p>
  </el-popover>
</template>
