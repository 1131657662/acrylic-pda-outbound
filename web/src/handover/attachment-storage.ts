import { z } from 'zod';
import { attachmentRefSchema, maxAttachmentCommandBytes } from './attachment-contracts';
import { FaultPersistenceError, type FaultOwner } from './pending-fault';

const uploadSchema = z.object({ key: z.uuid(), name: z.string().min(1).max(255), sha: z.string().regex(/^[a-f0-9]{64}$/),
  size: z.number().int().positive().max(10 * 1024 * 1024), epoch: z.string().regex(/^[1-9]\d*$/),
  reference: attachmentRefSchema.nullable() }).strict()
  .refine(row => !row.reference || (row.reference.evidenceSha256 === row.sha && Number(row.reference.sizeBytes) === row.size));
const savedSchema = z.object({ version: z.literal(1), userId: z.uuid(), warehouseId: z.uuid(), caseId: z.uuid(),
  uploads: z.array(uploadSchema).max(5).refine(rows => new Set(rows.map(row => row.key)).size === rows.length)
    .refine(rows => rows.reduce((total, row) => total + row.size, 0) <= maxAttachmentCommandBytes) }).strict();
export type AttachmentUpload = z.infer<typeof uploadSchema>;

function key(owner: FaultOwner): string {
  for (const id of [owner.userId, owner.warehouseId, owner.caseId]) z.uuid().parse(id);
  return `acrylic.handover-fault-attachments.v1:${owner.userId}:${owner.warehouseId}:${owner.caseId}`;
}
export function readUploads(owner: FaultOwner): AttachmentUpload[] {
  try {
    const raw = localStorage.getItem(key(owner)); if (raw === null) return [];
    const saved = savedSchema.parse(JSON.parse(raw));
    if (saved.userId !== owner.userId || saved.warehouseId !== owner.warehouseId || saved.caseId !== owner.caseId) throw new Error('Owner changed');
    return saved.uploads;
  } catch { throw new FaultPersistenceError('无法读取原附件上传键，请保留浏览器存储并恢复后重试。'); }
}
export function saveUploads(owner: FaultOwner, uploads: AttachmentUpload[]): void {
  try {
    const saved = savedSchema.parse({ ...owner, version: 1, uploads }); const value = JSON.stringify(saved); const name = key(owner);
    localStorage.setItem(name, value);
    if (localStorage.getItem(name) !== value) throw new Error('Readback failed');
  } catch { throw new FaultPersistenceError('无法保存原附件上传键，请恢复浏览器存储后重试；未发送新文件。'); }
}
