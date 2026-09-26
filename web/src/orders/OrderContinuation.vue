<script setup lang="ts">
import { computed, ref } from 'vue';
import { formatDateTime } from '../shared/date-time';
import { request, ApiError, errorText, currentRecoveryEpoch } from '../api';
import { useSessionStore } from '../session';
import { continuationSchema, continuationApprovalSchema, continuationLabels } from './continuation-contracts';
import type { Recall } from './recall-contracts';
import OrderDisposition from './OrderDisposition.vue';
import type { DispositionScan, PhysicalEvidence } from './disposition-contracts';
import { dispositionLabels } from './disposition-contracts';
const props = defineProps<{ warehouseId: string; recall: Recall; scans: DispositionScan[] }>();
const emit = defineEmits<{ updated: []; busy: [value: boolean] }>();
const session = useSessionStore(); const dialog = ref<'REQUEST' | 'APPROVE' | null>(null);
const reason = ref(''); const reviewed = ref(false); const busy = ref(false); const uncertain = ref(false); const error = ref('');
let pending: { path: string; body: string; key: string; epoch: string } | null = null;
const evidence = ref<PhysicalEvidence[]>([]); const evidenceReady = ref(false); const evidenceBusy = ref(false);
const removed = computed(() => props.recall.continuation?.dispositions.filter(d => props.scans.some(s => s.captureId === d.captureId)).length ?? evidence.value.length);
const allowed = computed(() => session.identity?.permissions.includes('order:resume'));
const eligible = computed(() => props.recall.status === 'STOPPED' && props.recall.recovery?.issues.length === 0 &&
  ['REJECTED', 'ABORTED'].includes(props.recall.previousSubmission?.status ?? ''));
function open(action: 'REQUEST' | 'APPROVE'): void { dialog.value = action; reason.value = ''; reviewed.value = false; error.value = ''; emit('busy', true); }
function close(): void { if (!busy.value && !uncertain.value) { dialog.value = null; emit('busy', false); } }
function command(): { path: string; body: string; key: string } {
  const r = props.recall; const query = `?warehouseId=${props.warehouseId}`;
  if (dialog.value === 'APPROVE') {
    if (!r.continuation) throw new Error('缺少续作申请');
    return { path: `/change-requests/${r.continuation.continuationId}/resolve${query}`, key: crypto.randomUUID(), body: JSON.stringify({
      action: 'REOPEN_WORK', expectedVersion: r.rowVersion, retainedManifestSha256: r.continuation.retainedManifestSha256, physicalEvidence: reason.value.trim() }) };
  }
  if (!r.previousSubmission || !r.recovery) throw new Error('缺少原终态批次或恢复包');
  return { path: `/orders/${r.orderId}/change-requests${query}`, key: crypto.randomUUID(), body: JSON.stringify({ type: 'REOPEN_WORK',
    previousSubmissionId: r.previousSubmission.id, expectedWorkRound: r.previousSubmission.workRound, expectedRevision: r.revision,
    recoveryPackageId: r.recovery.recoveryPackageId, requestedDeviceId: r.deviceId, reason: reason.value.trim(),
    dispositions: props.scans.filter(s => s.status === 'ACTIVE').map(s => {
      const proof = evidence.value.find(e => e.captureId === s.captureId);
      return { barcode: s.barcode, action: proof?.action ?? 'RETAIN', expectedDispatchCycle: s.expectedDispatchCycle, approvedEvidenceId: proof?.approvedEvidenceId };
    }) }) };
}
async function confirm(): Promise<void> {
  if (busy.value || !reviewed.value || !reason.value.trim()) return;
  busy.value = true; error.value = '';
  try {
    pending ??= { ...command(), epoch: currentRecoveryEpoch() };
    const options = { method: 'POST', body: pending.body, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': pending.key, 'X-Server-Recovery-Epoch': pending.epoch } };
    if (dialog.value === 'REQUEST') await request(pending.path, continuationSchema, options);
    else await request(pending.path, continuationApprovalSchema, options);
    pending = null; uncertain.value = false; dialog.value = null; emit('busy', false); emit('updated');
  } catch (cause) {
    uncertain.value = !(cause instanceof ApiError) || cause.status >= 500 || cause.status === 401;
    if (!uncertain.value) pending = null;
    error.value = errorText(cause);
  } finally { busy.value = false; }
}
</script>
<template>
  <div>
    <p v-if="recall.continuation"><strong>{{ continuationLabels[recall.continuation.status] }}</strong> · 第 {{ recall.continuation.workRound }} 轮 · 下一个输入序号 {{ recall.continuation.nextOrderScanSeq }}</p>
    <p v-if="recall.continuation?.approval" class="muted">新资格截止 {{ formatDateTime(recall.continuation.approval.expiresAt) }}，原 PDA 须联网核验并激活后续扫。</p>
    <template v-if="recall.continuation?.dispositions.length">
      <p>续作已批准减少 {{ removed }} 包，原车继续保留 {{ scans.length - removed }} 包。减少行的原扫码记录保留，库存处置须等待完整替换批次接收。</p>
      <el-table :data="recall.continuation.dispositions" max-height="280"><el-table-column prop="barcode" label="已核验原包" min-width="150" />
        <el-table-column label="批准处置" min-width="150"><template #default="{ row }">{{ dispositionLabels[row.action as keyof typeof dispositionLabels] }}</template></el-table-column>
      </el-table>
    </template>
    <template v-if="allowed && eligible">
      <OrderDisposition :warehouse-id="warehouseId" :recall="recall" :scans="scans" :disabled="!!dialog" @evidence="evidence = $event" @ready="evidenceReady = $event" @busy="evidenceBusy = $event; emit('busy', $event)" />
      <el-button v-if="!recall.continuation" type="primary" :disabled="!evidenceReady || evidenceBusy" @click="open('REQUEST')">申请恢复装车</el-button>
      <el-button v-else-if="recall.continuation.status === 'REQUESTED'" type="primary" @click="open('APPROVE')">主管核验并批准恢复</el-button>
    </template>
    <p v-else-if="recall.status === 'STOPPED' && !recall.continuation" class="muted">续作须具备已取消或退审的原批次、完整恢复包及专项权限。保留包有冲突时须先对账。</p>
    <el-dialog :model-value="!!dialog" :title="dialog === 'REQUEST' ? '申请恢复装车' : '主管核验并批准恢复'" width="540" :show-close="false" :close-on-click-modal="false" :close-on-press-escape="false">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-alert v-if="uncertain" title="结果未确认，原请求已保留，请重试原请求。" type="warning" :closable="false" />
      <p>原有效装载 {{ scans.length }} 包，批准减少 {{ removed }} 包，继续保留 {{ scans.length - removed }} 包。由原人员在原 PDA 续扫，原记录和序号保留。</p>
      <el-checkbox v-model="reviewed" :disabled="busy || uncertain">已逐包核对保留清单及卸货、隔离凭据</el-checkbox>
      <el-input v-model="reason" type="textarea" :maxlength="dialog === 'REQUEST' ? 500 : 1000" :disabled="busy || uncertain"
        :aria-label="dialog === 'REQUEST' ? '恢复原因' : '恢复装车核验凭据'" :placeholder="dialog === 'REQUEST' ? '填写恢复原因' : '填写核验位置、结果及凭据编号'" />
      <template #footer><el-button :disabled="busy || uncertain" @click="close">返回核对</el-button><el-button type="primary" :loading="busy" :disabled="!reviewed || !reason.trim()" @click="confirm">{{ uncertain ? '重试原请求' : '确认恢复装车' }}</el-button></template>
    </el-dialog>
  </div>
</template>
