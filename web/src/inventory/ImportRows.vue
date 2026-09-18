<script setup lang="ts">
import { ref, watch } from 'vue';
import { request } from '../api';
import { rowPageSchema, weightKg } from './contracts';
import { useCursorPage } from '../useCursorPage';
import CursorPager from '../components/CursorPager.vue';
import QueryFeedback from '../components/QueryFeedback.vue';

const props = defineProps<{ importId: string; warehouseId: string }>();
const errorsOnly = ref(false);
const paging = useCursorPage(async (cursor) => {
  const params = new URLSearchParams({ warehouseId: props.warehouseId, afterLine: cursor || '0', errorsOnly: String(errorsOnly.value), limit: '50' });
  return request(`/inventory/imports/${props.importId}/rows?${params}`, rowPageSchema);
});
const { rows, busy, error, page, nextCursor } = paging;
watch(() => [props.importId, props.warehouseId, errorsOnly.value], paging.reset, { immediate: true });
</script>

<template>
  <div class="section-heading"><h3>文件明细</h3><el-switch v-model="errorsOnly" active-text="仅看错误 / 重复" /></div>
  <QueryFeedback :busy="busy" :error="error" :count="rows.length" empty="没有符合条件的明细" @retry="paging.retry" />
  <el-table v-if="rows.length" v-loading="busy" :data="rows" stripe row-key="lineNo">
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
  <CursorPager :page="page" :size="50" :busy="busy" :has-next="!!nextCursor" label="文件明细分页" @first="paging.first" @previous="paging.previous" @next="paging.next" />
</template>
