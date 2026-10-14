import { z } from 'zod';
const hash = z.string().regex(/^[0-9a-f]{64}$/);
export const attachmentSchema = z.object({ attachmentId: z.uuid(), evidenceSha256: hash,
  sizeBytes: z.string().regex(/^[1-9][0-9]{0,7}$/).refine(value => Number(value) <= 10 * 1024 * 1024) }).strict();
export const observationSchema = z.object({ captureId: z.uuid(), status: z.enum(['LOADED', 'UNLOADED', 'NOT_FOUND']), note: z.string().max(500) }).strict()
  .refine(row => row.status === 'LOADED' || row.note.trim().length > 0, '卸下或未找到须说明');
export const evidenceSchema = z.object({ contextSha256: hash, checkedAt: z.iso.datetime(), area: z.string().trim().min(1).max(200),
  reason: z.string().trim().min(1).max(500), packages: z.array(observationSchema).max(10000), attachments: z.array(attachmentSchema).min(1).max(5) }).strict()
  .refine(body => new Set(body.packages.map(row => row.captureId)).size === body.packages.length)
  .refine(body => new Set(body.attachments.map(row => row.attachmentId)).size === body.attachments.length);
export const physicalResultSchema = z.object({
  receipt: z.object({ purpose: z.literal('INDEPENDENT_PHYSICAL_EVIDENCE_V1'), receiptId: z.uuid(), warehouseId: z.uuid(), relayId: z.uuid(),
    supervisorId: z.uuid(), taskId: z.uuid(), archiveSha256: hash, closureSha256: hash, requestSha256: hash, contextSha256: hash,
    recordedAt: z.iso.datetime(), signedProof: z.string().min(1), signingKeyId: z.string().min(1), physicalCheckRecorded: z.literal(true),
    pendingReconciliation: z.literal(true), associationAuthorized: z.literal(false), stockAvailable: z.literal(false),
    businessProcessed: z.literal(false), releaseAllowed: z.literal(false), localDeletionAllowed: z.literal(false) }).strict(),
  evidence: evidenceSchema, originalContext: z.record(z.string(), z.unknown()),
}).strict();
export type PhysicalEvidence = z.infer<typeof evidenceSchema>;
export type PhysicalResult = z.infer<typeof physicalResultSchema>;
export type PhysicalAttachment = z.infer<typeof attachmentSchema>;
export interface PhysicalOwner { userId: string; warehouseId: string; relayId: string }
function canonical(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(canonical);
  if (value !== null && typeof value === 'object') return Object.fromEntries(Object.entries(value).sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0).map(([key, row]) => [key, canonical(row)]));
  return value;
}
export async function digest(bytes: BufferSource): Promise<string> {
  return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))).map(byte => byte.toString(16).padStart(2, '0')).join('');
}
export async function jsonDigest(value: unknown): Promise<string> { return digest(new TextEncoder().encode(JSON.stringify(canonical(value)))); }
export async function verifyPhysicalResult(result: PhysicalResult, owner: PhysicalOwner, evidence: PhysicalEvidence): Promise<void> {
  const r = result.receipt;
  if (r.relayId !== owner.relayId || r.warehouseId !== owner.warehouseId || r.supervisorId !== owner.userId ||
      r.requestSha256 !== await jsonDigest(evidence) || r.requestSha256 !== await jsonDigest(result.evidence) ||
      r.contextSha256 !== evidence.contextSha256 || r.contextSha256 !== await jsonDigest(result.originalContext))
    throw new Error('实物回执与原身份、原请求或完整上下文不一致，请保留原键核对。');
}
