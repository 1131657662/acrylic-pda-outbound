import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { request, errorText } from '../api';
import { duplicatePageSchema, duplicateRangeError, type DuplicatePage } from './duplicate-contracts';

export function useTemporaryDuplicates(source: () => { submissionId: string; warehouseId: string }) {
  const data = ref<DuplicatePage | null>(null); const from = ref(''); const through = ref('');
  const busy = ref(false); const error = ref(''); const cursors = ref<string[]>(['']); const index = ref(0);
  const applied = ref({ fromDate: '', throughDate: '' }); let generation = 0;
  const changed = computed(() => from.value !== applied.value.fromDate || through.value !== applied.value.throughDate);
  async function load(cursor: string, page: number, fresh: boolean): Promise<void> {
    const ticket = ++generation; const scope = source(); busy.value = true; error.value = ''; data.value = null;
    const range = fresh ? { fromDate: from.value, throughDate: through.value } : applied.value;
    const params = new URLSearchParams({ warehouseId: scope.warehouseId, cursor, limit: '20' });
    if (range.fromDate || range.throughDate) { params.set('fromDate', range.fromDate); params.set('throughDate', range.throughDate); }
    try {
      const result = await request(`/temporary-reviews/${scope.submissionId}/duplicate-candidates?${params}`, duplicatePageSchema);
      if (ticket !== generation) return;
      data.value = result; applied.value = result.range; from.value = result.range.fromDate; through.value = result.range.throughDate;
      if (fresh) cursors.value = ['']; else cursors.value = [...cursors.value.slice(0, page), cursor];
      index.value = page;
    } catch (cause) { if (ticket === generation) error.value = errorText(cause); }
    finally { if (ticket === generation) busy.value = false; }
  }
  function search(): void {
    error.value = duplicateRangeError(from.value, through.value);
    if (error.value) { data.value = null; return; }
    void load('', 0, true);
  }
  function next(): void {
    if (!busy.value && !changed.value && data.value?.nextCursor) void load(data.value.nextCursor, index.value + 1, false);
  }
  function previous(): void {
    if (!busy.value && !changed.value && index.value > 0) void load(cursors.value[index.value - 1] ?? '', index.value - 1, false);
  }
  watch(source, () => { from.value = ''; through.value = ''; applied.value = { fromDate: '', throughDate: '' }; void load('', 0, true); }, { immediate: true });
  onBeforeUnmount(() => { generation++; });
  return { data, from, through, busy, error, index, changed, search, next, previous };
}
