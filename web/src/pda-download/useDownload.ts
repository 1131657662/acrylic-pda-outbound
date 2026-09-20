import { readRelease, sameRelease } from './release';
import { readApk, saveApk } from './apk';
import { useReleaseState } from './useReleaseState';

async function download(state: ReturnType<typeof useReleaseState>): Promise<void> {
    const { current, busy, phase, notice, problem, controller, failed } = state;
    if (busy.value || !current.value) return;
    const displayed = current.value; phase.value = 'downloading'; notice.value = ''; problem.value = null;
    const request = new AbortController(); controller.value = request;
    const timeout = window.setTimeout(() => request.abort(), 120_000);
    try {
      const latest = await readRelease(request.signal); current.value = latest;
      if (!sameRelease(displayed, latest)) { notice.value = '发布信息已更新，请核对当前版本后再次下载。'; return; }
      const blob = await readApk(latest, request.signal);
      const confirmed = await readRelease(request.signal); current.value = confirmed;
      if (!sameRelease(latest, confirmed)) { notice.value = '发布信息已更新，请核对当前版本后再次下载。'; return; }
      if (request.signal.aborted) return;
      saveApk(blob, latest.release.releaseId);
      notice.value = '安装包已校验并交给浏览器保存，请在 Android 系统安装器中确认安装。';
    } catch (cause) { failed(cause); }
    finally { window.clearTimeout(timeout); phase.value = 'idle'; }
}

export function useDownload() {
  const state = useReleaseState();
  return { ...state, download: () => download(state) };
}
