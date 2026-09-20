<script setup lang="ts">
import { computed } from 'vue';
import type { DownloadProblem } from './contracts';
const props = defineProps<{ problem: DownloadProblem }>();
defineEmits<{ retry: [] }>();
const messages: Record<DownloadProblem, { title: string; detail: string }> = {
  unconfigured: { title: '下载入口尚未启用', detail: '当前站点还未配置已核验的安装渠道，请联系管理员。' },
  unpublished: { title: '尚无可下载版本', detail: '当前环境尚未发布安装包，请稍后重试。' },
  paused: { title: '已暂停分发', detail: '当前安装包暂停提供，请等待管理员恢复后重试。' },
  incompatible: { title: '当前版本暂不适合首次安装', detail: '此发布尚未开放首次安装，或与当前后台、Android 系统不兼容。请联系管理员核对。' },
  invalid: { title: '发布信息或安装包校验失败', detail: '已停止下载，请重试；仍失败时请联系管理员。' },
  network: { title: '无法获取下载信息', detail: '请检查网络后重试。校验期间中断的安装包不会交给系统安装。' },
};
const message = computed(() => messages[props.problem]);
</script>
<template>
  <section class="download-feedback" role="alert">
    <h2>{{ message.title }}</h2><p>{{ message.detail }}</p>
    <el-button @click="$emit('retry')">重新检查</el-button>
  </section>
</template>
