<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import QueryFeedback from '../components/QueryFeedback.vue';
import CursorPager from '../components/CursorPager.vue';
import { useCursorPage } from '../useCursorPage';
import { useSessionStore } from '../session';
import { request, errorText } from '../api';
import { pageSchema } from '../inventory/master-contracts';
import { localTime } from '../orders/contracts';
import { policySchema, currentPolicySchema } from './contracts';
import type { Policy } from './contracts';
import PolicyEditor from './PolicyEditor.vue';
const session = useSessionStore();
const current = ref<Policy | null>(null); const currentError = ref(''); const loadingCurrent = ref(false);
const editing = ref(false); const tab = ref('current'); const success = ref(''); let generation = 0;
const canManage = computed(() => session.identity?.permissions.includes('policy:manage'));
const paging = useCursorPage(async (beforeVersion) => {
  if (!session.warehouseId) return { items: [], nextCursor: null };
  const params = new URLSearchParams({ warehouseId: session.warehouseId, limit: '20', beforeVersion });
  return request(`/policies?${params}`, pageSchema(policySchema));
});
const { rows: history, busy, error, page, nextCursor } = paging;
async function loadCurrent(): Promise<void> {
  const ticket = ++generation; currentError.value = ''; loadingCurrent.value = true;
  if (!session.warehouseId) { loadingCurrent.value = false; return; }
  try {
    const head = await request(`/policies/current?warehouseId=${session.warehouseId}`, currentPolicySchema);
    if (ticket === generation) current.value = head.policy;
  } catch (cause) { if (ticket === generation) currentError.value = errorText(cause); }
  finally { if (ticket === generation) loadingCurrent.value = false; }
}
function saved(): void {
  editing.value = false; success.value = '新规则已保存，已发布订单继续使用原规则版本。';
  void loadCurrent(); void paging.reset();
}
watch(() => session.warehouseId, () => {
  current.value = null; success.value = ''; editing.value = false;
  void loadCurrent(); void paging.reset();
}, { immediate: true });
</script>
<template>
  <WorkspaceLayout title="出库规则" :busy="editing">
    <div class="page-title"><h1>出库规则</h1>
      <el-button v-if="canManage" type="primary" :disabled="loadingCurrent || !!currentError || !session.warehouseId" @click="editing = true">设置规则</el-button></div>
    <el-alert v-if="success" :title="success" type="success" :closable="false" />
    <el-radio-group v-model="tab" class="auxiliary-tabs" aria-label="规则视图"><el-radio-button value="current">当前规则</el-radio-button><el-radio-button value="history">规则历史</el-radio-button></el-radio-group>
    <section v-show="tab === 'current'" class="panel">
      <QueryFeedback v-if="loadingCurrent || currentError" :busy="loadingCurrent" :error="currentError" :count="0" empty="" @retry="loadCurrent" />
      <template v-else>
        <template v-if="current"><h2>当前规则 V{{ current.version }}</h2><p>自动扣库存：<strong>{{ current.autoDeductInventory ? '开启' : '关闭' }}</strong></p></template>
        <el-alert v-else title="尚未设置：请管理员明确选择自动扣库存方式，才能发布订单。" type="warning" :closable="false" />
        <p class="muted">订单发布时固定规则版本，后续调整不改变已发布订单。</p>
        <p class="muted">离线授权 48 小时 · 现场分堆作业 · 短装须主管批准 · 扫码自动保存，手输校验后人工确认</p>
      </template>
    </section>
    <section v-show="tab === 'history'" class="panel">
      <QueryFeedback :busy="busy" :error="error" :count="history.length" empty="尚无历史规则" @retry="paging.retry" />
      <el-table v-if="history.length" :data="history" v-loading="busy"><el-table-column prop="version" label="版本" width="90" />
        <el-table-column label="自动扣库存" width="140"><template #default="{ row }">{{ row.autoDeductInventory ? '开启' : '关闭' }}</template></el-table-column>
        <el-table-column prop="reason" label="原因" min-width="220" /><el-table-column prop="publishedBy" label="操作人" width="140" />
        <el-table-column label="时间" width="190"><template #default="{ row }">{{ localTime(row.publishedAt) }}</template></el-table-column></el-table>
      <CursorPager :page="page" :size="20" :busy="busy" :has-next="!!nextCursor" label="规则历史分页" @first="paging.first" @previous="paging.previous" @next="paging.next" />
    </section>
    <PolicyEditor v-if="editing" :warehouse-id="session.warehouseId" :current="current" @saved="saved" @cancel="editing = false" />
  </WorkspaceLayout>
</template>
