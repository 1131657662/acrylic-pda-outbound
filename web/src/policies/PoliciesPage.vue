<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import { useSessionStore } from '../session';
import { request, errorText } from '../api';
import { pageSchema } from '../inventory/master-contracts';
import { localTime } from '../orders/contracts';
import { policySchema, currentPolicySchema } from './contracts';
import type { Policy } from './contracts';
import PolicyEditor from './PolicyEditor.vue';
const session = useSessionStore();
const current = ref<Policy | null>(null); const history = ref<Policy[]>([]); const next = ref<string | null>(null);
const busy = ref(false); const error = ref(''); const editing = ref(false); let generation = 0;
const canManage = computed(() => session.identity?.permissions.includes('policy:manage'));
async function load(before = ''): Promise<void> {
  if (!session.warehouseId) return;
  const ticket = ++generation; busy.value = true; error.value = '';
  try {
    const params = new URLSearchParams({ warehouseId: session.warehouseId, limit: '20' });
    if (before) params.set('beforeVersion', before);
    const [head, page] = await Promise.all([request(`/policies/current?${params}`, currentPolicySchema), request(`/policies?${params}`, pageSchema(policySchema))]);
    if (ticket === generation) { current.value = head.policy; history.value = before ? [...history.value, ...page.items] : page.items; next.value = page.nextCursor; }
  } catch (cause) { if (ticket === generation) error.value = errorText(cause); }
  finally { if (ticket === generation) busy.value = false; }
}
function saved(): void { editing.value = false; void load(); }
watch(() => session.warehouseId, () => { current.value = null; history.value = []; next.value = null; void load(); }, { immediate: true });
</script>
<template>
  <WorkspaceLayout title="出库政策" :busy="editing || busy">
    <div class="page-title"><div><p class="eyebrow">WAREHOUSE / POLICY</p><h1>出库政策</h1><p class="muted">订单发布时固定政策版本，后续调整保留历史。</p></div>
      <el-button v-if="canManage" type="primary" :disabled="busy || !!error" @click="editing = true">设置政策</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <section v-loading="busy" class="panel">
      <template v-if="current"><h2>当前政策 V{{ current.version }}</h2><p>自动扣库存：<strong>{{ current.autoDeductInventory ? '开启' : '关闭' }}</strong></p></template>
      <el-alert v-else title="尚未设置：请管理员明确选择自动扣库存方式，才能发布订单。" type="warning" :closable="false" />
      <p class="muted">离线授权 48 小时 · 现场分堆作业 · 短装须主管批准 · 扫码自动保存，手输校验后人工确认</p>
    </section>
    <section class="panel"><h2>政策历史</h2><el-table :data="history"><el-table-column prop="version" label="版本" width="90" />
      <el-table-column label="自动扣库存" width="140"><template #default="{ row }">{{ row.autoDeductInventory ? '开启' : '关闭' }}</template></el-table-column>
      <el-table-column prop="reason" label="原因" min-width="220" /><el-table-column prop="publishedBy" label="操作人" width="140" />
      <el-table-column label="时间" width="190"><template #default="{ row }">{{ localTime(row.publishedAt) }}</template></el-table-column></el-table>
      <el-button v-if="next" :loading="busy" @click="load(next ?? '')">更多政策版本</el-button>
    </section>
    <PolicyEditor v-if="editing" :warehouse-id="session.warehouseId" :current="current" @saved="saved" @cancel="editing = false" />
  </WorkspaceLayout>
</template>
