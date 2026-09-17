<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { z } from 'zod';
import { request, errorText } from '../api';
import { pageSchema } from '../inventory/master-contracts';
import { revisionSchema, eventSchema, detailSchema, localTime } from './contracts';
import type { Detail } from './contracts';
const props = defineProps<{ warehouseId: string; orderId: string }>();
const revisions = ref<z.infer<typeof revisionSchema>[]>([]); const events = ref<z.infer<typeof eventSchema>[]>([]);
const revisionNext = ref<string | null>(null); const eventNext = ref<string | null>(null);
const busy = ref(false); const error = ref(''); const previous = ref<Detail | null>(null);
async function loadRevisions(beforeRevision = ''): Promise<void> {
  const params = new URLSearchParams({ warehouseId: props.warehouseId, limit: '20' });
  if (beforeRevision) params.set('beforeRevision', beforeRevision);
  const result = await request(`/orders/${props.orderId}/revisions?${params}`, pageSchema(revisionSchema));
  revisions.value = beforeRevision ? [...revisions.value, ...result.items] : result.items; revisionNext.value = result.nextCursor;
}
async function loadEvents(beforeId = ''): Promise<void> {
  const params = new URLSearchParams({ warehouseId: props.warehouseId, limit: '20' });
  if (beforeId) params.set('beforeId', beforeId);
  const result = await request(`/orders/${props.orderId}/events?${params}`, pageSchema(eventSchema));
  events.value = beforeId ? [...events.value, ...result.items] : result.items; eventNext.value = result.nextCursor;
}
async function run(action: () => Promise<void>): Promise<void> {
  busy.value = true; error.value = '';
  try { await action(); } catch (cause) { error.value = errorText(cause); } finally { busy.value = false; }
}
async function inspect(revision: number): Promise<void> {
  await run(async () => { previous.value = await request(`/orders/${props.orderId}?warehouseId=${props.warehouseId}&revision=${revision}`, detailSchema); });
}
function reason(value: string): string {
  try {
    const result = z.object({ reason: z.string() }).safeParse(JSON.parse(value));
    return result.success ? result.data.reason : '查看对应计划版本';
  } catch { return '查看对应计划版本'; }
}
function eventLabel(value: string): string {
  const labels: Record<string, string> = { ORDER_CREATED: '创建订单', ORDER_REVISED: '修订计划', ORDER_CANCELLED: '作废订单',
    ORDER_PUBLISHED: '发布订单', ORDER_REVISED_PUBLISHED: '修订并重新发布' };
  return labels[value] ?? value;
}
onMounted(() => { void run(async () => { await loadRevisions(); await loadEvents(); }); });
</script>
<template>
  <section class="panel"><h2>版本与操作记录</h2><el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-tabs><el-tab-pane label="计划版本"><el-table v-loading="busy" :data="revisions">
      <el-table-column prop="revision" label="版本" width="80" /><el-table-column prop="vehicleNo" label="车号" min-width="130" /><el-table-column prop="plannedTotal" label="计划包数" width="100" />
      <el-table-column prop="reason" label="原因" min-width="180" /><el-table-column prop="createdBy" label="操作人" width="120" /><el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="inspect(row.revision)">查看版本</el-button></template></el-table-column>
    </el-table><el-button v-if="revisionNext" :loading="busy" @click="run(() => loadRevisions(revisionNext ?? ''))">更多版本</el-button></el-tab-pane>
    <el-tab-pane label="操作时间线"><el-table v-loading="busy" :data="events">
      <el-table-column label="动作" width="140"><template #default="{ row }">{{ eventLabel(row.type) }}</template></el-table-column>
      <el-table-column prop="actor" label="操作人" width="130" /><el-table-column label="原因" min-width="200"><template #default="{ row }">{{ reason(row.details) }}</template></el-table-column><el-table-column label="时间" width="190"><template #default="{ row }">{{ localTime(row.serverTime) }}</template></el-table-column>
    </el-table><el-button v-if="eventNext" :loading="busy" @click="run(() => loadEvents(eventNext ?? ''))">更多记录</el-button></el-tab-pane></el-tabs>
    <el-dialog :model-value="!!previous" title="历史计划版本（只读）" width="720" @close="previous = null"><template v-if="previous">
      <h3>V{{ previous.plan.revision }} · {{ previous.plan.vehicleNo }} · {{ previous.plan.plannedTotal }} 包</h3><p class="muted">{{ previous.plan.reason }} · {{ previous.plan.createdBy }} · {{ localTime(previous.plan.createdAt) }}</p>
      <el-table :data="previous.lines"><el-table-column prop="deliveryCode" label="代码" width="90" /><el-table-column prop="model" label="型号" /><el-table-column prop="name" label="名称" /><el-table-column prop="plannedCount" label="计划包数" width="100" /></el-table><p>{{ previous.plan.notes }}</p>
    </template></el-dialog>
  </section>
</template>
