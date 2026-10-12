import { ApiError } from '../api';
import { attachmentSchema } from './contracts';
import type { ReconciliationOwner } from './pending';
import type { z } from 'zod';

export async function downloadEvidence(input: { owner: ReconciliationOwner; attachment: z.infer<typeof attachmentSchema>;
  review: boolean; assertCurrent: () => void }): Promise<void> {
  input.assertCurrent(); const item = attachmentSchema.parse(input.attachment);
  const path = `/api/v1/corrections/attachments/${item.attachmentId}${input.review ? '/review' : ''}?warehouseId=${input.owner.warehouse}`;
  const response = await fetch(path, { credentials: 'same-origin', cache: 'no-store' });
  if (!response.ok) throw new ApiError(response.status, 'ATTACHMENT_READ_FAILED', '原始凭据读取失败，请重新核对权限和原案件。');
  const bytes = await response.arrayBuffer(); input.assertCurrent();
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  const hash = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
  if (String(bytes.byteLength) !== item.sizeBytes || hash !== item.evidenceSha256) throw new Error('原始凭据字节不可核验。');
  input.assertCurrent(); const url = URL.createObjectURL(new Blob([bytes], { type: 'application/octet-stream' }));
  const link = document.createElement('a'); link.href = url; link.download = `${item.attachmentId}.bin`;
  try { link.click(); } finally { URL.revokeObjectURL(url); }
}
