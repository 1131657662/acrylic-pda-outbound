<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import InventoryHistory from './InventoryHistory.vue';
import { request, errorText } from '../api';
import { useSessionStore } from '../session';
import { pageSchema, baleSchema, baselineSchema } from './master-contracts';
import type { Bale } from './master-contracts';
import { weightKg } from './contracts';
const session = useSessionStore();
const filter = reactive({ barcode: '', deliveryCode: '', presence: 'all' });
const rows = ref<Bale[]>([]); const next = ref<string | null>(null); const busy = ref(false); const error = ref('');
const active = ref<string | null>(null); const mappingRevision = ref('0'); let generation = 0;
let applied = { ...filter };
async function load(afterBarcode = ''): Promise<void> {
  const current = ++generation; if (!session.warehouseId) return;
  busy.value = true; error.value = '';
  if (!afterBarcode) applied = { ...filter };
  const params = new URLSearchParams({ warehouseId: session.warehouseId, ...applied, afterBarcode, limit: '50' });
  try {
    const page = await request(`/inventory/bales?${params}`, pageSchema(baleSchema));
    if (current !== generation) return;
    rows.value = page.items; next.value = page.nextCursor;
  } catch (cause) { if (current === generation) error.value = errorText(cause); }
  finally { if (current === generation) busy.value = false; }
}
async function baseline(): Promise<void> {
  const warehouse = session.warehouseId; if (!warehouse) return;
  try {
    const data = await request(`/inventory/current?warehouseId=${warehouse}`, baselineSchema);
    if (warehouse === session.warehouseId) { active.value = data.activeSnapshotId; mappingRevision.value = data.mappingRevision; }
  } catch (cause) { if (warehouse === session.warehouseId) error.value = errorText(cause); }
}
watch(() => session.warehouseId, () => { rows.value = []; next.value = null; active.value = null; mappingRevision.value = '0'; void load(); void baseline(); }, { immediate: true });
</script>
<template>
  <WorkspaceLayout title="库存查询与版本">
    <div class="page-title"><div><p class="eyebrow">INVENTORY / STOCK</p><h1>库存查询与版本</h1><p class="muted">分别查看上游库存声明、系统扣账与冻结状态。映射修订号 {{ mappingRevision }}。</p></div></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <section class="panel"><form class="stock-filter" @submit.prevent="load()">
      <el-input v-model="filter.barcode" aria-label="查询条码" placeholder="完整条码" maxlength="11" clearable />
      <el-input v-model="filter.deliveryCode" aria-label="查询发货代码" placeholder="三位发货代码" maxlength="3" clearable />
      <el-select v-model="filter.presence" aria-label="上游状态"><el-option value="all" label="全部上游状态" /><el-option value="present" label="上游列为在库" /><el-option value="missing" label="上游未列出" /></el-select>
      <el-button type="primary" native-type="submit" :loading="busy">查询</el-button>
    </form>
      <el-table v-loading="busy" :data="rows" row-key="barcode" empty-text="没有符合条件的包">
        <el-table-column prop="barcode" label="条码" width="155" /><el-table-column prop="deliveryCode" label="代码" width="70" />
        <el-table-column prop="model" label="型号" min-width="175" /><el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column label="净重 kg" width="100" align="right"><template #default="{ row }">{{ weightKg(row.weightGrams) }}</template></el-table-column>
        <el-table-column label="上游状态" width="125"><template #default="{ row }"><el-tag :type="row.upstreamPresent ? 'success' : 'warning'">{{ row.upstreamPresent ? '列为在库' : '未列出' }}</el-tag></template></el-table-column>
        <el-table-column label="系统扣账" width="110"><template #default="{ row }">{{ row.bookStatus === 'DEDUCTED' ? '已扣账' : '未扣账' }}</template></el-table-column>
        <el-table-column prop="blockedReason" label="冻结原因" min-width="120" /><el-table-column prop="dispatchCycle" label="出库周期" width="95" />
      </el-table>
      <div class="pagination"><span class="muted">每页 50 条 · 净重保留原始精度</span><el-button :disabled="busy" @click="load()">第一页</el-button><el-button :disabled="busy || !next" @click="next && load(next)">下一页</el-button></div>
    </section>
    <InventoryHistory :warehouse-id="session.warehouseId" :active-snapshot-id="active" />
  </WorkspaceLayout>
</template>
