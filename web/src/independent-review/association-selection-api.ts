import { z } from 'zod';
import { candidateSchema } from './contracts';
import type { AssociationEvidence } from './association-contracts';
import { checkAssociationPhysical, type Candidate } from './association-api';
import { checkAssociationConflict } from './association-conflict-api';
import { checkAssociationGroupProducts } from './association-shortfall';
import { checkSelectedPackageUniqueness } from './association-selection';
import { jsonDigest, type PhysicalOwner } from './physical-contracts';

export async function checkAssociationSelection(input: { owner: PhysicalOwner; body: AssociationEvidence; target: Candidate; assertCurrent: () => void }): Promise<void> {
  const { owner, body, target, assertCurrent } = input; const contexts: unknown[] = [];
  const references = [{ relayId: owner.relayId, ...body }, ...(body.additionalTasks ?? [])];
  for (const reference of references) {
    const memberOwner = { ...owner, relayId: reference.relayId };
    const memberBody = { ...reference, targetOrderId: body.targetOrderId, reason: body.reason };
    const physical = await checkAssociationPhysical(memberOwner, memberBody); assertCurrent();
    const selected = z.object({ candidates: z.object({ items: z.array(z.record(z.string(), z.unknown())) }) }).parse(physical.originalContext)
      .candidates.items.find(row => row.id === target.id);
    if (!selected || await jsonDigest(candidateSchema.parse(selected)) !== await jsonDigest(target)) throw new Error('各任务的目标完整依据不一致，请刷新核对。');
    await checkAssociationConflict(memberOwner, memberBody); assertCurrent(); contexts.push(physical.originalContext);
  }
  checkAssociationGroupProducts(contexts, target);
  if (body.additionalTasks) checkSelectedPackageUniqueness(contexts);
}
