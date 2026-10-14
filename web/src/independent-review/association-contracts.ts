import { z } from 'zod';
import { jsonDigest, physicalResultSchema, type PhysicalOwner } from './physical-contracts';
import { conflictResultSchema, verifyAssociationConflict } from './conflict-contracts';

const hash = z.string().regex(/^[0-9a-f]{64}$/);
export const associationEvidenceSchema = z.object({ contextSha256: hash, physicalCheckId: z.uuid(), targetOrderId: z.uuid(),
  candidateExclusions: z.array(z.object({ orderId: z.uuid(), reason: z.string().trim().min(1).max(500) }).strict()).max(2000),
  reason: z.string().trim().min(1).max(500), conflictDecisionId: z.uuid().optional(),
  shortfallApproval: z.object({ originalShortfall: z.string().regex(/^(0|[1-9][0-9]{0,5})$/),
    targetShortfall: z.string().regex(/^(0|[1-9][0-9]{0,5})$/), reason: z.string().trim().min(1).max(500) }).strict().optional() }).strict()
  .refine(body => new Set(body.candidateExclusions.map(row => row.orderId)).size === body.candidateExclusions.length)
  .refine(body => body.candidateExclusions.every(row => row.orderId !== body.targetOrderId));
export const associationResultSchema = z.object({
  receipt: z.object({ purpose: z.literal('INDEPENDENT_ASSOCIATION_EVIDENCE_V1'), associationId: z.uuid(), warehouseId: z.uuid(),
    relayId: z.uuid(), deviceId: z.uuid(), taskId: z.uuid(), targetOrderId: z.uuid(), supervisorId: z.uuid(), physicalCheckId: z.uuid(),
    archiveSha256: hash, closureSha256: hash, requestSha256: hash, contextSha256: hash, recordedAt: z.iso.datetime(),
    signedProof: z.string().min(1), signingKeyId: z.string().min(1), associationRecorded: z.literal(true),
    pendingReconciliation: z.literal(true), newCollectionAuthorized: z.literal(false), stockAvailable: z.literal(false),
    businessProcessed: z.literal(false), releaseAllowed: z.literal(false), localDeletionAllowed: z.literal(false) }).strict(),
  evidence: associationEvidenceSchema, originalContext: z.record(z.string(), z.unknown()), physicalEvidence: physicalResultSchema,
  conflictEvidence: conflictResultSchema.optional(),
}).strict();
export type AssociationEvidence = z.infer<typeof associationEvidenceSchema>;
export type AssociationResult = z.infer<typeof associationResultSchema>;
const originalSchema = z.object({ warehouseId: z.uuid(), relayId: z.uuid(), deviceId: z.uuid(), taskId: z.uuid(),
  archiveSha256: hash, closureSha256: hash });

/** Digest/context binding for display; RSA signature verification remains on the server. */
export async function verifyAssociation(result: AssociationResult, owner: PhysicalOwner, body?: AssociationEvidence): Promise<void> {
  const r = result.receipt; const p = result.physicalEvidence; const original = originalSchema.parse(result.originalContext.original);
  if (r.warehouseId !== owner.warehouseId || r.relayId !== owner.relayId || original.warehouseId !== r.warehouseId ||
      original.relayId !== r.relayId || original.deviceId !== r.deviceId || original.taskId !== r.taskId ||
      original.archiveSha256 !== r.archiveSha256 || original.closureSha256 !== r.closureSha256 ||
      r.requestSha256 !== await jsonDigest(result.evidence) || body && r.requestSha256 !== await jsonDigest(body) ||
      r.contextSha256 !== result.evidence.contextSha256 || r.contextSha256 !== await jsonDigest(result.originalContext) ||
      r.targetOrderId !== result.evidence.targetOrderId || r.physicalCheckId !== result.evidence.physicalCheckId ||
      p.receipt.receiptId !== r.physicalCheckId || p.receipt.relayId !== r.relayId || p.receipt.warehouseId !== r.warehouseId ||
      p.receipt.taskId !== r.taskId || p.receipt.archiveSha256 !== r.archiveSha256 || p.receipt.closureSha256 !== r.closureSha256 ||
      p.receipt.contextSha256 !== r.contextSha256 || p.evidence.contextSha256 !== r.contextSha256 ||
      p.receipt.requestSha256 !== await jsonDigest(p.evidence) || r.contextSha256 !== await jsonDigest(p.originalContext))
    throw new Error('关联回执与原请求、原件、目标或实物依据不一致，请保留原键核对。');
  await verifyAssociationConflict({ conflict: result.conflictEvidence, decisionId: result.evidence.conflictDecisionId,
    contextSha256: r.contextSha256, owner });
}
