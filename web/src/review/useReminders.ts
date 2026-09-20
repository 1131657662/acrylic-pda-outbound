import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ApiError, errorText, request } from '../api';
import { useSessionStore } from '../session';
import { pageSchema } from '../inventory/master-contracts';
import { reminderSchema, type Reminder } from './contracts';

const previousPositions = new Map<string, string>();
export function useReminders() {
  const route = useRoute(); const router = useRouter(); const session = useSessionStore();
  const rows = ref<Reminder[]>([]); const next = ref<string | null>(null);
  const busy = ref(false); const loaded = ref(false); const error = ref(''); const receivedAt = ref('');
  const cursor = computed(() => typeof route.query.cursor === 'string' ? route.query.cursor : '');
  const scope = computed(() => JSON.stringify([session.identity?.id, session.warehouseId]));
  const key = computed(() => `${scope.value}:${cursor.value}`);
  const previous = computed(() => previousPositions.get(key.value));
  const authorized = computed(() => session.identity?.permissions.some(p => ['submission:review', 'temporary_order:confirm'].includes(p)));
  let generation = 0;
  async function load(): Promise<void> {
    const current = ++generation;
    if (!session.warehouseId || !authorized.value) return;
    busy.value = true; error.value = '';
    try {
      const params = new URLSearchParams({ warehouseId: session.warehouseId, limit: '20', cursor: cursor.value });
      const result = await request(`/review-reminders?${params}`, pageSchema(reminderSchema));
      if (current !== generation) return;
      rows.value = result.items; next.value = result.nextCursor; loaded.value = true; receivedAt.value = new Date().toISOString();
    } catch (cause) {
      if (current !== generation) return;
      error.value = errorText(cause);
      if (cause instanceof ApiError && [401, 403, 404].includes(cause.status)) { rows.value = []; next.value = null; loaded.value = false; }
    } finally { if (current === generation) busy.value = false; }
  }
  function go(value: string): void {
    if (busy.value) return;
    if (value && value === next.value) {
      if (previousPositions.size >= 200) previousPositions.clear();
      previousPositions.set(`${scope.value}:${value}`, cursor.value);
    }
    void router.push({ path: '/review-reminders', query: { warehouse: session.warehouseId, cursor: value || undefined } });
  }
  watch(() => [key.value, JSON.stringify(session.identity?.permissions)], () => {
    rows.value = []; next.value = null; loaded.value = false; error.value = ''; busy.value = false; void load();
  }, { immediate: true });
  onBeforeUnmount(() => { generation++; });
  return { rows, next, busy, loaded, error, receivedAt, cursor, key, previous, go, load };
}
