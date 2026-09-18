import { computed, onScopeDispose, ref, shallowRef } from 'vue';
import { errorText } from './api';

interface CursorPage<T> { items: T[]; nextCursor: string | null }

export function useCursorPage<T>(fetchPage: (cursor: string) => Promise<CursorPage<T>>) {
  const rows = shallowRef<T[]>([]);
  const busy = ref(false); const error = ref(''); const nextCursor = ref<string | null>(null);
  const page = ref(1); const cursors = ref(['']);
  let generation = 0; let requestedPage = 1;
  const hasPrevious = computed(() => page.value > 1);
  async function load(target = page.value): Promise<void> {
    const cursor = cursors.value[target - 1];
    if (cursor === undefined) return;
    const ticket = ++generation; requestedPage = target; busy.value = true; error.value = '';
    try {
      const result = await fetchPage(cursor);
      if (ticket !== generation) return;
      rows.value = result.items; nextCursor.value = result.nextCursor; page.value = target;
      cursors.value = cursors.value.slice(0, target);
      if (result.nextCursor) cursors.value.push(result.nextCursor);
    } catch (cause) { if (ticket === generation) error.value = errorText(cause); }
    finally { if (ticket === generation) busy.value = false; }
  }
  function reset(): Promise<void> {
    rows.value = []; nextCursor.value = null; page.value = 1; cursors.value = [''];
    return load(1);
  }
  onScopeDispose(() => { generation++; });
  return { rows, busy, error, nextCursor, page, hasPrevious, reset,
    reload: () => load(), retry: () => load(requestedPage), first: () => load(1),
    previous: () => load(page.value - 1), next: () => nextCursor.value ? load(page.value + 1) : Promise.resolve() };
}
