import { DownloadError } from './contracts';

export async function downloadResponse(path: string, signal: AbortSignal): Promise<Response> {
  try {
    const response = await fetch(path, { cache: 'no-store', credentials: 'omit', redirect: 'error', signal });
    if (response.status === 404) throw new DownloadError('unpublished');
    if (response.status === 410) throw new DownloadError('paused');
    if (!response.ok) throw new DownloadError('network');
    return response;
  } catch (cause) { throw cause instanceof DownloadError ? cause : new DownloadError('network'); }
}
export async function readLimited(response: Response, maximum: number): Promise<Uint8Array<ArrayBuffer>> {
  const reader = response.body?.getReader();
  if (!reader) throw new DownloadError('network');
  const chunks: Uint8Array[] = []; let size = 0;
  try {
    while (true) {
      const part = await reader.read();
      if (part.done) break;
      size += part.value.length;
      if (size > maximum) throw new DownloadError('invalid');
      chunks.push(part.value);
    }
  } finally { await reader.cancel().catch(() => undefined); reader.releaseLock(); }
  const result = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { result.set(chunk, offset); offset += chunk.length; }
  return result;
}
export async function readJson(path: string, signal: AbortSignal): Promise<unknown> {
  const response = await downloadResponse(path, signal);
  const bytes = await readLimited(response, 64 * 1024);
  try { return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes)); }
  catch (cause) { throw cause instanceof DownloadError ? cause : new DownloadError('invalid'); }
}
