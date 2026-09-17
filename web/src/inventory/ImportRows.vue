<script setup lang="ts">
import { ref, watch } from 'vue';
import { request, errorText } from '../api';
import { rowPageSchema, weightKg } from './contracts';
import type { ImportRow } from './contracts';

const props = defineProps<{ importId: string; warehouseId: string }>();
const rows = ref<ImportRow[]>([]);
const errorsOnly = ref(false);
const nextCursor = ref<string | null>(null);
const busy = ref(false);
const error = ref('');
let generation = 0;
async function load(afterLine = '0'): Promise<void> {
  const current = ++generation;
  busy.value = true; error.value = '';
  const params = new URLSearchParams({ warehouseId: props.warehouseId, afterLine, errorsOnly: String(errorsOnly.value), limit: '50' });
  try {
    const page = await request(`/inventory/imports/${props.importId}/rows?${params}`, rowPageSchema);
    if (current !== generation) return;
    rows.value = page.items; nextCursor.value = page.nextCursor;
  } catch (cause) { if (current === generation) error.value = errorText(cause); }
  finally { if (current === generation) busy.value = false; }
}
watch(() => [props.importId, props.warehouseId, errorsOnly.value], () => { rows.value = []; void load(); }, { immediate: true });
</script>

<template>
  <div class="section-heading"><h3>文件明细</h3><el-switch v-model="errorsOnly" active-text="仅看错误 / 重复" /></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <el-table v-loading="busy" :data="rows" empty-text="没有符合条件的明细" stripe max-height="440" row-key="lineNo">
    <el-table-column prop="lineNo" label="原行号" width="80" />
    <el-table-column prop="parsedFields.barcode" label="条码" width="145" />
    <el-table-column prop="parsedFields.deliveryCode" label="代码" width="70" />
    <el-table-column prop="parsedFields.model" label="型号" min-width="200" />
    <el-table-column prop="parsedFields.name" label="品名" min-width="120" />
    <el-table-column label="净重 kg" width="110" align="right">
      <template #default="{ row }">{{ row.parsedFields ? weightKg(row.parsedFields.weightGrams) : '—' }}</template>
    </el-table-column>
    <el-table-column label="预检说明" min-width="240"><template #default="{ row }">
      <span :class="row.errorCode ? 'text-error' : 'muted'">{{ row.errorDetail ?? '字段有效' }}</span>
    </template></el-table-column>
    <el-table-column type="expand"><template #default="{ row }"><pre class="raw-line">{{ row.rawText }}</pre></template></el-table-column>
  </el-table>
  <div class="pagination"><span class="muted">按原文件行号排序 · 每页 50 行</span>
    <el-button :disabled="busy" @click="load()">第一页</el-button>
    <el-button :disabled="busy || !nextCursor" @click="nextCursor && load(nextCursor)">下一页</el-button>
  </div>
</template>
