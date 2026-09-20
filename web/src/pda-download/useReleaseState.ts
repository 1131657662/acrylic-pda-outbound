import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue';
import { DownloadError } from './contracts';
import type { DownloadProblem, VerifiedRelease } from './contracts';
import { readRelease } from './release';

export function useReleaseState() {
  const current = ref<VerifiedRelease | null>(null);
  const problem = ref<DownloadProblem | null>(null); const phase = ref<'idle' | 'loading' | 'downloading'>('idle');
  const notice = ref(''); const controller = shallowRef<AbortController | null>(null);
  const busy = computed(() => phase.value !== 'idle');
  function failed(cause: unknown): void {
    current.value = null; notice.value = '';
    problem.value = cause instanceof DownloadError ? cause.kind : 'network';
  }
  async function refresh(): Promise<void> {
    if (busy.value) return;
    phase.value = 'loading'; current.value = null; problem.value = null; notice.value = '';
    const request = new AbortController(); controller.value = request;
    const timeout = window.setTimeout(() => request.abort(), 15_000);
    try { current.value = await readRelease(request.signal); }
    catch (cause) { failed(cause); }
    finally { window.clearTimeout(timeout); phase.value = 'idle'; }
  }
  function visible(): void { if (document.visibilityState === 'visible') void refresh(); }
  onMounted(() => { void refresh(); document.addEventListener('visibilitychange', visible); });
  onBeforeUnmount(() => { controller.value?.abort(); document.removeEventListener('visibilitychange', visible); });
  return { current, problem, phase, notice, busy, controller, failed, refresh };
}
