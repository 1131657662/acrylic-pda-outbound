import { onBeforeUnmount, onMounted, ref } from 'vue';
import { errorText, request } from '../api';
import { temporaryCandidatesSchema, type TemporaryCandidates } from './temporary-candidates';

interface Scope { warehouse: string; order: string; revision: number; version: string }
export function useTemporaryCandidates(scope: Scope) {
  const result = ref<TemporaryCandidates | null>(null); const busy = ref(false); const error = ref('');
  const from = ref(''); const through = ref(''); let generation = 0;
  async function load(page = 1): Promise<void> {
    const current = ++generation; busy.value = true; error.value = ''; result.value = null;
    try {
      const params = new URLSearchParams({ warehouseId: scope.warehouse, page: String(page) });
      if (from.value || through.value) { params.set('fromDate', from.value); params.set('throughDate', through.value); }
      const data = await request(`/orders/${scope.order}/temporary-candidates?${params}`, temporaryCandidatesSchema);
      if (current !== generation) return;
      if (data.orderId !== scope.order || data.revision !== scope.revision || data.orderVersion !== scope.version) {
        error.value = '正式订单已变化，请先刷新订单，再核对临时单候选。'; return;
      }
      result.value = data; from.value = data.range.fromDate; through.value = data.range.throughDate;
    } catch (cause) { if (current === generation) error.value = errorText(cause); }
    finally { if (current === generation) busy.value = false; }
  }
  onMounted(() => { void load(); });
  onBeforeUnmount(() => { generation++; });
  return { result, busy, error, from, through, load };
}
