import { onBeforeUnmount, onMounted, ref } from 'vue';
import { errorText, request } from '../api';
import { activeJob, exportSchema, exportsSchema, type ExportFormat, type ExportJob } from './contracts';
import { downloadExport } from './download';

export function useCenterJobs(warehouse: string) {
  const jobs = ref<ExportJob[]>([]); const next = ref<string | null>(null); const loading = ref(false); const loaded = ref(false);
  const error = ref(''); const downloading = ref(''); const format = ref<ExportFormat | ''>(''); const status = ref('');
  const abort = new AbortController(); let generation = 0; let timer = 0;
  let applied = { format: '', status: '' };
  function schedule(): void {
    clearTimeout(timer);
    if (!abort.signal.aborted && jobs.value.some(activeJob)) timer = window.setTimeout(() => { void poll(); }, 2500);
  }
  async function poll(): Promise<void> {
    const current = generation;
    try {
      for (const job of jobs.value.filter(activeJob)) {
        const updated = await request(`/exports/${job.id}?warehouseId=${warehouse}`, exportSchema, { signal: abort.signal });
        if (current !== generation || abort.signal.aborted) return;
        jobs.value = jobs.value.map(j => j.id === job.id ? updated : j).filter(j => !applied.status || j.status === applied.status);
      }
      schedule();
    } catch (cause) { if (!abort.signal.aborted && current === generation) error.value = errorText(cause); }
  }
  async function load(older = false): Promise<void> {
    if (older && (!next.value || loading.value)) return;
    const current = ++generation; loading.value = true; error.value = ''; clearTimeout(timer);
    const filter = older ? applied : { format: format.value, status: status.value };
    const params = new URLSearchParams({ warehouseId: warehouse });
    if (older && next.value) params.set('before', next.value);
    if (filter.format) params.set('format', filter.format);
    if (filter.status) params.set('status', filter.status);
    try {
      const result = await request(`/exports?${params}`, exportsSchema, { signal: abort.signal });
      if (current !== generation || abort.signal.aborted) return;
      applied = filter;
      jobs.value = older ? [...jobs.value, ...result.items] : result.items;
      next.value = result.nextCursor; loaded.value = true; schedule();
    } catch (cause) { if (!abort.signal.aborted && current === generation) error.value = errorText(cause); }
    finally { if (current === generation) loading.value = false; }
  }
  function accepted(job: ExportJob): void {
    generation++; clearTimeout(timer); format.value = ''; status.value = ''; loading.value = false;
    jobs.value = [job]; next.value = null; loaded.value = true; void load();
  }
  async function download(job: ExportJob): Promise<void> {
    if (downloading.value) return;
    downloading.value = job.id; error.value = '';
    try { await downloadExport(job, warehouse, abort.signal); }
    catch (cause) { if (!abort.signal.aborted) error.value = errorText(cause); }
    finally { if (!abort.signal.aborted) downloading.value = ''; }
  }
  onMounted(() => { void load(); });
  onBeforeUnmount(() => { generation++; abort.abort(); clearTimeout(timer); });
  return { jobs, next, loading, loaded, error, downloading, format, status, load, accepted, download };
}
