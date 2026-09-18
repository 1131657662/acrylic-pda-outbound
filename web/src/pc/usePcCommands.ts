import { onBeforeUnmount, ref } from 'vue';
import { z } from 'zod';
import { request, errorText, ApiError, currentRecoveryEpoch } from '../api';
import { receiptSchema } from './contracts';
import { readPending, savePending, clearPending, actionCodes, type Context, type Pending, type Action } from './pending';

export function usePcCommands(context: Context, refresh: () => Promise<void>) {
  const pending = ref<Pending | null>(null); const busy = ref(false); const error = ref(''); const notice = ref('');
  const storageError = ref('');
  function reload(): void {
    try { pending.value = readPending(context); storageError.value = ''; }
    catch (cause) { storageError.value = errorText(cause); }
  }
  reload(); window.addEventListener('storage', reload); onBeforeUnmount(() => window.removeEventListener('storage', reload));
  async function send(action: Action, body: object): Promise<boolean> {
    reload(); if (busy.value || pending.value || storageError.value) return false;
    const record = { ...context, key: crypto.randomUUID(), epoch: currentRecoveryEpoch(), action, body: JSON.stringify(body) };
    try { savePending(record); pending.value = record; }
    catch { storageError.value = '浏览器无法保存恢复资料，本次请求未发送。请恢复浏览器存储后重试。'; return false; }
    return execute(record, false);
  }
  async function execute(record: Pending, recovering: boolean): Promise<boolean> {
    busy.value = true; error.value = ''; notice.value = '';
    let writing = false;
    try {
      const base = `/orders/${context.order}/pc`;
      const recovered = recovering ? await request(`${base}/requests/${record.key}?warehouseId=${context.warehouse}`, z.object({ receipt: receiptSchema.nullable() })) : null;
      writing = !recovered?.receipt;
      const receipt = recovered?.receipt ?? await request(`${base}/${record.action}?warehouseId=${context.warehouse}`, receiptSchema,
        { method: 'POST', body: record.body, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': record.key, 'X-Server-Recovery-Epoch': record.epoch } });
      if (receipt.requestKey !== record.key || receipt.orderId !== record.order || receipt.action !== actionCodes[record.action]) throw new Error('后台回执与原操作不符，请保留原请求并核对。');
      clearPending(record); pending.value = null; notice.value = record.action === 'entries' ? '已计入本单。结单并由后台确认出库后才能放行。' : '后台已确认此操作。';
      await refresh(); return true;
    } catch (cause) {
      const safeFailure = writing && cause instanceof ApiError && [400, 404, 409].includes(cause.status) && !['IDEMPOTENCY_CONTENT_MISMATCH', 'SERVER_RECOVERY_EPOCH_MISMATCH', 'ORDER_RECOVERY_REQUIRED'].includes(cause.code);
      if (safeFailure) { try { clearPending(record); pending.value = null; } catch { storageError.value = '恢复资料未能清理，请核对原操作。'; } }
      error.value = errorText(cause); return false;
    } finally { busy.value = false; }
  }
  async function recover(): Promise<boolean> { reload(); return pending.value && !busy.value ? execute(pending.value, true) : false; }
  return { pending, busy, error, notice, storageError, send, recover, reload };
}
