<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { request, ApiError, currentRecoveryEpoch, errorText } from '../api';
import { useSessionStore } from '../session';
import { faultWithdrawalSchema, faultWithdrawalCommandSchema } from './fault-withdrawal-contracts';
import { loadWithdrawal, saveWithdrawal, clearWithdrawal, lockWithdrawal } from './fault-withdrawal-pending';
import type { SavedWithdrawal, WithdrawalOwner } from './fault-withdrawal-pending';
import { faultWithdrawalEnabled } from './fault-withdrawal-feature';
const props = defineProps<{ warehouseId: string; orderId: string; rowVersion: string; status: string }>();
const emit = defineEmits<{ updated: []; busy: [value: boolean] }>();
const session = useSessionStore(); const saved = ref<SavedWithdrawal | null>(null); const error = ref('');
const dialog = ref(false); const loading = ref(false); const reason = ref(''); const physical = ref(''); const checked = ref(false);
const unresolved = computed(() => !!saved.value && !saved.value.receipt);
const allowed = computed(() => faultWithdrawalEnabled && session.identity?.permissions.includes('order:recall') && ['ASSIGNED', 'REVIEW_REQUIRED', 'RECALL_PENDING'].includes(props.status));
const dispose = computed(() => session.identity?.permissions.includes('order:dispose'));
function owner(): WithdrawalOwner {
  if (!session.identity || session.warehouseId !== props.warehouseId) throw new Error('身份或仓库已变化，请保留原请求并恢复原身份');
  return { userId: session.identity.id, warehouseId: props.warehouseId, orderId: props.orderId };
}
function assertOwner(expected: WithdrawalOwner): void {
  const now = owner();
  if (now.userId !== expected.userId || now.warehouseId !== expected.warehouseId || now.orderId !== expected.orderId)
    throw new Error('身份或订单已变化，原撤回请求未发送');
}
function open(): void { dialog.value = true; emit('busy', true); }
function close(): void { if (loading.value || unresolved.value) return; dialog.value = false; emit('busy', false); }
function prepare(expected: WithdrawalOwner): SavedWithdrawal {
  const pending = loadWithdrawal(expected);
  if (pending && !pending.receipt) return pending;
  const body = { type: 'FAULT_WITHDRAWAL', expectedVersion: props.rowVersion, reason: reason.value.trim(),
    ...(checked.value ? { physicalChecked: true, physicalEvidence: physical.value.trim() } : {}) };
  const next = { ...expected, key: crypto.randomUUID(), epoch: currentRecoveryEpoch(), body: JSON.stringify(body), receipt: null };
  saveWithdrawal(next); return next;
}
async function send(expected: WithdrawalOwner): Promise<void> {
  assertOwner(expected); saved.value = prepare(expected); const pending = saved.value;
  const receipt = await request(`/orders/${props.orderId}/change-requests?warehouseId=${props.warehouseId}`, faultWithdrawalSchema,
    { method: 'POST', body: pending.body, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': pending.key,
      'X-Server-Recovery-Epoch': pending.epoch } }, () => assertOwner(expected));
  assertOwner(expected);
  if (receipt.orderId !== expected.orderId || receipt.resolvedBy !== expected.userId) throw new Error('原撤回回执身份不符，请核对原请求');
  const completed = { ...pending, receipt }; saveWithdrawal(completed); saved.value = completed;
  dialog.value = false; emit('busy', false); emit('updated');
}
async function confirm(): Promise<void> {
  if (loading.value || (!unresolved.value && (!reason.value.trim() || checked.value && !physical.value.trim()))) return;
  const expected = owner(); loading.value = true; error.value = '';
  try { await lockWithdrawal(expected, () => send(expected)); }
  catch (cause) {
    if (cause instanceof ApiError && cause.status >= 400 && cause.status < 500 && cause.status !== 401 && cause.code !== 'IDEMPOTENCY_CONTENT_MISMATCH') {
      try { assertOwner(expected); clearWithdrawal(expected); saved.value = null; } catch { /* Preserve unresolved state when storage cannot be verified. */ }
    }
    error.value = cause instanceof Error && !(cause instanceof ApiError) ? cause.message : errorText(cause);
  } finally { loading.value = false; }
}
watch(() => [session.identity?.id, props.warehouseId, props.orderId], () => {
  saved.value = null; dialog.value = false; emit('busy', false);
  if (!session.identity) return;
  try {
    saved.value = loadWithdrawal(owner());
    if (unresolved.value && saved.value && faultWithdrawalEnabled) {
      const command = faultWithdrawalCommandSchema.parse(JSON.parse(saved.value.body));
      reason.value = command.reason; checked.value = command.physicalChecked === true;
      physical.value = command.physicalEvidence ?? ''; open();
    }
    if (unresolved.value && saved.value && !faultWithdrawalEnabled)
      error.value = '故障撤回功能当前关闭；原请求已保留，未发送新请求。';
  }
  catch { error.value = '原撤回请求暂不可读取，请保留浏览器存储并恢复；未发送新请求。'; }
}, { immediate: true });
</script>
<template>
  <section class="panel">
    <h2>故障订单撤回重发</h2>
    <p>原机故障或离线时，有权后台人员可撤回未完成订单。撤回后重新发布，新设备从头作业；原领取和记录保留。</p>
    <el-alert v-if="error && !dialog" :title="error" type="error" :closable="false" />
    <p v-if="!faultWithdrawalEnabled && !saved?.receipt" class="muted">故障撤回功能当前关闭；原记录与占用保留。</p>
    <p v-if="saved?.receipt">原故障领取已撤回，未冒充原机停止；请在订单主操作中核对草稿并重新发布。</p>
    <el-button v-if="allowed" type="danger" plain :disabled="loading" @click="open">撤回故障订单</el-button>
    <el-dialog :model-value="dialog" title="撤回故障订单" width="520" :show-close="false" :close-on-click-modal="false" :close-on-press-escape="false">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-alert v-if="unresolved" title="结果未确认，原请求已持久保留。重试将使用原键、原正文和原恢复代次。" type="warning" :closable="false" />
      <p>成功出库不能撤回，应办理更正。旧设备离线时不能立即获知撤回，现场须停止使用或回收故障设备。</p>
      <el-input v-model="reason" aria-label="故障撤回原因" type="textarea" maxlength="500" :disabled="loading || unresolved" placeholder="填写故障与撤回原因" />
      <template v-if="dispose">
        <el-checkbox v-model="checked" :disabled="loading || unresolved">我已核对已收包的卸货与实物，可释放原未完成占用</el-checkbox>
        <el-input v-if="checked" v-model="physical" aria-label="故障撤回实物核验凭据" type="textarea" maxlength="1000" :disabled="loading || unresolved" placeholder="逐包核验结果、卸货位置与凭据；没有完成核验请勿勾选" />
      </template>
      <template #footer><el-button :disabled="loading || unresolved" @click="close">返回核对</el-button>
        <el-button type="danger" :loading="loading" :disabled="!unresolved && (!reason.trim() || checked && !physical.trim())" @click="confirm">{{ unresolved ? '重试原撤回请求' : '确认故障撤回' }}</el-button></template>
    </el-dialog>
  </section>
</template>
