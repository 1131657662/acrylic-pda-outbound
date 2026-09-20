import { onBeforeUnmount, ref } from 'vue';
import { errorText, request } from '../api';
import { rangeSchema } from './contracts';
import { summarySchema, type Summary, type ExportRange } from './summary-contracts';

export function useExportSummary(warehouse: string) {
  const summary = ref<Summary | null>(null); const loading = ref(false); const error = ref('');
  const abort = new AbortController(); let generation = 0;
  async function load(range: ExportRange): Promise<void> {
    const current = ++generation; summary.value = null; loading.value = true; error.value = '';
    try {
      const parsed = rangeSchema.safeParse(range);
      if (!parsed.success) { error.value = '请填写有效的开始和结束日期。'; return; }
      const params = new URLSearchParams({ warehouseId: warehouse, ...parsed.data });
      const result = await request(`/exports/summary?${params}`, summarySchema, { signal: abort.signal });
      if (!abort.signal.aborted && current === generation) summary.value = result;
    } catch (cause) { if (!abort.signal.aborted && current === generation) error.value = errorText(cause); }
    finally { if (current === generation) loading.value = false; }
  }
  onBeforeUnmount(() => { generation++; abort.abort(); });
  return { summary, loading, error, load };
}
