import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { request, errorText } from '../api';
import { useSessionStore } from '../session';
import { orderPageSchema } from './contracts';
import type { Order } from './contracts';
import { parseOrderQuery, orderRouteQuery, contextKey } from './list-context';
import type { OrderQuery } from './list-context';

export function useOrderList() {
  const route = useRoute(); const router = useRouter(); const session = useSessionStore();
  const rows = ref<Order[]>([]); const total = ref(0); const loaded = ref(false); const busy = ref(false);
  const error = ref(''); const notice = ref(''); const receivedAt = ref(''); let generation = 0;
  const query = computed(() => parseOrderQuery(route.query));
  const key = computed(() => contextKey(session.identity?.id ?? '', session.warehouseId, query.value));
  const applied = ref<OrderQuery>(query.value);
  async function load(): Promise<void> {
    const current = ++generation;
    if (!session.warehouseId || !session.identity?.permissions.includes('order:read')) { busy.value = false; return; }
    busy.value = true; error.value = '';
    const requested = { ...query.value };
    try {
      const params = new URLSearchParams({ warehouseId: session.warehouseId, ...requested, page: String(requested.page), pageSize: String(requested.pageSize) });
      const result = await request(`/orders?${params}`, orderPageSchema);
      if (current !== generation) return;
      rows.value = result.items; total.value = result.total; loaded.value = true; applied.value = requested; receivedAt.value = new Date().toISOString();
      if (result.page !== requested.page) {
        notice.value = '结果已更新，已返回最后一页。';
        await router.replace({ query: orderRouteQuery({ ...requested, page: result.page }, session.warehouseId) });
      }
    } catch (cause) { if (current === generation) error.value = errorText(cause); }
    finally { if (current === generation) busy.value = false; }
  }
  function change(update: Partial<OrderQuery>): void {
    notice.value = '';
    const next = { ...query.value, ...update };
    if (JSON.stringify(next) === JSON.stringify(query.value)) { void load(); return; }
    void router.push({ path: '/orders', query: orderRouteQuery(next, session.warehouseId) });
  }
  watch(() => [session.warehouseId, session.identity?.id], () => { rows.value = []; loaded.value = false; total.value = 0; });
  watch(() => [route.fullPath, session.warehouseId, session.identity?.id], () => { void load(); }, { immediate: true });
  onBeforeUnmount(() => { generation++; });
  return { rows, total, loaded, busy, error, notice, receivedAt, query, applied, key, load, change };
}
