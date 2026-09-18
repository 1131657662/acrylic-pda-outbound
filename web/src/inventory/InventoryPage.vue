<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import InventoryHistory from './InventoryHistory.vue';
import CursorPager from '../components/CursorPager.vue';
import QueryFeedback from '../components/QueryFeedback.vue';
import { useCursorPage } from '../useCursorPage';
import { request, errorText } from '../api';
import { useSessionStore } from '../session';
import { pageSchema, baleSchema, baselineSchema } from './master-contracts';
import { weightKg } from './contracts';
const session = useSessionStore();
const filter = reactive({ barcode: '', deliveryCode: '', presence: 'all' });
const tab = ref('stock'); const baselineError = ref('');
const active = ref<string | null>(null); const mappingRevision = ref('');
let applied = { ...filter };
const paging = useCursorPage(async (afterBarcode) => {
  if (!session.warehouseId) return { items: [], nextCursor: null };
  const params = new URLSearchParams({ warehouseId: session.warehouseId, ...applied, afterBarcode, limit: '50' });
  return request(`/inventory/bales?${params}`, pageSchema(baleSchema));
});
const { rows, busy, error, page, nextCursor } = paging;
function search(): void { applied = { ...filter }; void paging.reset(); }
async function baseline(): Promise<void> {
  baselineError.value = '';
  const warehouse = session.warehouseId; if (!warehouse) return;
  try {
    const data = await request(`/inventory/current?warehouseId=${warehouse}`, baselineSchema);
    if (warehouse === session.warehouseId) { active.value = data.activeSnapshotId; mappingRevision.value = data.mappingRevision; }
  } catch (cause) { if (warehouse === session.warehouseId) baselineError.value = errorText(cause); }
}
watch(() => session.warehouseId, () => { active.value = null; mappingRevision.value = ''; search(); void baseline(); }, { immediate: true });
</script>
<template>
  <WorkspaceLayout title="库存查询" :list="tab === 'stock'">
    <div class="page-title"><h1>库存查询</h1><span v-if="mappingRevision" class="muted">产品对应版本 {{ mappingRevision }}</span></div>
    <el-radio-group v-model="tab" class="auxiliary-tabs" aria-label="库存视图"><el-radio-button value="stock">库存列表</el-radio-button><el-radio-button value="history">版本与差异</el-radio-button></el-radio-group>
    <section v-show="tab === 'stock'" class="panel auxiliary-list"><form class="stock-filter" @submit.prevent="search">
      <label>条码<el-input v-model="filter.barcode" aria-label="查询条码" placeholder="完整条码" maxlength="11" clearable /></label>
      <label>发货代码<el-input v-model="filter.deliveryCode" aria-label="查询发货代码" placeholder="三位发货代码" maxlength="3" clearable /></label>
      <label>上游状态<el-select v-model="filter.presence" aria-label="上游状态"><el-option value="all" label="全部上游状态" /><el-option value="present" label="上游列为在库" /><el-option value="missing" label="上游未列出" /></el-select></label>
      <el-button type="primary" native-type="submit" :loading="busy">查询</el-button>
    </form>
      <QueryFeedback :busy="busy" :error="error" :count="rows.length" empty="没有符合条件的包" @retry="paging.retry" />
      <div v-if="rows.length" class="auxiliary-table"><el-table v-loading="busy" :data="rows" row-key="barcode" height="100%">
        <el-table-column prop="barcode" label="条码" width="155" /><el-table-column prop="deliveryCode" label="代码" width="70" />
        <el-table-column prop="model" label="型号" min-width="175" /><el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column label="净重 kg" width="100" align="right"><template #default="{ row }">{{ weightKg(row.weightGrams) }}</template></el-table-column>
        <el-table-column label="上游状态" width="125"><template #default="{ row }"><el-tag :type="row.upstreamPresent ? 'success' : 'warning'">{{ row.upstreamPresent ? '列为在库' : '未列出' }}</el-tag></template></el-table-column>
        <el-table-column label="系统扣账" width="110"><template #default="{ row }">{{ row.bookStatus === 'DEDUCTED' ? '已扣账' : '未扣账' }}</template></el-table-column>
        <el-table-column prop="blockedReason" label="冻结原因" min-width="120" /><el-table-column prop="dispatchCycle" label="出库周期" width="95" />
      </el-table></div>
      <CursorPager :page="page" :size="50" :busy="busy" :has-next="!!nextCursor" @first="paging.first" @previous="paging.previous" @next="paging.next" />
    </section>
    <div v-show="tab === 'history'">
      <QueryFeedback v-if="baselineError" :busy="false" :error="baselineError" :count="0" empty="" @retry="baseline" />
      <InventoryHistory :warehouse-id="session.warehouseId" :active-snapshot-id="active" />
    </div>
  </WorkspaceLayout>
</template>
