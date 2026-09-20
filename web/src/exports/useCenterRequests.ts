import { onBeforeUnmount, ref } from 'vue';
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
      localStorage.removeItem(key);
      if (!abort.signal.aborted) { pending.value = null; accepted(job); }
    } catch (cause) {
      if (abort.signal.aborted) return;
      if (cause instanceof ApiError && cause.status < 500 && cause.status !== 401) { localStorage.removeItem(key); pending.value = null; }
      error.value = errorText(cause);
    } finally { if (!abort.signal.aborted) busy.value = false; }
  }
  onBeforeUnmount(() => abort.abort());
  return { busy, pending, error, resume: () => pending.value && send(pending.value.action),
    create: (format: 'XLSX' | 'PDF', range: ExportRange) => send({ kind: 'range', format, range }),
    retry: (job: ExportJob) => send({ kind: 'retry', id: job.id }) };
}
