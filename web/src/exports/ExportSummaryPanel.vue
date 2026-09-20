<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { localTime } from '../orders/contracts';
import { exactKg, shanghaiToday, type ExportRange } from './summary-contracts';
import { useExportSummary } from './useExportSummary';
const props = defineProps<{ warehouse: string; disabled: boolean }>();
const emit = defineEmits<{ create: [format: 'XLSX' | 'PDF', range: ExportRange] }>();
const route = useRoute(); const router = useRouter(); const today = shanghaiToday();
const range = reactive({ fromDate: typeof route.query.fromDate === 'string' ? route.query.fromDate : today,
  throughDate: typeof route.query.throughDate === 'string' ? route.query.throughDate : today });
const { summary, loading, error, load } = useExportSummary(props.warehouse);
const changed = computed(() => !summary.value || range.fromDate !== summary.value.range.fromDate || range.throughDate !== summary.value.range.throughDate);
async function search(): Promise<void> {
  await router.replace({ query: { ...route.query, ...range } }); await load({ ...range });
}
watch(() => [route.query.fromDate, route.query.throughDate], () => {
  range.fromDate = typeof route.query.fromDate === 'string' ? route.query.fromDate : today;
  range.throughDate = typeof route.query.throughDate === 'string' ? route.query.throughDate : today;
});
</script>
<template>
  <section aria-label="跨订单原确认汇总" class="summary-section">
    <h2>跨订单原确认汇总</h2>
    <p class="muted">按北京时间原出库日查询，含正式订单首次确认结果，不含后续更正。每次最多31天、100单、5000包。</p>
    <form class="summary-filters" @submit.prevent="search">
      <label>开始日期<el-input v-model="range.fromDate" type="date" aria-label="开始日期" :disabled="loading || disabled" /></label>
      <label>结束日期<el-input v-model="range.throughDate" type="date" aria-label="结束日期" :disabled="loading || disabled" /></label>
      <el-button native-type="submit" :loading="loading" :disabled="disabled">查询汇总</el-button>
    </form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="summary">
      <p v-if="changed" role="status">日期已变更，请重新查询后导出。</p>
      <div class="summary-head" :class="{ muted: changed }">
        <strong>{{ summary.orderCount }} 单 · {{ summary.actualCount }} 包 · {{ exactKg(summary.weightGrams) }} kg</strong>
        <span>原确认 V1 · 查询于 {{ localTime(summary.readAt) }}</span>
      </div>
      <div class="summary-actions">
        <el-button :disabled="disabled || changed || loading" @click="emit('create', 'XLSX', summary.range)">导出汇总 Excel</el-button>
        <el-button :disabled="disabled || changed || loading || summary.actualCount > 1000" @click="emit('create', 'PDF', summary.range)">导出汇总 PDF</el-button>
      </div>
      <p class="muted">文件按申请时的完整范围固定成员和版本，可能与查询时不同；PDF最多1000包。扫码记录 Excel 仍从单订单下载。</p>
      <el-empty v-if="!summary.orderCount" description="该期间暂无已完成正式订单" />
      <template v-else>
        <h3>产品汇总</h3>
        <el-table :data="summary.products" max-height="360">
          <el-table-column prop="code" label="发货代码" min-width="100" />
          <el-table-column prop="model" label="原计划型号" min-width="200" />
          <el-table-column prop="name" label="品名" min-width="120" />
          <el-table-column prop="plannedCount" label="计划包数" align="right" min-width="100" />
          <el-table-column prop="actualCount" label="原确认包数" align="right" min-width="110" />
          <el-table-column label="原确认 kg" align="right" min-width="150"><template #default="{ row }">{{ exactKg(row.weightGrams) }}</template></el-table-column>
        </el-table>
        <details><summary>查看 {{ summary.orderCount }} 个原确认订单</summary>
          <ul class="summary-members"><li v-for="order in summary.orders" :key="order.id">
            <router-link :to="{ path: `/orders/${order.id}`, query: { warehouse, tab: 'exports' } }">{{ order.orderNo }}</router-link>
            <span>{{ order.vehicle }} · {{ order.originalDay }} · {{ order.actualCount }} 包 · {{ exactKg(order.weightGrams) }} kg</span>
          </li></ul>
        </details>
      </template>
    </template>
  </section>
</template>
<style scoped>
.summary-section { min-width: 0; border-bottom: 1px solid var(--el-border-color-light); padding-bottom: 24px; margin-bottom: 24px; }
h2 { font-size: 18px; } h3 { font-size: 15px; }
.summary-filters, .summary-actions, .summary-head { display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-end; margin: 16px 0; }
.summary-filters label { display: grid; gap: 8px; max-width: 100%; }
.summary-filters .el-input { width: 180px; max-width: 100%; }
.summary-head { justify-content: space-between; }
.summary-head span { color: var(--el-text-color-secondary); font-size: 13px; }
details { margin-top: 16px; } summary { cursor: pointer; }
.summary-members { padding-left: 20px; } .summary-members li { margin: 12px 0; overflow-wrap: anywhere; }
.summary-members span { display: block; color: var(--el-text-color-secondary); margin-top: 8px; }
</style>
