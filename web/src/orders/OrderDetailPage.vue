<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import { useSessionStore } from '../session';
import { request, errorText, ApiError } from '../api';
import { detailSchema, preflightSchema, receiptSchema, statusLabel, localTime } from './contracts';
import type { Detail, Preflight } from './contracts';
import OrderEditor from './OrderEditor.vue';
import OrderHistory from './OrderHistory.vue';
import OrderPublishDialog from './OrderPublishDialog.vue';
import OrderPublications from './OrderPublications.vue';
import OrderSubmission from './OrderSubmission.vue';
const route = useRoute(); const router = useRouter(); const session = useSessionStore();
const detail = ref<Detail | null>(null); const preflight = ref<Preflight | null>(null); const busy = ref(false); const error = ref('');
const editor = ref(false); const cancelReason = ref(''); const cancelDialog = ref(false); const uncertain = ref(false);
const publishDialog = ref(false);
let cancelBody = ''; let cancelKey = ''; let generation = 0;
const canWrite = computed(() => session.identity?.permissions.includes('order:write'));
const canCancel = computed(() => session.identity?.permissions.includes('order:cancel'));
const canPublish = computed(() => session.identity?.permissions.includes('order:publish'));
const unassigned = computed(() => detail.value && ['DRAFT', 'READY'].includes(detail.value.order.status));
async function load(): Promise<void> {
  const current = ++generation;
  if (!session.warehouseId || typeof route.params.id !== 'string') return;
  busy.value = true; error.value = '';
  try {
    const params = new URLSearchParams({ warehouseId: session.warehouseId });
    const id = route.params.id;
    const loaded = await request(`/orders/${id}?${params}`, detailSchema);
    const checked = await request(`/orders/${id}/preflight?${params}`, preflightSchema);
    if (current === generation) { detail.value = loaded; preflight.value = checked; }
  } catch (cause) { if (current === generation) error.value = errorText(cause); }
  finally { if (current === generation) busy.value = false; }
}
async function cancel(): Promise<void> {
  if (busy.value || !detail.value || !cancelReason.value.trim()) return;
  if (!uncertain.value) {
    cancelBody = JSON.stringify({ expectedVersion: detail.value.order.rowVersion, reason: cancelReason.value });
    cancelKey = crypto.randomUUID();
  }
  busy.value = true; error.value = '';
  try {
    await request(`/orders/${detail.value.order.id}/cancel?warehouseId=${session.warehouseId}`, receiptSchema, { method: 'POST',
      body: cancelBody, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': cancelKey } });
    uncertain.value = false; cancelDialog.value = false; cancelReason.value = ''; await load();
  } catch (cause) { uncertain.value = !(cause instanceof ApiError) || cause.status >= 500; error.value = errorText(cause); }
  finally { busy.value = false; }
}
function saved(): void { editor.value = false; void load(); }
function published(): void { publishDialog.value = false; void load(); }
watch(() => [session.warehouseId, route.params.id], () => { detail.value = null; preflight.value = null; void load(); }, { immediate: true });
</script>
<template>
  <WorkspaceLayout title="订单详情" :busy="editor || cancelDialog || publishDialog || busy">
    <el-button text @click="router.push('/orders')">← 返回订单列表</el-button>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-if="detail" class="page-title order-detail-title"><div><p class="eyebrow">ORDER / {{ detail.order.orderNo }}</p><h1>{{ detail.plan.vehicleNo }}</h1><p class="muted">{{ detail.plan.containerNo || '未填写柜号' }} · {{ statusLabel(detail.order.status) }} · V{{ detail.plan.revision }}</p></div>
      <div class="order-actions"><el-button v-if="canPublish && detail.order.status === 'DRAFT'" type="primary" :disabled="busy || !preflight?.checksPassed" @click="publishDialog = true">发布订单</el-button>
        <el-button v-if="canWrite && unassigned && (detail.order.status === 'DRAFT' || canPublish)" :disabled="busy" @click="editor = true">{{ detail.order.status === 'READY' ? '修订并重新发布' : '修订草稿' }}</el-button>
        <el-button v-if="canCancel && unassigned" type="danger" plain :disabled="busy" @click="cancelDialog = true">作废订单</el-button></div></div>
    <template v-if="detail">
      <section class="metrics"><div><span>计划合计</span><strong>{{ detail.plan.plannedTotal }} 包</strong></div><div><span>计划版本</span><strong>V{{ detail.plan.revision }}</strong></div><div><span>来源</span><strong>{{ detail.order.source === 'PDA_TEMP' ? 'PDA临时单' : 'Web' }}</strong></div><div><span>云端状态</span><strong class="small-metric">{{ statusLabel(detail.order.status) }}</strong></div></section>
      <OrderSubmission :warehouse-id="session.warehouseId" :order-id="detail.order.id" @updated="load" />
      <section v-if="detail.order.status === 'DRAFT'" class="panel"><div class="section-heading"><div><h2>下发前检查</h2><p class="muted">检查计划与库存、映射版本。候选数量未扣除其他订单占用；本次检查不预留库存，也不会下发PDA。</p></div><el-tag :type="preflight?.checksPassed ? 'success' : 'danger'">{{ preflight?.checksPassed ? '版本检查通过' : '存在阻断项' }}</el-tag></div>
        <el-alert v-for="issue in preflight?.blockers" :key="issue.code" :title="issue.message" type="error" :closable="false" />
        <el-table :data="preflight?.lines ?? []"><el-table-column prop="deliveryCode" label="代码" width="90" /><el-table-column prop="plannedCount" label="计划包数" width="110" /><el-table-column prop="candidateCount" label="库存候选包数" width="140" /><el-table-column prop="shortage" label="提示缺口" width="110" /><el-table-column label="结果"><template #default="{ row }"><el-tag :type="row.mappingValid && row.shortage === 0 ? 'success' : 'warning'">{{ row.mappingValid && row.shortage === 0 ? '数量充足' : '需核对' }}</el-tag></template></el-table-column></el-table>
      </section>
      <OrderHistory :key="`${detail.order.id}-${detail.order.rowVersion}`" :warehouse-id="session.warehouseId" :order-id="detail.order.id" />
      <OrderPublications :key="`pub-${detail.order.id}-${detail.order.rowVersion}`" :warehouse-id="session.warehouseId" :order-id="detail.order.id" />
      <section class="panel"><div class="section-heading"><div><h2>产品计划</h2><p class="muted">产品行独立计划，订单合计由明细相加。</p></div></div>
        <el-table :data="detail.lines"><el-table-column prop="deliveryCode" label="发货代码" width="110" /><el-table-column prop="model" label="型号" min-width="190" /><el-table-column prop="name" label="名称" min-width="150" /><el-table-column prop="plannedCount" label="计划包数" width="115" /></el-table>
        <p class="muted detail-note">创建人：{{ detail.order.createdBy }} · 创建时间：{{ localTime(detail.order.createdAt) }} · 备注：{{ detail.plan.notes || '无' }}</p>
      </section>
    </template>
    <OrderEditor v-if="editor && detail" :warehouse-id="session.warehouseId" :detail="detail" @saved="saved" @cancel="editor = false" />
    <OrderPublishDialog v-if="publishDialog && detail" :warehouse-id="session.warehouseId" :detail="detail" @saved="published" @cancel="publishDialog = false" />
    <el-dialog v-model="cancelDialog" title="作废未领取订单" width="480" :close-on-click-modal="false" :show-close="!busy && !uncertain" :close-on-press-escape="!busy && !uncertain"><el-alert v-if="uncertain" title="结果未确认，请重试原作废请求。" type="warning" :closable="false" /><el-input v-model="cancelReason" aria-label="作废原因" type="textarea" maxlength="500" placeholder="请填写作废原因" :disabled="busy || uncertain" /><template #footer><el-button :disabled="busy || uncertain" @click="cancelDialog = false">取消</el-button><el-button type="danger" :loading="busy" :disabled="!cancelReason.trim()" @click="cancel">{{ uncertain ? '重试原作废请求' : '确认作废' }}</el-button></template></el-dialog>
  </WorkspaceLayout>
</template>
<style scoped>
.order-actions { display: flex; gap: 10px; }
.small-metric { font-size: 16px; }
.detail-note { margin-top: 18px; }
.order-detail-title { margin-top: 20px; }
</style>
