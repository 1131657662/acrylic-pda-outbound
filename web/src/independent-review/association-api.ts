import { ApiError, request } from '../api';
import { reviewSchema, type IndependentReview } from './contracts';
import { associationResultSchema, verifyAssociation, type AssociationResult } from './association-contracts';
import { loadAssociation, saveAssociation } from './association-storage';
import type { PhysicalOwner } from './physical-contracts';
import { jsonDigest, physicalResultSchema } from './physical-contracts';
import type { AssociationEvidence } from './association-contracts';
import { checkAssociationTargetProducts } from './association-shortfall';
export type Candidate = Extract<IndependentReview, { section: 'candidates' }>['items'][number];
export async function checkAssociationPhysical(owner: PhysicalOwner, body: AssociationEvidence, target?: Candidate): Promise<void> {
  const result = await request(`/original-relays/${owner.relayId}/physical/checks/${body.physicalCheckId}?warehouseId=${owner.warehouseId}`, physicalResultSchema);
  if (result.receipt.relayId !== owner.relayId || result.receipt.warehouseId !== owner.warehouseId ||
      result.receipt.receiptId !== body.physicalCheckId || result.receipt.contextSha256 !== body.contextSha256 ||
      result.evidence.contextSha256 !== body.contextSha256 || await jsonDigest(result.originalContext) !== body.contextSha256 ||
      await jsonDigest(result.evidence) !== result.receipt.requestSha256 || result.evidence.packages.some(row => row.status !== 'LOADED'))
    throw new Error('实物检查与完整关联依据不一致，须重新核对实物回执。');
  if (target) checkAssociationTargetProducts(result.originalContext, target);
}
function path(owner: PhysicalOwner, suffix = ''): string {
  return `/original-relays/${owner.relayId}/associations${suffix}?warehouseId=${owner.warehouseId}`;
}
export async function readAssociation(owner: PhysicalOwner): Promise<AssociationResult | null> {
  const command = loadAssociation(owner).command;
  if (command && !command.receipt) return null;
  try {
    const result = await request(path(owner, command?.receipt ? `/${command.receipt.associationId}` : ''), associationResultSchema);
    await verifyAssociation(result, owner, command?.body);
    if (command?.receipt && JSON.stringify(result.receipt) !== JSON.stringify(command.receipt)) throw new Error('服务器与本地原关联回执不一致。');
    return result;
  } catch (cause) {
    if (!command && cause instanceof ApiError && cause.status === 404 && cause.code === 'INDEPENDENT_ASSOCIATION_NOT_FOUND') return null;
    throw cause;
  }
}
export async function sendAssociation(owner: PhysicalOwner, assertCurrent: () => void): Promise<void> {
  const state = loadAssociation(owner); const command = state.command;
  if (!command) throw new Error('尚无已保存的原关联请求。');
  const result = await request(path(owner), associationResultSchema, { method: 'POST', body: JSON.stringify(command.body),
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key, 'X-Server-Recovery-Epoch': command.epoch } }, assertCurrent);
  await verifyAssociation(result, owner, command.body); assertCurrent();
  if (result.receipt.supervisorId !== owner.userId) throw new Error('原关联主管与发送身份不一致。');
  command.receipt = result.receipt; saveAssociation(state);
}
export async function allCandidates(data: IndependentReview, assertCurrent: () => void): Promise<Candidate[]> {
  const rows: Candidate[] = []; const seen = new Set<string>(); let cursor = '';
  do {
    const query = new URLSearchParams({ warehouseId: data.original.warehouseId, section: 'candidates', limit: '200', cursor });
    const page = await request(`/original-relays/${data.original.relayId}/independent-reconciliation?${query}`, reviewSchema); assertCurrent();
    if (page.section !== 'candidates' || page.contextSha256 !== data.contextSha256 || page.original.relayId !== data.original.relayId ||
        page.original.warehouseId !== data.original.warehouseId || page.total !== data.summary.candidateTotal)
      throw new Error('完整候选依据已变化，请刷新原件核对。');
    rows.push(...page.items); cursor = page.nextCursor ?? '';
    if (rows.length > 2000 || page.hasMore !== Boolean(cursor) || cursor && seen.has(cursor)) throw new Error('候选分页不完整或超过上限。');
    seen.add(cursor);
  } while (cursor);
  if (rows.length !== Number(data.summary.candidateTotal) || new Set(rows.map(row => row.id)).size !== rows.length)
    throw new Error('完整候选集合不一致。');
  return rows;
}
