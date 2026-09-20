import { onBeforeUnmount, onMounted, ref } from 'vue';
import { ApiError, currentRecoveryEpoch, errorText, request } from '../api';
import { activeJob, exportSchema, exportsSchema, pendingSchema } from './contracts';
import type { ExportFormat, ExportJob, PendingExport } from './contracts';
import { downloadExport } from './download';

export function useOrderExports(scope: { actor: string; warehouse: string; order: string }) {
  const jobs = ref<ExportJob[]>([]); const error = ref(''); const busy = ref(false); const loading = ref(false);
  const downloading = ref(''); const next = ref<string | null>(null); const pending = ref<PendingExport | null>(null);
  const storageKey = `r08-export:${scope.actor}:${scope.warehouse}:${scope.order}`;
  const endpoint = `/orders/${scope.order}/exports?warehouseId=${scope.warehouse}`;
  const abort = new AbortController(); let alive = true; let timer = 0;
  function restore(): void {
    try { const raw = localStorage.getItem(storageKey); if (raw) pending.value = pendingSchema.parse(JSON.parse(raw)); }
    catch { error.value = '无法恢复原导出请求，请核对文件列表。'; }
  }
  function save(value: PendingExport | null): void {
    if (value) localStorage.setItem(storageKey, JSON.stringify(value)); else localStorage.removeItem(storageKey);
    pending.value = value;
  }
  function schedule(): void {
    clearTimeout(timer);
    if (alive && jobs.value.some(activeJob)) timer = window.setTimeout(() => { void poll(); }, 2500);
  }
  async function poll(): Promise<void> {
    try {
      for (const job of jobs.value.filter(activeJob)) {
        const updated = await request(`/exports/${job.id}?warehouseId=${scope.warehouse}`, exportSchema, { signal: abort.signal });
        if (!alive) return;
        jobs.value = jobs.value.map(item => item.id === updated.id ? updated : item);
      }
      schedule();
    } catch (cause) { if (alive) error.value = errorText(cause); }
  }
  async function load(older = false): Promise<void> {
    if (loading.value) return;
    loading.value = true; error.value = '';
    try {
      const result = await request(endpoint + (older && next.value ? `&before=${next.value}` : ''), exportsSchema, { signal: abort.signal });
      if (!alive) return;
      jobs.value = older ? [...jobs.value, ...result.items] : result.items; next.value = result.nextCursor; schedule();
    } catch (cause) { if (alive) error.value = errorText(cause); }
    finally { if (alive) loading.value = false; }
  }
  async function create(format: ExportFormat): Promise<void> {
    if (busy.value) return;
    busy.value = true; error.value = '';
    try {
      if (!pending.value) save({ key: crypto.randomUUID(), format, epoch: currentRecoveryEpoch() });
      const original = pending.value;
      if (!original) return;
      const job = await request(endpoint, exportSchema, { method: 'POST', signal: abort.signal,
        body: JSON.stringify({ format: original.format, basis: 'ORIGINAL_CONFIRMED' }), headers: {
          'Content-Type': 'application/json', 'Idempotency-Key': original.key, 'X-Server-Recovery-Epoch': original.epoch,
        } });
      if (!alive) return;
      save(null); jobs.value = [job, ...jobs.value.filter(item => item.id !== job.id)]; schedule();
    } catch (cause) {
      if (!alive) return;
      if (cause instanceof ApiError && cause.status < 500 && cause.status !== 401) save(null);
      error.value = errorText(cause);
    } finally { if (alive) busy.value = false; }
  }
  async function download(job: ExportJob): Promise<void> {
    if (downloading.value) return;
    downloading.value = job.id; error.value = '';
    try { await downloadExport(job, scope.warehouse, abort.signal); }
    catch (cause) { if (alive) error.value = errorText(cause); }
    finally { if (alive) downloading.value = ''; }
  }
  onMounted(() => { restore(); void load(); });
  onBeforeUnmount(() => { alive = false; abort.abort(); clearTimeout(timer); });
  return { jobs, error, busy, loading, downloading, next, pending, load, create, download };
}
