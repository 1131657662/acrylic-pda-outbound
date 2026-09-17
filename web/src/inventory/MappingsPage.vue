<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import { useSessionStore } from '../session';
import { request, errorText } from '../api';
import { pageSchema, mappingSchema } from './master-contracts';
import type { Mapping } from './master-contracts';
import MappingEditor from './MappingEditor.vue';
import MappingHistory from './MappingHistory.vue';
const session = useSessionStore();
const rows = ref<Mapping[]>([]); const next = ref<string | null>(null); const busy = ref(false); const error = ref('');
const editor = ref(false); const selected = ref<Mapping | null>(null); const historyCode = ref(''); const historyKey = ref(0);
const canEdit = computed(() => session.identity?.permissions.includes('mapping:maintain'));
let generation = 0;
async function load(afterCode = ''): Promise<void> {
  const current = ++generation; if (!session.warehouseId) return;
  busy.value = true; error.value = '';
  try {
    const params = new URLSearchParams({ warehouseId: session.warehouseId, afterCode, limit: '50' });
    const page = await request(`/delivery-mappings?${params}`, pageSchema(mappingSchema));
    if (current === generation) { rows.value = page.items; next.value = page.nextCursor; }
  } catch (cause) { if (current === generation) error.value = errorText(cause); }
  finally { if (current === generation) busy.value = false; }
}
function edit(item: Mapping | null): void { selected.value = item; editor.value = true; }
function saved(): void { editor.value = false; historyKey.value++; void load(); }
watch(() => session.warehouseId, () => { rows.value = []; next.value = null; editor.value = false; historyCode.value = ''; void load(); }, { immediate: true });
</script>
<template>
  <WorkspaceLayout title="发货代码映射" :busy="editor">
    <div class="page-title"><div><p class="eyebrow">INVENTORY / MAPPINGS</p><h1>发货代码映射</h1><p class="muted">维护代码与产品的对应关系，每次变更保留版本和原因。</p></div>
      <el-button v-if="canEdit && session.warehouseId" type="primary" @click="edit(null)">新增映射</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <section class="panel"><el-table v-loading="busy" :data="rows" empty-text="暂无映射，可在库存发布时确认建立">
      <el-table-column prop="deliveryCode" label="代码" width="90" /><el-table-column prop="model" label="型号" min-width="180" />
      <el-table-column prop="name" label="名称" min-width="150" /><el-table-column prop="version" label="版本" width="80" />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="160"><template #default="{ row }"><el-button v-if="canEdit" link type="primary" @click="edit(row)">修订</el-button><el-button link @click="historyCode = row.deliveryCode">历史</el-button></template></el-table-column>
    </el-table><div class="pagination"><span class="muted">每页 50 条</span><el-button :disabled="busy" @click="load()">第一页</el-button><el-button :disabled="busy || !next" @click="next && load(next)">下一页</el-button></div></section>
    <MappingHistory v-if="historyCode" :key="historyKey" :code="historyCode" :warehouse-id="session.warehouseId" />
    <MappingEditor v-if="editor" :warehouse-id="session.warehouseId" :item="selected" @saved="saved" @cancel="editor = false" />
  </WorkspaceLayout>
</template>
