<script setup lang="ts">
import { ref, watch } from 'vue';
import { request } from '../api';
import { pageSchema, changeSchema, mappingChangeSchema, changeLabel } from './master-contracts';
import type { Diff, MappingChange } from './master-contracts';
import type { z } from 'zod';
import { weightKg } from './contracts';
import { useCursorPage } from '../useCursorPage';
import CursorPager from '../components/CursorPager.vue';
import QueryFeedback from '../components/QueryFeedback.vue';
const props = defineProps<{ preview: Diff }>();
const kind = ref('changes');
const paging = useCursorPage<z.infer<typeof changeSchema> | MappingChange>(async (after) => {
  const params = new URLSearchParams({ warehouseId: props.preview.imported.warehouseId, after, limit: '20',
    expectedSnapshot: props.preview.baseline.activeSnapshotId ?? 'NONE', expectedMappingRevision: props.preview.baseline.mappingRevision });
  const path = `/inventory/imports/${props.preview.imported.id}/diff/${kind.value}?${params}`;
  return kind.value === 'changes' ? request(path, pageSchema(changeSchema)) : request(path, pageSchema(mappingChangeSchema));
});
const { rows, busy, error, page, nextCursor } = paging;
watch([() => props.preview, kind], paging.reset, { immediate: true });
</script>
<template>
  <el-radio-group v-model="kind" aria-label="差异类别"><el-radio-button value="changes">库存差异</el-radio-button><el-radio-button value="mappings">候选映射</el-radio-button></el-radio-group>
  <QueryFeedback :busy="busy" :error="error" :count="rows.length" :empty="kind === 'changes' ? '无库存差异' : '无候选映射变化'" @retry="paging.retry" />
  <el-table v-if="rows.length && kind === 'changes'" v-loading="busy" :data="rows">
    <el-table-column prop="barcode" label="条码" width="145" /><el-table-column label="变化" min-width="145"><template #default="{ row }">{{ changeLabel(row.type) }}</template></el-table-column>
    <el-table-column label="原型号 / kg" min-width="185"><template #default="{ row }">{{ row.before ? `${row.before.model} / ${weightKg(row.before.weightGrams)}` : '—' }}</template></el-table-column>
    <el-table-column label="新型号 / kg" min-width="185"><template #default="{ row }">{{ row.after ? `${row.after.model} / ${weightKg(row.after.weightGrams)}` : '—' }}</template></el-table-column>
  </el-table>
  <el-table v-else-if="rows.length" v-loading="busy" :data="rows">
    <el-table-column prop="deliveryCode" label="代码" width="75" />
    <el-table-column label="原映射" min-width="180"><template #default="{ row }">{{ row.previous ? `${row.previous.model} · ${row.previous.name}` : '首次建立' }}</template></el-table-column>
    <el-table-column label="候选映射" min-width="180"><template #default="{ row }">{{ row.model }} · {{ row.name }}</template></el-table-column>
    <el-table-column label="生效后状态" width="115"><template #default="{ row }">{{ row.enabled ? '启用' : '保持停用' }}</template></el-table-column>
  </el-table>
  <CursorPager :page="page" :size="20" :busy="busy" :has-next="!!nextCursor" label="库存差异分页" @first="paging.first" @previous="paging.previous" @next="paging.next" />
</template>
