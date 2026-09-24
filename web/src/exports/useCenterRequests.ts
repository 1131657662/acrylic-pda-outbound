import { onBeforeUnmount, ref } from 'vue';
import { ElMessageBox } from 'element-plus';
import { z } from 'zod';
import { ApiError, currentRecoveryEpoch, errorText, request } from '../api';
import { exportSchema, rangeSchema, type ExportJob } from './contracts';
import type { ExportRange } from './summary-contracts';

const actionSchema = z.discriminatedUnion('kind', [
  z.object({ kind: z.literal('range'), format: z.enum(['XLSX', 'PDF']), range: rangeSchema }),
  z.object({ kind: z.literal('retry'), id: z.string().uuid() }),
]);
const pendingSchema = z.object({ key: z.string().uuid(), epoch: z.string().min(1), action: actionSchema });
type Pending = z.infer<typeof pendingSchema>;
const unaccepted = new Set(['EXPORT_RANGE_INVALID', 'EXPORT_FORMAT_UNSUPPORTED', 'EXPORT_LIMIT_EXCEEDED',
  'EXPORT_QUEUE_FULL', 'EXPORT_NOT_RETRYABLE', 'EXPORT_TEMPLATE_UNAVAILABLE', 'EXPORT_SNAPSHOT_UNAVAILABLE']);

export function useCenterRequests(scope: { actor: string; warehouse: string }, accepted: (job: ExportJob) => void) {
  const busy = ref(false); const pending = ref<Pending | null>(null); const error = ref('');
  const key = `r08-center:${scope.actor}:${scope.warehouse}`; const abort = new AbortController();
  try { const saved = localStorage.getItem(key); if (saved) pending.value = pendingSchema.parse(JSON.parse(saved)); }
  catch { error.value = '无法恢复原导出申请，请核对任务列表。'; }
  async function send(action: Pending['action']): Promise<void> {
    if (busy.value) return;
    busy.value = true; error.value = '';
    try {
      if (!pending.value) {
        const value = pendingSchema.parse({ key: crypto.randomUUID(), epoch: currentRecoveryEpoch(), action });
        localStorage.setItem(key, JSON.stringify(value)); pending.value = value;
      }
      const original = pending.value;
      const path = original.action.kind === 'retry' ? `/exports/${original.action.id}/retry` : '/exports';
      const body = original.action.kind === 'range' ? JSON.stringify({ ...original.action, basis: 'ORIGINAL_CONFIRMED', kind: undefined }) : undefined;
      const job = await request(`${path}?warehouseId=${scope.warehouse}`, exportSchema, { method: 'POST', body, signal: abort.signal,
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': original.key, 'X-Server-Recovery-Epoch': original.epoch } });
      if (!abort.signal.aborted) { localStorage.removeItem(key); pending.value = null; accepted(job); }
    } catch (cause) {
      if (abort.signal.aborted) return;
      const rejected = cause instanceof ApiError && unaccepted.has(cause.code);
      if (rejected) { localStorage.removeItem(key); pending.value = null; }
      error.value = `${errorText(cause)}${rejected ? ' 本次申请明确未受理，可重新申请。' : ' 原请求结果尚未确认，请核对任务列表后再决定。'}`;
    } finally { if (!abort.signal.aborted) busy.value = false; }
  }
  async function discard(): Promise<void> {
    if (busy.value || !pending.value) return;
    try {
      await ElMessageBox.confirm('原请求可能已受理。请先核对任务列表；放弃原键后再次申请可能产生重复文件。',
        '放弃原请求', { confirmButtonText: '确认放弃', cancelButtonText: '继续保留', type: 'warning' });
    } catch { return; }
    localStorage.removeItem(key); pending.value = null; error.value = '';
  }
  onBeforeUnmount(() => abort.abort());
  return { busy, pending, error, discard, resume: () => pending.value && send(pending.value.action),
    create: (format: 'XLSX' | 'PDF', range: ExportRange) => send({ kind: 'range', format, range }),
    retry: (job: ExportJob) => send({ kind: 'retry', id: job.id }) };
}
