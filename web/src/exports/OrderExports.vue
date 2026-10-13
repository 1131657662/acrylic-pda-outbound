<script setup lang="ts">
import { Download, Document, Refresh } from '@element-plus/icons-vue';
import { localTime } from '../orders/contracts';
import { basisLabel, formatLabels, statusLabels } from './contracts';
import { useOrderExports } from './useOrderExports';
const props = defineProps<{ actor: string; warehouse: string; order: string }>();
const { jobs, error, busy, loading, downloading, next, pending, load, create, download } = useOrderExports(props);
</script>
<template>
  <section class="order-exports" aria-label="原确认文件">
    <div class="export-toolbar">
      <el-button :icon="Document" :loading="busy" :disabled="!!pending || loading" @click="create('XLSX')">生成 Excel</el-button>
      <el-button :icon="Document" :loading="busy" :disabled="!!pending || loading" @click="create('SCAN_XLSX')">生成扫码记录 Excel</el-button>
      <el-button :icon="Document" :loading="busy" :disabled="!!pending || loading" @click="create('PDF')">生成 PDF</el-button>
      <el-tooltip content="刷新文件状态"><el-button :icon="Refresh" aria-label="刷新文件状态" :disabled="busy || loading" @click="load()" /></el-tooltip>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="pending && !busy" title="原导出请求的结果尚未确认" type="warning" :closable="false">
      <el-button :loading="busy" @click="create(pending.format)">重试原请求</el-button>
    </el-alert>
    <el-empty v-if="!jobs.length && !loading" description="暂无原确认文件" />
    <div v-loading="loading" class="export-list">
      <article v-for="job in jobs" :key="job.id" :data-export-id="job.id" class="export-row">
        <div class="export-facts">
          <strong>{{ formatLabels[job.format] }} · {{ basisLabel(job) }}</strong>
          <span>计划 V{{ job.revision }} · 原出库日 {{ job.originalDay }}</span>
          <span>申请于 {{ localTime(job.createdAt) }} · 保留至 {{ localTime(job.expiresAt) }}</span>
          <small v-if="job.status === 'FAILED'">错误编号：{{ job.errorCode }} · 尝试 {{ job.attempts }} 次</small>
        </div>
        <div class="export-actions">
          <el-tag :type="job.status === 'SUCCEEDED' ? 'success' : job.status === 'FAILED' ? 'danger' : 'info'">{{ statusLabels[job.status] }}</el-tag>
          <el-button v-if="job.downloadable" :icon="Download" :loading="downloading === job.id" :disabled="!!downloading && downloading !== job.id" @click="download(job)">下载</el-button>
          <el-button v-else-if="['FAILED', 'EXPIRED', 'INVALIDATED'].includes(job.status)" :icon="Refresh" :disabled="busy || !!pending" @click="create(job.format)">重新生成</el-button>
        </div>
      </article>
    </div>
    <el-button v-if="next" :loading="loading" @click="load(true)">更早的文件</el-button>
  </section>
</template>
<style scoped>
.export-toolbar, .export-actions { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.export-toolbar { margin-bottom: 16px; }
.export-row { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 20px 0; border-bottom: 1px solid var(--el-border-color-light); }
.export-facts { display: grid; gap: 8px; min-width: 0; overflow-wrap: anywhere; }
.export-facts span, .export-facts small { color: var(--el-text-color-secondary); font-size: 13px; }
.export-actions { flex-shrink: 0; }
.export-list { min-height: 40px; margin-bottom: 16px; }
@media (max-width: 680px) { .export-row { align-items: flex-start; flex-direction: column; } }
</style>
