<script setup lang="ts">
import { ref, watch } from 'vue';
import type { z } from 'zod';
import { request, errorText } from '../api';
import { pageSchema, receiptSchema, discrepancySchema, changeLabel } from './master-contracts';
import type { Receipt } from './master-contracts';
import { weightKg, localTime } from './contracts';
const props = defineProps<{ warehouseId: string; activeSnapshotId: string | null }>();
const kind = ref('snapshots'); const versions = ref<Receipt[]>([]); const discrepancies = ref<z.infer<typeof discrepancySchema>[]>([]);
const next = ref<string | null>(null); const busy = ref(false); const error = ref(''); let generation = 0;
async function load(cursor = ''): Promise<void> {
  const current = ++generation; if (!props.warehouseId) return;
  busy.value = true; error.value = '';
  const params = new URLSearchParams({ warehouseId: props.warehouseId, limit: '20' });
  if (cursor) params.set(kind.value === 'snapshots' ? 'beforeVersion' : 'beforeId', cursor);
  try {
    if (kind.value === 'snapshots') {
      const page = await request(`/inventory/snapshots?${params}`, pageSchema(receiptSchema));
      if (current === generation) { versions.value = page.items; next.value = page.nextCursor; }
    } else {
      const page = await request(`/inventory/discrepancies?${params}`, pageSchema(discrepancySchema));
      if (current === generation) { discrepancies.value = page.items; next.value = page.nextCursor; }
    }
  } catch (cause) { if (current === generation) error.value = errorText(cause); }
  finally { if (current === generation) busy.value = false; }
}
watch([() => props.warehouseId, kind], () => { versions.value = []; discrepancies.value = []; next.value = null; void load(); }, { immediate: true });
</script>
<template>
  <section class="panel"><div class="section-heading"><h2>版本与差异记录</h2><el-radio-group v-model="kind" aria-label="历史类别">
    <el-radio-button value="snapshots">库存版本</el-radio-button><el-radio-button value="discrepancies">待核对差异</el-radio-button></el-radio-group></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-if="kind === 'snapshots'" v-loading="busy" :data="versions" empty-text="尚未发布库存版本">
      <el-table-column label="版本" width="145"><template #default="{ row }">V{{ row.version }} <el-tag v-if="row.id === activeSnapshotId" size="small">当前版本</el-tag></template></el-table-column>
      <el-table-column prop="rowCount" label="上游文件包数" width="130" /><el-table-column label="净重 kg" min-width="150"><template #default="{ row }">{{ weightKg(row.totalWeightGrams) }}</template></el-table-column>
      <el-table-column prop="mappingRevision" label="映射修订号" width="120" /><el-table-column label="发布时间" min-width="180"><template #default="{ row }">{{ localTime(row.publishedAt) }}</template></el-table-column>
    </el-table>
    <el-table v-else v-loading="busy" :data="discrepancies" empty-text="暂无待核对差异">
      <el-table-column type="expand"><template #default="{ row }"><div class="raw-line">原值：{{ row.oldValues }}<br />新值：{{ row.newValues }}<br />新版本：{{ row.newSnapshotId }}</div></template></el-table-column>
      <el-table-column prop="barcode" label="条码" width="160" /><el-table-column label="差异原因" min-width="200"><template #default="{ row }">{{ changeLabel(row.type) }}</template></el-table-column>
      <el-table-column label="记录时间" min-width="180"><template #default="{ row }">{{ localTime(row.createdAt) }}</template></el-table-column>
    </el-table>
    <p v-if="kind === 'discrepancies'" class="muted">差异记录供核对，发布不会据此自动恢复库存或修改历史出库。</p>
    <div class="pagination"><span class="muted">每页 20 条</span><el-button :disabled="busy" @click="load()">历史第一页</el-button><el-button :disabled="busy || !next" @click="next && load(next)">历史下一页</el-button></div>
  </section>
</template>
