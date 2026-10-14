import { z } from 'zod';
import { jsonDigest, physicalResultSchema, type PhysicalOwner } from './physical-contracts';
import { conflictResultSchema, verifyAssociationConflict } from './conflict-contracts';

const hash = z.string().regex(/^[0-9a-f]{64}$/);
export const candidateExclusionsSchema = z.array(z.object({ orderId: z.uuid(), reason: z.string().trim().min(1).max(500) }).strict()).max(2000)
  .refine(rows => new Set(rows.map(row => row.orderId)).size === rows.length);
export const associationMemberSchema = z.object({ relayId: z.uuid(), contextSha256: hash, physicalCheckId: z.uuid(),
  candidateExclusions: candidateExclusionsSchema, conflictDecisionId: z.uuid().optional() }).strict();
export const associationMemberResultSchema = z.object({ evidence: associationMemberSchema,
  originalContext: z.record(z.string(), z.unknown()), physicalEvidence: physicalResultSchema,
  conflictEvidence: conflictResultSchema.optional() }).strict();
export type AssociationMember = z.infer<typeof associationMemberSchema>;
export type AssociationMemberResult = z.infer<typeof associationMemberResultSchema>;
const originalSchema = z.object({ warehouseId: z.uuid(), relayId: z.uuid(), deviceId: z.uuid(), taskId: z.uuid(),
  archiveSha256: hash, closureSha256: hash });

function invalid(): Error { return new Error('关联成员与原请求、原件或实物依据不一致，请保留原键核对。'); }
async function verifyMember(member: AssociationMemberResult, owner: PhysicalOwner): Promise<void> {
  const e = member.evidence; const p = member.physicalEvidence; const r = p.receipt;
  const o = originalSchema.parse(member.originalContext.original);
  if (e.relayId !== o.relayId || o.warehouseId !== owner.warehouseId || r.warehouseId !== o.warehouseId ||
      r.relayId !== o.relayId || r.taskId !== o.taskId || r.archiveSha256 !== o.archiveSha256 || r.closureSha256 !== o.closureSha256 ||
      e.physicalCheckId !== r.receiptId || e.contextSha256 !== r.contextSha256 || e.contextSha256 !== p.evidence.contextSha256 ||
      e.contextSha256 !== await jsonDigest(member.originalContext) || e.contextSha256 !== await jsonDigest(p.originalContext) ||
      r.requestSha256 !== await jsonDigest(p.evidence)) throw invalid();
  await verifyAssociationConflict({ conflict: member.conflictEvidence, decisionId: e.conflictDecisionId,
    contextSha256: e.contextSha256, owner: { ...owner, relayId: e.relayId } });
}
export async function verifyAssociationMembers(input: { references?: AssociationMember[]; members?: AssociationMemberResult[];
  main: { relayId: string; deviceId: string; taskId: string }; owner: PhysicalOwner }): Promise<void> {
  const { references, members, main, owner } = input;
  if (Boolean(references) !== Boolean(members) || (references?.length ?? 0) !== (members?.length ?? 0)) throw invalid();
  const relays = new Set([main.relayId]); const tasks = new Set([`${main.deviceId}/${main.taskId}`]);
  for (const member of members ?? []) {
    const reference = references?.find(row => row.relayId === member.evidence.relayId);
    const o = originalSchema.parse(member.originalContext.original); const task = `${o.deviceId}/${o.taskId}`;
    if (!reference || relays.has(o.relayId) || tasks.has(task) || await jsonDigest(reference) !== await jsonDigest(member.evidence)) throw invalid();
    relays.add(o.relayId); tasks.add(task); await verifyMember(member, owner);
  }
  if (!relays.has(owner.relayId)) throw invalid();
}
