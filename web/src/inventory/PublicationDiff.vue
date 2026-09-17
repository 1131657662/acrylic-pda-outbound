<script setup lang="ts">
import { ref, watch } from 'vue';
import { request, errorText } from '../api';
import { pageSchema, changeSchema, mappingChangeSchema, changeLabel } from './master-contracts';
import type { Diff, MappingChange } from './master-contracts';
import type { z } from 'zod';
import { weightKg } from './contracts';
const props = defineProps<{ preview: Diff }>();
const kind = ref('changes');
const changes = ref<z.infer<typeof changeSchema>[]>([]);
const mappings = ref<MappingChange[]>([]);
const next = ref<string | null>(null);
const busy = ref(false);
const error = ref('');
let generation = 0;
async function load(after = ''): Promise<void> {
  const current = ++generation; busy.value = true; error.value = '';
  const params = new URLSearchParams({ warehouseId: props.preview.imported.warehouseId, after, limit: '20',
    expectedSnapshot: props.preview.baseline.activeSnapshotId ?? 'NONE', expectedMappingRevision: props.preview.baseline.mappingRevision });
  const path = `/inventory/imports/${props.preview.imported.id}/diff/${kind.value}?${params}`;
  try {
    if (kind.value === 'changes') {
      const page = await request(path, pageSchema(changeSchema));
      if (generation !== current) return;
      changes.value = page.items; next.value = page.nextCursor;
    } else {
      const page = await request(path, pageSchema(mappingChangeSchema));
      if (generation !== current) return;
      mappings.value = page.items; next.value = page.nextCursor;
    }
  } catch (cause) { if (generation === current) error.value = errorText(cause); }
  finally { if (generation === current) busy.value = false; }
}
watch([() => props.preview, kind], () => { changes.value = []; mappings.value = []; next.value = null; void load(); }, { immediate: true });
</script>
<template>
  <el-radio-group v-model="kind" aria-label="差异类别"><el-radio-button value="changes">库存差异</el-radio-button><el-radio-button value="mappings">候选映射</el-radio-button></el-radio-group>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <el-table v-if="kind === 'changes'" v-loading="busy" :data="changes" empty-text="无库存差异" max-height="360">
    <el-table-column prop="barcode" label="条码" width="145" /><el-table-column label="变化" min-width="145"><template #default="{ row }">{{ changeLabel(row.type) }}</template></el-table-column>
    <el-table-column label="原型号 / kg" min-width="185"><template #default="{ row }">{{ row.before ? `${row.before.model} / ${weightKg(row.before.weightGrams)}` : '—' }}</template></el-table-column>
    <el-table-column label="新型号 / kg" min-width="185"><template #default="{ row }">{{ row.after ? `${row.after.model} / ${weightKg(row.after.weightGrams)}` : '—' }}</template></el-table-column>
  </el-table>
  <el-table v-else v-loading="busy" :data="mappings" empty-text="无候选映射变化" max-height="360">
    <el-table-column prop="deliveryCode" label="代码" width="75" />
    <el-table-column label="原映射" min-width="180"><template #default="{ row }">{{ row.previous ? `${row.previous.model} · ${row.previous.name}` : '首次建立' }}</template></el-table-column>
    <el-table-column label="候选映射" min-width="180"><template #default="{ row }">{{ row.model }} · {{ row.name }}</template></el-table-column>
    <el-table-column label="生效后状态" width="115"><template #default="{ row }">{{ row.enabled ? '启用' : '保持停用' }}</template></el-table-column>
  </el-table>
  <div class="pagination"><span class="muted">每页 20 条</span><el-button :disabled="busy" @click="load()">差异第一页</el-button><el-button :disabled="busy || !next" @click="next && load(next)">差异下一页</el-button></div>
</template>
