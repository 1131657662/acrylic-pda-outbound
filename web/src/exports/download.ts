import { z } from 'zod';
import { ApiError } from '../api';
import type { ExportJob } from './contracts';

export async function downloadExport(job: ExportJob, warehouse: string, signal: AbortSignal): Promise<void> {
  const response = await fetch(`/api/v1/exports/${job.id}/file?warehouseId=${warehouse}`, { credentials: 'same-origin', signal });
  if (!response.ok) {
    const body = z.object({ code: z.string(), message: z.string() }).safeParse(await response.json());
    throw new ApiError(response.status, body.success ? body.data.code : 'DOWNLOAD_FAILED',
      body.success ? body.data.message : '下载失败，请刷新文件状态后重试。');
  }
  const bytes = await response.arrayBuffer();
  const hash = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)), b => b.toString(16).padStart(2, '0')).join('');
  if (hash !== job.sha256 || bytes.byteLength !== job.bytes) throw new Error('文件摘要不一致');
  if (signal.aborted) return;
  const url = URL.createObjectURL(new Blob([bytes], { type: response.headers.get('Content-Type') ?? 'application/octet-stream' }));
  const anchor = document.createElement('a'); anchor.href = url;
  const prefix = job.format === 'SCAN_XLSX' ? 'scan-records-v1' : 'original-v1';
  anchor.download = `${prefix}-${job.id}.${job.format === 'PDF' ? 'pdf' : 'xlsx'}`;
  document.body.append(anchor); anchor.click(); anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
