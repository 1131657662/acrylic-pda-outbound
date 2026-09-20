import type { VerifiedRelease } from './contracts';
import { DownloadError } from './contracts';
import { downloadResponse, readLimited } from './transport';

export async function readApk(verified: VerifiedRelease, signal: AbortSignal): Promise<Blob> {
  const { release } = verified;
  const response = await downloadResponse(`/${release.apkPath}`, signal);
  const bytes = await readLimited(response, release.sizeBytes);
  const hash = new Uint8Array(await crypto.subtle.digest('SHA-256', bytes));
  const sha256 = Array.from(hash, byte => byte.toString(16).padStart(2, '0')).join('');
  if (bytes.length !== release.sizeBytes || sha256 !== release.sha256) throw new DownloadError('invalid');
  return new Blob([bytes], { type: 'application/vnd.android.package-archive' });
}
export function saveApk(blob: Blob, releaseId: string): void {
  const url = URL.createObjectURL(blob); const link = document.createElement('a');
  link.href = url; link.download = `${releaseId}.apk`; document.body.append(link); link.click(); link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
