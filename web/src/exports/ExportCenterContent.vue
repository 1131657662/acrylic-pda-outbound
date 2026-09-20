<script setup lang="ts">
import { Download, Refresh } from '@element-plus/icons-vue';
import { localTime } from '../orders/contracts';
import { formatLabels, statusLabels } from './contracts';
import { useCenterJobs } from './useCenterJobs';
import { useCenterRequests } from './useCenterRequests';
import ExportSummaryPanel from './ExportSummaryPanel.vue';
const props = defineProps<{ actor: string; warehouse: string }>();
const { jobs, next, loading, loaded, error, downloading, format, status, load, accepted, download } = useCenterJobs(props.warehouse);
const { busy, pending, error: actionError, create, retry, resume } = useCenterRequests(props, accepted);
</script>
<template>
  <el-alert v-if="actionError" :title="actionError" type="error" :closable="false" />
  <el-alert v-if="pending && !busy" title="原导出申请尚未确认，刷新或重新登录后仍可核对原请求。" type="warning" :closable="false">
    <p v-if="pending.action.kind === 'range'">{{ pending.action.range.fromDate }} 至 {{ pending.action.range.throughDate }} · {{ formatLabels[pending.action.format] }}</p>
    <el-button :loading="busy" @click="resume">重试原申请</el-button>
  </el-alert>
  <ExportSummaryPanel :warehouse="warehouse" :disabled="busy || !!pending" @create="create" />
  <section aria-label="我的导出任务">
    <h2>我的导出任务</h2>
    <p class="muted">当前仓库内本人申请的单订单和跨订单文件，按申请时间从新到旧。下载有效期7天。</p>
    <form class="center-filters" @submit.prevent="load()">
      <label>文件格式<el-select v-model="format" aria-label="文件格式" :disabled="loading"><el-option value="" label="全部格式" /><el-option v-for="(label, value) in formatLabels" :key="value" :value="value" :label="label" /></el-select></label>
      <label>任务状态<el-select v-model="status" aria-label="任务状态" :disabled="loading"><el-option value="" label="全部状态" /><el-option v-for="(label, value) in statusLabels" :key="value" :value="value" :label="label" /></el-select></label>
      <el-button native-type="submit" :icon="Refresh" :loading="loading">查询任务</el-button>
    </form>
    <el-alert v-if="error" :title="loaded ? `${error} 下方为上次读取结果。` : error" type="error" :closable="false" />
    <el-empty v-if="loaded && !loading && !jobs.length" description="暂无匹配的导出任务" />
    <div v-loading="loading" class="center-jobs">
      <article v-for="job in jobs" :key="job.id" class="center-job" :data-export-id="job.id">
        <div class="job-description">
          <strong>{{ job.orderNo }} · {{ formatLabels[job.format] }} · 原确认 V1</strong>
          <span v-if="job.range">{{ job.range.fromDate }} 至 {{ job.range.throughDate }} · 固定 {{ job.orderCount }} 单</span>
          <span v-else>原出库日 {{ job.originalDay }} · 计划 V{{ job.revision }}</span>
          <span>申请于 {{ localTime(job.createdAt) }} · 保留至 {{ localTime(job.expiresAt) }}</span>
          <small v-if="job.retryOf">由原快照重新生成</small>
          <small v-if="job.errorCode">错误编号：{{ job.errorCode }} · 尝试 {{ job.attempts }} 次</small>
          <small v-if="job.status === 'INVALIDATED'">恢复代次已变化，请重新查询汇总或进入原订单生成。</small>
          <router-link v-if="job.orderId" :to="{ path: `/orders/${job.orderId}`, query: { warehouse, tab: 'exports' } }">查看原订单</router-link>
        </div>
        <div class="job-actions">
          <el-tag :type="job.status === 'SUCCEEDED' ? 'success' : job.status === 'FAILED' ? 'danger' : 'info'">{{ statusLabels[job.status] }}</el-tag>
          <el-button v-if="job.downloadable" :icon="Download" :loading="downloading === job.id" :disabled="!!downloading && downloading !== job.id" @click="download(job)">下载</el-button>
          <el-button v-if="['FAILED', 'EXPIRED', 'SUCCEEDED'].includes(job.status)" :disabled="busy || !!pending" @click="retry(job)">按原快照重生成</el-button>
        </div>
      </article>
    </div>
    <div class="center-pagination"><span>已显示 {{ jobs.length }} 项</span><el-button v-if="next" :loading="loading" :disabled="!!error" @click="load(true)">加载更早任务</el-button></div>
  </section>
</template>
<style scoped>
h2 { font-size: 18px; }
.center-filters { display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-end; margin: 16px 0; }
.center-filters label { display: grid; gap: 8px; max-width: 100%; } .center-filters .el-select { width: 180px; max-width: 100%; }
.center-jobs { min-height: 48px; }
.center-job { display: flex; justify-content: space-between; gap: 20px; padding: 20px 0; border-bottom: 1px solid var(--el-border-color-light); }
.job-description { display: grid; gap: 8px; min-width: 0; overflow-wrap: anywhere; }
.job-description span, small { color: var(--el-text-color-secondary); font-size: 13px; }
.job-actions { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; flex-shrink: 0; }
.center-pagination { display: flex; gap: 16px; align-items: center; justify-content: space-between; margin-top: 16px; }
@media (max-width: 900px) { .center-job { flex-direction: column; } }
</style>
