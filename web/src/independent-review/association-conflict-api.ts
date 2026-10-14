import { request } from '../api';
import { conflictResultSchema, verifyConflict } from './conflict-contracts';
import type { AssociationEvidence } from './association-contracts';
import type { PhysicalOwner } from './physical-contracts';

export async function checkAssociationConflict(owner: PhysicalOwner, body: AssociationEvidence): Promise<void> {
  if (!body.conflictDecisionId) return;
  const result = await request(`/original-relays/${owner.relayId}/conflict-decisions/${body.conflictDecisionId}?warehouseId=${owner.warehouseId}`, conflictResultSchema);
  await verifyConflict(result, owner);
  if (result.receipt.decisionId !== body.conflictDecisionId || result.evidence.contextSha256 !== body.contextSha256)
    throw new Error('冲突决定与本次完整关联依据不一致，请刷新核对。');
}
