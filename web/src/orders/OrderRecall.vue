<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { request, ApiError, errorText } from '../api';
import { useSessionStore } from '../session';
import { recallSchema, recallResponseSchema, recallProofSchema, recallLabel, recoveryDetailSchema } from './recall-contracts';
import type { Recall } from './recall-contracts';
import type { z } from 'zod';
import OrderContinuation from './OrderContinuation.vue';
import { dispositionLabels } from './disposition-contracts';
const props = defineProps<{ warehouseId: string; orderId: string; rowVersion: string; status: string }>();
const emit = defineEmits<{ updated: []; busy: [value: boolean] }>();
const session = useSessionStore(); const recall = ref<Recall | null>(null); const error = ref(''); const loading = ref(false);
const detail = ref<z.infer<typeof recoveryDetailSchema> | null>(null);
const childBusy = ref(false);
const effectiveScans = computed(() => detail.value?.scans.filter(s => s.status === 'ACTIVE' && !detail.value?.dispositions.some(d => d.captureId === s.captureId)) ?? []);
function scanLabel(id: string, status: string): string {
  const proof = detail.value?.dispositions.find(d => d.captureId === id);
  return proof ? dispositionLabels[proof.action] : status === 'ACTIVE' ? '有效明细' : '本地已撤销';
}
const dialog = ref<'RECALL' | 'CANCEL_EMPTY' | null>(null); const reason = ref(''); const uncertain = ref(false);
let pending: { path: string; key: string; body: string } | null = null; let generation = 0;
const canRecall = computed(() => session.identity?.permissions.includes('order:recall'));
const canCancel = computed(() => canRecall.value && session.identity?.permissions.includes('order:cancel') && recall.value?.status === 'STOPPED' && !recall.value.continuation && recall.value.recovery?.actualCount === '0');
async function load(): Promise<void> {
  const current = ++generation; loading.value = true; error.value = '';
  try {
    const data = await request(`/orders/${props.orderId}/change-requests?warehouseId=${props.warehouseId}`, recallResponseSchema);
    if (current !== generation) return;
    recall.value = data.request; detail.value = null;
    if (data.request?.recovery) {
      const found = await request(`/recovery-packages/${data.request.recovery.recoveryPackageId}`, recoveryDetailSchema);
      if (current === generation) detail.value = found;
    }
  } catch (cause) { if (current === generation) error.value = errorText(cause); }
  finally { if (current === generation) loading.value = false; }
}
function open(action: 'RECALL' | 'CANCEL_EMPTY'): void { dialog.value = action; reason.value = ''; emit('busy', true); }
function close(): void { if (loading.value || uncertain.value) return; dialog.value = null; emit('busy', false); }
function command(): { path: string; key: string; body: string } {
  const query = `?warehouseId=${props.warehouseId}`;
  if (dialog.value === 'RECALL') return { path: `/orders/${props.orderId}/change-requests${query}`, key: crypto.randomUUID(),
    body: JSON.stringify({ type: 'RECALL', expectedVersion: props.rowVersion, reason: reason.value.trim() }) };
  const item = recall.value; if (!item?.recovery) throw new Error('缺少完整恢复包');
  return { path: `/change-requests/${item.changeRequestId}/resolve${query}`, key: crypto.randomUUID(), body: JSON.stringify({
    action: 'CANCEL_EMPTY', expectedVersion: item.rowVersion, recoveryPackageId: item.recovery.recoveryPackageId,
    payloadSha256: item.recovery.payloadSha256, physicalEvidence: reason.value.trim() }) };
}
async function confirm(): Promise<void> {
  if (loading.value || !reason.value.trim()) return;
  pending ??= command(); loading.value = true; error.value = '';
  try {
    const options = { method: 'POST', body: pending.body, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': pending.key } };
    if (dialog.value === 'RECALL') await request(pending.path, recallSchema, options);
    else await request(pending.path, recallProofSchema, options);
    pending = null; uncertain.value = false; dialog.value = null; emit('busy', false); emit('updated'); await load();
  } catch (cause) {
    uncertain.value = !(cause instanceof ApiError) || cause.status >= 500 || cause.status === 401;
    if (!uncertain.value) pending = null;
    error.value = errorText(cause);
  } finally { loading.value = false; }
}
watch(() => [props.warehouseId, props.orderId], () => { recall.value = null; detail.value = null; void load(); }, { immediate: true });
</script>
<template>
  <section class="panel">
    <div class="section-heading"><div><h2>停止与恢复装车</h2><p class="muted">原 PDA 联网核对后才能确认停止；离线期间无法立即停止，订单与占用保留。</p></div>
      <el-button :loading="loading" :disabled="!!dialog || childBusy" @click="load">刷新停止状态</el-button></div>
    <el-alert v-if="error && !dialog" :title="error" type="error" :closable="false" />
    <template v-if="recall">
      <el-tag :type="recall.status === 'CANCELLED' ? 'info' : 'warning'">{{ recallLabel[recall.status] }}</el-tag>
      <p>停止原因：{{ recall.reason }}</p>
      <p v-if="recall.recovery">停止记录：有效装载 {{ recall.recovery.actualCount }} 包</p><details v-if="recall.recovery" class="file-proof"><summary>技术详情</summary>输入高水位 {{ recall.recovery.captureHighWatermark }} · 审计覆盖至 {{ recall.recovery.auditThroughSeq }}</details>
      <p v-else-if="recall.status === 'WITHDRAWN'" class="muted">原故障领取已失效，原记录保留。后台核对新草稿后重新发布，新设备从头作业。</p>
      <p v-else class="muted">尚无完整恢复包，不能作废或重新分配；原 PDA 须完成未保存输入核对并上传。</p>
      <el-alert v-if="recall.recovery && recall.recovery.actualCount !== '0'" title="已有装车记录，保留实物与占用，等待后续受控处理。" type="warning" :closable="false" />
      <el-alert v-for="issue in recall.recovery?.issues" :key="`${issue.barcode}-${issue.code}`" :title="`${issue.barcode}：${issue.code}`" type="warning" :closable="false" />
      <el-table v-if="detail" :data="detail.scans" max-height="360"><el-table-column prop="orderScanSeq" label="原扫码序号" width="110" /><el-table-column prop="barcode" label="条码" min-width="160" /><el-table-column prop="deliveryCode" label="产品" width="90" /><el-table-column label="记录状态" width="130"><template #default="{ row }">{{ scanLabel(row.captureId, row.status) }}</template></el-table-column></el-table>
      <el-button v-if="canCancel" type="danger" plain :disabled="loading || childBusy" @click="open('CANCEL_EMPTY')">主管核验零包作废</el-button>
      <OrderContinuation v-if="detail" :warehouse-id="warehouseId" :recall="recall" :scans="effectiveScans" @busy="childBusy = $event; emit('busy', $event)" @updated="load(); emit('updated')" />
    </template>
    <el-button v-if="canRecall && ['ASSIGNED', 'REVIEW_REQUIRED', 'CONTINUING'].includes(status) && (!recall || recall.status === 'WITHDRAWN' || recall.continuation?.status === 'ACTIVATED')" type="warning" :disabled="loading || childBusy" @click="open('RECALL')">申请停止装车</el-button>
    <p v-else-if="!recall" class="muted">当前没有停止装车申请。</p>
    <el-dialog :model-value="!!dialog" :title="dialog === 'RECALL' ? '申请停止装车' : '主管核验零包作废'" width="520" :close-on-click-modal="false" :show-close="false" :close-on-press-escape="false">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-alert v-if="uncertain" title="结果未确认，原请求已保留，请重试原请求。" type="warning" :closable="false" />
      <p>{{ dialog === 'RECALL' ? '召回申请不会立即解除离线授权或释放订单；须等待原机上传恢复证据并确认停止。' : '请核实车辆及货物确实没有本单有效装包。此操作作废订单，原记录和停止证明保留。' }}</p>
      <el-input v-model="reason" :aria-label="dialog === 'RECALL' ? '停止原因' : '实物核验凭据'" type="textarea" :maxlength="dialog === 'RECALL' ? 500 : 1000" :disabled="loading || uncertain" :placeholder="dialog === 'RECALL' ? '请填写停止原因' : '填写实物核验位置、结果及凭据'" />
      <template #footer><el-button :disabled="loading || uncertain" @click="close">返回核对</el-button><el-button type="danger" :loading="loading" :disabled="!reason.trim()" @click="confirm">{{ uncertain ? '重试原请求' : '确认此操作' }}</el-button></template>
    </el-dialog>
  </section>
</template>
