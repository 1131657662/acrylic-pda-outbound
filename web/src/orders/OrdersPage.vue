<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import { useSessionStore } from '../session';
import { request, errorText } from '../api';
import { pageSchema } from '../inventory/master-contracts';
import { orderSchema, statusLabel, localTime } from './contracts';
import type { Order } from './contracts';
import OrderEditor from './OrderEditor.vue';
const session = useSessionStore(); const router = useRouter();
const rows = ref<Order[]>([]); const status = ref(''); const search = ref(''); const next = ref<string | null>(null);
const busy = ref(false); const error = ref(''); const editor = ref(false); let generation = 0;
const canWrite = computed(() => session.identity?.permissions.includes('order:write'));
async function load(cursor = ''): Promise<void> {
  const current = ++generation; if (!session.warehouseId) return;
  busy.value = true; error.value = '';
  try {
    const params = new URLSearchParams({ warehouseId: session.warehouseId, status: status.value, search: search.value, cursor, limit: '50' });
    const result = await request(`/orders?${params}`, pageSchema(orderSchema));
    if (current === generation) { rows.value = result.items; next.value = result.nextCursor; }
  } catch (cause) { if (current === generation) error.value = errorText(cause); }
  finally { if (current === generation) busy.value = false; }
}
function saved(id: string): void { editor.value = false; void router.push(`/orders/${id}`); }
watch(() => session.warehouseId, () => { rows.value = []; next.value = null; editor.value = false; void load(); }, { immediate: true });
</script>
<template>
  <WorkspaceLayout title="装车订单" :busy="editor">
    <div class="page-title"><div><p class="eyebrow">OUTBOUND / ORDERS</p><h1>装车订单</h1><p class="muted">一车一单，多产品独立计划。修改留存版本，作废保留记录。</p></div>
      <el-button v-if="canWrite" type="primary" @click="editor = true">新建订单</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <section class="panel">
      <form class="order-filters" @submit.prevent="load()"><el-input v-model="search" aria-label="订单或车号" placeholder="完整订单号或车号" maxlength="40" clearable />
        <el-select v-model="status" aria-label="订单状态" placeholder="全部状态"><el-option label="全部状态" value="" /><el-option label="草稿" value="DRAFT" /><el-option label="待领取" value="READY" /><el-option label="已作废" value="CANCELLED" /></el-select>
        <el-button native-type="submit" :loading="busy">查询订单</el-button></form>
      <el-table v-loading="busy" :data="rows" empty-text="暂无符合条件的订单">
        <el-table-column label="订单 / 车柜号" min-width="320"><template #default="{ row }"><router-link :to="`/orders/${row.id}`" class="order-link">{{ row.orderNo }}</router-link><p class="muted">{{ row.vehicleNo }}{{ row.containerNo ? ` · ${row.containerNo}` : '' }}</p></template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'CANCELLED' ? 'info' : 'warning'">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="plannedTotal" label="计划包数" width="110" /><el-table-column label="计划版本" width="100"><template #default="{ row }">V{{ row.revision }}</template></el-table-column>
        <el-table-column label="出库实绩" min-width="155"><template #default>云端未收到实绩</template></el-table-column>
        <el-table-column label="创建信息" min-width="175"><template #default="{ row }">{{ row.createdBy }}<p class="muted">{{ localTime(row.createdAt) }}</p></template></el-table-column>
      </el-table>
      <div class="pagination"><span class="muted">按创建时间倒序，每页 50 单</span><el-button :disabled="busy" @click="load()">第一页</el-button><el-button :disabled="busy || !next" @click="next && load(next)">下一页</el-button></div>
    </section>
    <OrderEditor v-if="editor" :warehouse-id="session.warehouseId" :detail="null" @saved="saved" @cancel="editor = false" />
  </WorkspaceLayout>
</template>
<style scoped>
.order-filters { display: grid; grid-template-columns: minmax(260px, 1fr) 160px auto; gap: 12px; margin-bottom: 24px; }
.order-link { color: var(--el-color-primary); overflow-wrap: anywhere; }
</style>
