<script setup lang="ts">
import { ref, watch } from 'vue';
import { request, errorText } from '../api';
import { mappingSchema, pageSchema } from './master-contracts';
import type { Mapping } from './master-contracts';
import { localTime } from './contracts';
const props = defineProps<{ warehouseId: string; code: string }>();
const rows = ref<Mapping[]>([]); const next = ref<string | null>(null); const busy = ref(false); const error = ref('');
let generation = 0;
async function load(cursor = ''): Promise<void> {
  const current = ++generation; busy.value = true; error.value = '';
  const params = new URLSearchParams({ warehouseId: props.warehouseId, limit: '20' });
  if (cursor) params.set('beforeVersion', cursor);
  try {
    const page = await request(`/delivery-mappings/${props.code}/history?${params}`, pageSchema(mappingSchema));
    if (current === generation) { rows.value = page.items; next.value = page.nextCursor; }
  } catch (cause) { if (current === generation) error.value = errorText(cause); }
  finally { if (current === generation) busy.value = false; }
}
watch(() => [props.warehouseId, props.code], () => { rows.value = []; next.value = null; void load(); }, { immediate: true });
</script>
<template>
  <section class="panel"><h2>{{ code }} · 映射历史</h2><el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="busy" :data="rows" empty-text="暂无此代码的历史记录">
      <el-table-column prop="version" label="版本" width="80" /><el-table-column prop="model" label="型号" min-width="180" />
      <el-table-column prop="name" label="名称" min-width="140" /><el-table-column label="状态" width="110"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}{{ row.current ? ' · 当前' : '' }}</template></el-table-column>
      <el-table-column prop="reason" label="变更原因" min-width="180" /><el-table-column label="时间" min-width="180"><template #default="{ row }">{{ localTime(row.createdAt) }}</template></el-table-column>
    </el-table><div class="pagination"><span class="muted">每页 20 个版本</span><el-button :disabled="busy" @click="load()">历史第一页</el-button><el-button :disabled="busy || !next" @click="next && load(next)">历史下一页</el-button></div>
  </section>
</template>
