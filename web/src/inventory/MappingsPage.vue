<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import { useSessionStore } from '../session';
import { request } from '../api';
import { pageSchema, mappingSchema } from './master-contracts';
import type { Mapping } from './master-contracts';
import MappingEditor from './MappingEditor.vue';
import MappingHistory from './MappingHistory.vue';
import CursorPager from '../components/CursorPager.vue';
import QueryFeedback from '../components/QueryFeedback.vue';
import { useCursorPage } from '../useCursorPage';
const session = useSessionStore();
const success = ref('');
const editor = ref(false); const selected = ref<Mapping | null>(null); const historyCode = ref(''); const historyKey = ref(0);
const canEdit = computed(() => session.identity?.permissions.includes('mapping:maintain'));
const paging = useCursorPage(async (afterCode) => {
  if (!session.warehouseId) return { items: [], nextCursor: null };
  const params = new URLSearchParams({ warehouseId: session.warehouseId, afterCode, limit: '50' });
  return request(`/delivery-mappings?${params}`, pageSchema(mappingSchema));
});
const { rows, busy, error, page, nextCursor } = paging;
function edit(item: Mapping | null): void { selected.value = item; editor.value = true; }
function saved(): void { editor.value = false; historyKey.value++; success.value = '发货代码与产品对应关系已保存'; void paging.reload(); }
watch(() => session.warehouseId, () => { editor.value = false; historyCode.value = ''; success.value = ''; void paging.reset(); }, { immediate: true });
</script>
<template>
  <WorkspaceLayout title="发货代码与产品" :busy="editor" list>
    <div class="page-title"><div><p class="eyebrow">INVENTORY / MAPPINGS</p><h1>发货代码与产品</h1><p class="muted">维护代码与产品的对应关系，每次变更保留版本和原因。</p></div>
      <el-button v-if="canEdit && session.warehouseId" type="primary" @click="edit(null)">新增映射</el-button></div>
    <el-alert v-if="success" :title="success" type="success" closable @close="success = ''" />
    <section class="panel auxiliary-list">
    <QueryFeedback :busy="busy" :error="error" :count="rows.length" empty="尚无发货代码与产品对应关系" @retry="paging.retry" />
    <div v-if="rows.length" class="auxiliary-table"><el-table v-loading="busy" :data="rows" height="100%">
      <el-table-column prop="deliveryCode" label="代码" width="90" /><el-table-column prop="model" label="型号" min-width="180" />
      <el-table-column prop="name" label="名称" min-width="150" /><el-table-column prop="version" label="版本" width="80" />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="160"><template #default="{ row }"><el-button v-if="canEdit" link type="primary" @click="edit(row)">修订</el-button><el-button link @click="historyCode = row.deliveryCode">历史</el-button></template></el-table-column>
    </el-table></div><CursorPager :page="page" :size="50" :busy="busy" :has-next="!!nextCursor" @first="paging.first" @previous="paging.previous" @next="paging.next" /></section>
    <el-drawer :model-value="!!historyCode" :title="`${historyCode} · 产品对应历史`" size="640px" class="auxiliary-drawer" @close="historyCode = ''">
      <MappingHistory v-if="historyCode" :key="historyKey" :code="historyCode" :warehouse-id="session.warehouseId" />
    </el-drawer>
    <MappingEditor v-if="editor" :warehouse-id="session.warehouseId" :item="selected" @saved="saved" @cancel="editor = false" />
  </WorkspaceLayout>
</template>
