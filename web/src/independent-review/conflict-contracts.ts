import { z } from 'zod';
import { jsonDigest, physicalResultSchema, type PhysicalOwner, type PhysicalResult } from './physical-contracts';

const hash = z.string().regex(/^[0-9a-f]{64}$/);
const context = z.record(z.string(), z.unknown());
export const conflictEvidenceSchema = z.object({ contextSha256: hash, physicalCheckId: z.uuid(),
  predecessorDecisionId: z.uuid().optional(),
  peerChecks: z.array(z.object({ relayId: z.uuid(), physicalCheckId: z.uuid() }).strict()).min(1).max(199),
  reason: z.string().trim().min(1).max(500) }).strict()
  .refine(body => new Set(body.peerChecks.map(row => row.relayId)).size === body.peerChecks.length);
export const conflictResultSchema = z.object({
  receipt: z.object({ purpose: z.literal('INDEPENDENT_CONFLICT_EVIDENCE_V1'), decisionId: z.uuid(),
    warehouseId: z.uuid(), relayId: z.uuid(), deviceId: z.uuid(), taskId: z.uuid(), supervisorId: z.uuid(),
    decision: z.literal('ASSIGN_TO_SELECTED_TASK'), archiveSha256: hash, closureSha256: hash,
    requestSha256: hash, contextSha256: hash, recordedAt: z.iso.datetime(), signedProof: z.string().min(1),
    predecessorDecisionId: z.uuid().optional(), predecessorReceiptSha256: hash.optional(),
    signingKeyId: z.string().min(1), conflictDecisionRecorded: z.literal(true), pendingReconciliation: z.literal(true),
    associationAuthorized: z.literal(false), newCollectionAuthorized: z.literal(false), stockAvailable: z.literal(false),
    businessProcessed: z.literal(false), releaseAllowed: z.literal(false), localDeletionAllowed: z.literal(false) }).strict(),
  evidence: conflictEvidenceSchema,
  originalContext: z.object({ selected: z.object({ context, physical: physicalResultSchema }).strict(),
    predecessorReceipt: context.optional(),
    peers: z.array(z.object({ group: context, context, physical: physicalResultSchema }).strict()).min(1).max(199),
    claims: z.array(z.object({ barcode: z.string().min(1), baleId: z.uuid(), dispatchCycle: z.string().regex(/^[1-9][0-9]*$/) }).strict()).min(1) }).strict(),
}).strict();
export type ConflictEvidence = z.infer<typeof conflictEvidenceSchema>;
export type ConflictResult = z.infer<typeof conflictResultSchema>;
const original = z.object({ warehouseId: z.uuid(), relayId: z.uuid(), deviceId: z.uuid(), taskId: z.uuid(),
  archiveSha256: hash, closureSha256: hash });
const group = z.object({ deviceId: z.uuid(), taskId: z.uuid(), crossTaskConflict: z.boolean(), closureDivergence: z.boolean() });
const peers = z.array(context);

function invalid(): Error { return new Error('冲突决定与原请求、完整各方依据或实物引用不一致，请保留原键核对。'); }
async function verifyCheck(check: PhysicalResult, snapshot: Record<string, unknown>, owner: PhysicalOwner): Promise<void> {
  const o = original.parse(snapshot.original); const r = check.receipt; const digest = await jsonDigest(snapshot);
  if (o.warehouseId !== owner.warehouseId || r.warehouseId !== o.warehouseId || r.relayId !== o.relayId ||
      r.taskId !== o.taskId || r.archiveSha256 !== o.archiveSha256 || r.closureSha256 !== o.closureSha256 ||
      r.contextSha256 !== digest || check.evidence.contextSha256 !== digest || await jsonDigest(check.originalContext) !== digest ||
      r.requestSha256 !== await jsonDigest(check.evidence)) throw invalid();
}
async function verifyPeers(result: ConflictResult, owner: PhysicalOwner): Promise<void> {
  const rows = result.originalContext.peers; const references = result.evidence.peerChecks;
  const groups = peers.parse(result.originalContext.selected.context.peers).filter(row => group.parse(row).crossTaskConflict);
  const digests = await Promise.all(groups.map(jsonDigest));
  if (groups.length !== rows.length || references.length !== rows.length) throw invalid();
  const seen = new Set<string>();
  for (const row of rows) {
    const g = group.parse(row.group); const o = original.parse(row.context.original); const r = row.physical.receipt;
    const id = `${g.deviceId}/${g.taskId}`;
    if (seen.has(id) || g.closureDivergence || g.deviceId !== o.deviceId || g.taskId !== o.taskId ||
        !digests.includes(await jsonDigest(row.group)) ||
        !references.some(ref => ref.relayId === r.relayId && ref.physicalCheckId === r.receiptId)) throw invalid();
    seen.add(id); await verifyCheck(row.physical, row.context, owner);
  }
}
/** Display digest/reference checks; server verifies RSA and immutable database references. */
export async function verifyConflict(result: ConflictResult, owner: PhysicalOwner, body?: ConflictEvidence): Promise<void> {
  const r = result.receipt; const selected = result.originalContext.selected; const o = original.parse(selected.context.original);
  const previous = result.originalContext.predecessorReceipt; const predecessor = result.evidence.predecessorDecisionId;
  if (Boolean(predecessor) !== Boolean(previous) || Boolean(predecessor) !== Boolean(r.predecessorDecisionId) ||
      Boolean(predecessor) !== Boolean(r.predecessorReceiptSha256) || predecessor && (!previous ||
      predecessor !== r.predecessorDecisionId || previous.decisionId !== predecessor ||
      previous.warehouseId !== r.warehouseId || previous.relayId !== r.relayId || previous.taskId !== r.taskId ||
      previous.deviceId !== r.deviceId || r.predecessorReceiptSha256 !== await jsonDigest(previous))) throw invalid();
  if (r.warehouseId !== owner.warehouseId || r.relayId !== owner.relayId || r.relayId !== o.relayId ||
      r.warehouseId !== o.warehouseId || r.deviceId !== o.deviceId || r.taskId !== o.taskId ||
      r.archiveSha256 !== o.archiveSha256 || r.closureSha256 !== o.closureSha256 ||
      r.requestSha256 !== await jsonDigest(result.evidence) || body && r.requestSha256 !== await jsonDigest(body) ||
      r.contextSha256 !== await jsonDigest(result.originalContext) || result.evidence.contextSha256 !== await jsonDigest(selected.context) ||
      result.evidence.physicalCheckId !== selected.physical.receipt.receiptId) throw invalid();
  await verifyCheck(selected.physical, selected.context, owner); await verifyPeers(result, owner);
}
export async function verifyAssociationConflict(input: {
  conflict?: ConflictResult; decisionId?: string; contextSha256: string; owner: PhysicalOwner;
}): Promise<void> {
  const { conflict, decisionId, contextSha256, owner } = input;
  if (Boolean(decisionId) !== Boolean(conflict) || conflict && (conflict.receipt.decisionId !== decisionId ||
      await jsonDigest(conflict.originalContext.selected.context) !== contextSha256))
    throw new Error('关联回执缺少对应原冲突决定或完整依据不一致。');
  if (conflict) await verifyConflict(conflict, owner);
}
