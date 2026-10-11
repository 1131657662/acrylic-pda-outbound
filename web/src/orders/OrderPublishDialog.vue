<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { request, errorText, ApiError, currentRecoveryEpoch } from '../api';
import { useSessionStore } from '../session';
import { currentPolicySchema } from '../policies/contracts';
import type { Policy } from '../policies/contracts';
import { receiptSchema } from './contracts';
import type { Detail } from './contracts';
import { loadPublication, savePublication, clearPublication, lockPublication, publicationCommandSchema } from './publication-pending';
import type { PublicationOwner, SavedPublication } from './publication-pending';
const props = defineProps<{ warehouseId: string; detail: Detail }>();
const emit = defineEmits<{ saved: []; cancel: [] }>();
const policy = ref<Policy | null>(null); const busy = ref(false); const loading = ref(true);
const session = useSessionStore(); const saved = ref<SavedPublication | null>(null);
const reason = ref(''); const error = ref(''); const storageBlocked = ref(false);
const uncertain = computed(() => !!saved.value && !saved.value.receipt);
function owner(): PublicationOwner {
  if (!session.identity || session.warehouseId !== props.warehouseId) throw new Error('身份或仓库已变化，请恢复原身份');
  return { userId: session.identity.id, warehouseId: props.warehouseId, orderId: props.detail.order.id };
}
function assertOwner(expected: PublicationOwner): void {
  const now = owner();
  if (now.userId !== expected.userId || now.warehouseId !== expected.warehouseId || now.orderId !== expected.orderId)
    throw new Error('身份或订单已变化，原发布请求未发送');
}
function prepare(expected: PublicationOwner): SavedPublication {
  const pending = loadPublication(expected);
  if (pending && !pending.receipt) return pending;
  if (!policy.value) throw new Error('尚未读取出库规则，请重新核对');
  const body = JSON.stringify({ expectedVersion: props.detail.order.rowVersion, expectedPolicyVersion: policy.value.version, reason: reason.value.trim() });
  const next = { ...expected, key: crypto.randomUUID(), epoch: currentRecoveryEpoch(), body, receipt: null };
  savePublication(next); return next;
}
async function send(expected: PublicationOwner): Promise<void> {
  assertOwner(expected); saved.value = prepare(expected); const pending = saved.value;
  const receipt = await request(`/orders/${expected.orderId}/publish?warehouseId=${expected.warehouseId}`, receiptSchema,
    { method: 'POST', body: pending.body, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': pending.key,
      'X-Server-Recovery-Epoch': pending.epoch } }, () => assertOwner(expected));
  assertOwner(expected);
  if (receipt.orderId !== expected.orderId) throw new Error('原发布回执订单不符，请核对原请求');
  const completed = { ...pending, receipt }; savePublication(completed); saved.value = completed; emit('saved');
}
async function publish(): Promise<void> {
  if (busy.value || storageBlocked.value || !uncertain.value && (!policy.value || !reason.value.trim())) return;
  const expected = owner(); busy.value = true; error.value = '';
  try { await lockPublication(expected, () => send(expected)); }
  catch (cause) {
    if (cause instanceof ApiError && cause.status >= 400 && cause.status < 500 && cause.status !== 401 && cause.code !== 'IDEMPOTENCY_CONTENT_MISMATCH') {
      try { assertOwner(expected); clearPublication(expected); saved.value = null; } catch { /* Keep the original request on identity/storage failure. */ }
    }
    error.value = cause instanceof Error && !(cause instanceof ApiError) ? cause.message : errorText(cause);
  } finally { busy.value = false; }
}
watch(() => [session.identity?.id, props.warehouseId, props.detail.order.id], async () => {
  saved.value = null; policy.value = null; error.value = ''; storageBlocked.value = false; loading.value = true;
  try {
    const expected = owner(); saved.value = loadPublication(expected);
    if (uncertain.value && saved.value) { reason.value = publicationCommandSchema.parse(JSON.parse(saved.value.body)).reason; return; }
    const result = await request(`/policies/current?warehouseId=${expected.warehouseId}`, currentPolicySchema);
    assertOwner(expected); policy.value = result.policy;
  } catch (cause) { storageBlocked.value = true; error.value = cause instanceof Error ? cause.message : errorText(cause); }
  finally { loading.value = false; }
}, { immediate: true });
</script>
<template>
  <el-dialog :model-value="true" title="发布装车订单" width="580" :close-on-click-modal="false" :show-close="!busy && !uncertain"
    :close-on-press-escape="!busy && !uncertain" @close="emit('cancel')">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="uncertain" title="发布结果未确认，请重试原发布请求。" type="warning" :closable="false" />
    <div v-loading="loading"><h3>{{ detail.plan.vehicleNo }} · V{{ detail.plan.revision }} · {{ detail.plan.plannedTotal }} 包</h3>
      <p v-if="policy">固定规则 V{{ policy.version }}：自动扣库存<strong>{{ policy.autoDeductInventory ? '开启' : '关闭' }}</strong></p>
      <el-alert v-else-if="!loading && !uncertain" title="请先在“出库规则”中完成设置。" type="warning" :closable="false" />
      <p class="muted">发布后进入待领取。计划单生成任务同时建立；库存候选不足仍需现场核对，发布不会预留具体包。</p>
      <el-input v-model="reason" aria-label="发布原因" placeholder="填写发布原因" maxlength="500" :disabled="busy || uncertain" />
    </div>
    <template #footer><el-button :disabled="busy || uncertain" @click="emit('cancel')">取消</el-button>
      <el-button type="primary" :loading="busy" :disabled="loading || storageBlocked || !uncertain && (!policy || !reason.trim())" @click="publish">{{ uncertain ? '重试原发布请求' : '确认发布' }}</el-button></template>
  </el-dialog>
</template>
