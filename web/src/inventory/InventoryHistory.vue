<script setup lang="ts">
import { ref, watch } from 'vue';
import type { z } from 'zod';
import { request } from '../api';
import { pageSchema, receiptSchema, discrepancySchema, changeLabel } from './master-contracts';
import type { Receipt } from './master-contracts';
import { weightKg, localTime } from './contracts';
import { useCursorPage } from '../useCursorPage';
import CursorPager from '../components/CursorPager.vue';
import QueryFeedback from '../components/QueryFeedback.vue';
const props = defineProps<{ warehouseId: string; activeSnapshotId: string | null }>();
const kind = ref('snapshots');
const paging = useCursorPage<Receipt | z.infer<typeof discrepancySchema>>(async (cursor) => {
  if (!props.warehouseId) return { items: [], nextCursor: null };
  const params = new URLSearchParams({ warehouseId: props.warehouseId, limit: '20' });
  if (cursor) params.set(kind.value === 'snapshots' ? 'beforeVersion' : 'beforeId', cursor);
  return kind.value === 'snapshots'
    ? request(`/inventory/snapshots?${params}`, pageSchema(receiptSchema))
    : request(`/inventory/discrepancies?${params}`, pageSchema(discrepancySchema));
});
const { rows, busy, error, page, nextCursor } = paging;
watch([() => props.warehouseId, kind], paging.reset, { immediate: true });
</script>
<template>
  <section class="panel"><div class="section-heading"><h2>版本与差异记录</h2><el-radio-group v-model="kind" aria-label="历史类别">
    <el-radio-button value="snapshots">库存版本</el-radio-button><el-radio-button value="discrepancies">待核对差异</el-radio-button></el-radio-group></div>
    <QueryFeedback :busy="busy" :error="error" :count="rows.length" :empty="kind === 'snapshots' ? '尚未发布库存版本' : '暂无待核对差异'" @retry="paging.retry" />
    <el-table v-if="rows.length && kind === 'snapshots'" v-loading="busy" :data="rows">
      <el-table-column label="版本" width="145"><template #default="{ row }">V{{ row.version }} <el-tag v-if="row.id === activeSnapshotId" size="small">当前版本</el-tag></template></el-table-column>
      <el-table-column prop="rowCount" label="上游文件包数" width="130" /><el-table-column label="净重 kg" min-width="150"><template #default="{ row }">{{ weightKg(row.totalWeightGrams) }}</template></el-table-column>
      <el-table-column prop="mappingRevision" label="映射修订号" width="120" /><el-table-column label="发布时间" min-width="180"><template #default="{ row }">{{ localTime(row.publishedAt) }}</template></el-table-column>
    </el-table>
    <el-table v-else-if="rows.length" v-loading="busy" :data="rows">
      <el-table-column type="expand"><template #default="{ row }"><div class="raw-line">原值：{{ row.oldValues }}<br />新值：{{ row.newValues }}<br />新版本：{{ row.newSnapshotId }}</div></template></el-table-column>
      <el-table-column prop="barcode" label="条码" width="160" /><el-table-column label="差异原因" min-width="200"><template #default="{ row }">{{ changeLabel(row.type) }}</template></el-table-column>
      <el-table-column label="记录时间" min-width="180"><template #default="{ row }">{{ localTime(row.createdAt) }}</template></el-table-column>
    </el-table>
    <p v-if="kind === 'discrepancies'" class="muted">差异记录供核对，发布不会据此自动恢复库存或修改历史出库。</p>
    <CursorPager :page="page" :size="20" :busy="busy" :has-next="!!nextCursor" label="库存历史分页" @first="paging.first" @previous="paging.previous" @next="paging.next" />
  </section>
</template>
