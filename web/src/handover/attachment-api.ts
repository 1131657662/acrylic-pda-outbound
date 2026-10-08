import { z } from 'zod';
import { ApiError, request } from '../api';
import { attachmentHash, attachmentRefSchema, type AttachmentRef } from './attachment-contracts';
import type { AttachmentUpload } from './attachment-storage';
import type { FaultOwner } from './pending-fault';

const csrfSchema = z.object({ headerName: z.string(), token: z.string() });
const envelope = z.object({ code: z.string(), message: z.string(), data: z.unknown(), requestId: z.string() });
interface UploadInput { owner: FaultOwner; saved: AttachmentUpload; file: File; assertCurrent: () => void }

/** Reuse the saved upload key and epoch; ownership is checked after hashing and after the CSRF read. */
export async function uploadAttachment(input: UploadInput): Promise<AttachmentRef> {
  if (input.file.size !== input.saved.size || await attachmentHash(input.file) !== input.saved.sha)
    throw new Error('请选择与原上传摘要相同的文件，原上传键已保留。');
  input.assertCurrent();
  const csrf = await request('/auth/csrf', csrfSchema); input.assertCurrent();
  const headers = new Headers({ 'Content-Type': 'application/octet-stream', 'X-Content-SHA256': input.saved.sha,
    'X-Server-Recovery-Epoch': input.saved.epoch }); headers.set(csrf.headerName, csrf.token);
  const path = `/api/v1/handovers/${input.owner.caseId}/attachments/${input.saved.key}?warehouseId=${input.owner.warehouseId}`;
  const response = await fetch(path, { method: 'PUT', body: input.file, credentials: 'same-origin', headers });
  input.assertCurrent(); const body = envelope.parse(await response.json());
  if (!response.ok) throw new ApiError(response.status, body.code, `${body.message}（请求 ${body.requestId}）`);
  const ref = attachmentRefSchema.parse(body.data);
  if (ref.evidenceSha256 !== input.saved.sha || Number(ref.sizeBytes) !== input.saved.size) throw new Error('附件回执与原文件不一致，请保留原键重试。');
  return ref;
}

export function attachmentDownload(caseId: string, warehouseId: string, attachmentId: string): string {
  for (const id of [caseId, warehouseId, attachmentId]) z.uuid().parse(id);
  return `/api/v1/handovers/${caseId}/attachments/${attachmentId}?warehouseId=${warehouseId}`;
}
