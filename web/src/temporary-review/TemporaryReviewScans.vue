<script setup lang="ts">
import { watch } from 'vue';
import { request } from '../api';
import { useCursorPage } from '../useCursorPage';
import { pageSchema } from '../inventory/master-contracts';
import { kilograms } from '../orders/submission-contracts';
import { temporaryScanSchema, issueLabel } from './contracts';
const props = defineProps<{ submissionId: string; warehouseId: string }>();
const pages = useCursorPage(cursor => request(`/temporary-reviews/${props.submissionId}/scans?${new URLSearchParams({
  warehouseId: props.warehouseId, cursor, limit: '20',
})}`, pageSchema(temporaryScanSchema)));
const { rows, busy, error, hasPrevious, nextCursor } = pages;
watch(() => [props.submissionId, props.warehouseId], () => { void pages.reset(); }, { immediate: true });
</script>
<template>
  <section class="panel">
    <h2>原装车记录</h2><p class="muted">按原扫码序号分页；本地撤销记录不计入有效包数。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button text @click="pages.retry">重试查询</el-button></el-alert>
    <el-table v-if="!error" v-loading="busy" :data="rows" row-key="captureId">
      <el-table-column prop="orderScanSeq" label="原序号" width="90" />
      <el-table-column prop="barcode" label="条码" min-width="170" /><el-table-column prop="deliveryCode" label="发货代码" width="100" />
      <el-table-column label="净重 kg" width="120" align="right"><template #default="{ row }">{{ kilograms(row.weightGrams) }}</template></el-table-column>
      <el-table-column prop="expectedDispatchCycle" label="原出库周期" width="115" />
      <el-table-column label="记录状态" width="120"><template #default="{ row }">{{ row.status === 'ACTIVE' ? '有效装车' : '本地已撤销' }}</template></el-table-column>
      <el-table-column label="当前库存核对" min-width="220"><template #default="{ row }">{{ row.currentIssue ? issueLabel(row.currentIssue) : row.status === 'ACTIVE' ? '当前未检出库存冲突，仍待独立确认' : '不计入有效装车' }}</template></el-table-column>
    </el-table>
    <nav class="order-pagination" aria-label="临时装车记录分页"><span>每页最多 20 条</span><div>
      <el-button :disabled="busy || !hasPrevious" @click="pages.previous">上一页</el-button>
      <el-button :disabled="busy || !!error || !nextCursor" @click="pages.next">下一页</el-button>
    </div></nav>
  </section>
</template>
