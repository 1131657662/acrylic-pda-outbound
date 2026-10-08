import { z } from 'zod';

export const maxAttachmentBytes = 10 * 1024 * 1024;
export const maxAttachmentCommandBytes = 50 * 1024 * 1024;
export const attachmentRefSchema = z.object({ attachmentId: z.uuid(), evidenceSha256: z.string().regex(/^[a-f0-9]{64}$/),
  sizeBytes: z.string().regex(/^[1-9][0-9]{0,7}$/).refine(value => Number(value) <= maxAttachmentBytes) }).strict();
export const attachmentRefsSchema = z.array(attachmentRefSchema).min(1).max(5)
  .refine(items => new Set(items.map(item => item.attachmentId)).size === items.length)
  .refine(items => items.reduce((total, item) => total + Number(item.sizeBytes), 0) <= maxAttachmentCommandBytes);
export type AttachmentRef = z.infer<typeof attachmentRefSchema>;

export async function attachmentHash(file: Blob): Promise<string> {
  if (file.size < 1 || file.size > maxAttachmentBytes) throw new Error('单个附件须为 1 字节至 10MiB。');
  const hash = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
  return Array.from(new Uint8Array(hash), byte => byte.toString(16).padStart(2, '0')).join('');
}
