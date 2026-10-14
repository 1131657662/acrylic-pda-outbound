import type { IndependentReview } from './contracts';
import type { Candidate } from './association-api';
import type { AssociationEvidence } from './association-contracts';

export interface AssociationShortfall { original: string; target: string; needed: boolean }
function count(value: string): number {
  if (!/^(0|[1-9][0-9]{0,5})$/.test(value) || Number(value) > 100000) throw new Error('计划包数或短装差额无效，请重新核对。');
  return Number(value);
}
function productKey(row: { deliveryCode: string; model: string; name: string }): string {
  return JSON.stringify([row.deliveryCode, row.model, row.name]);
}
export function associationShortfall(data: IndependentReview, target: Candidate, others: IndependentReview[] = []): AssociationShortfall {
  let active = 0; let original = 0; const planned = count(target.plannedTotal);
  for (const row of [data, ...others]) {
    const closure = row.original.closure;
    if (count(row.summary.activeTotal) !== count(closure.active) || count(closure.shortfall) > 0 && !closure.shortfallReason.trim())
      throw new Error('原结束有效包或短装原因不自洽，请重新核对。');
    active += count(closure.active); original += count(closure.shortfall);
  }
  if (active > 100000 || original > 100000) throw new Error('全组数量超过资源上限。');
  if (target.products.reduce((sum, row) => sum + count(row.plannedCount), 0) !== planned || planned < active)
    throw new Error('目标计划不自洽或少于有效包，不能以短装批准放行。');
  const products = new Set(target.products.map(productKey));
  if (products.size !== target.products.length) throw new Error('目标计划包含重复产品，请重新核对。');
  return { original: String(original), target: String(planned - active), needed: original > 0 || planned > active };
}
export function approveAssociationShortfall(input: { facts: AssociationShortfall; confirmed: boolean; reason: string }): Pick<AssociationEvidence, 'shortfallApproval'> {
  if (!input.facts.needed) return {};
  const reason = input.reason.trim();
  if (!input.confirmed || !reason || reason.length > 500) throw new Error('须显式确认原任务与目标两种短装差额，并填写主管批准说明。');
  return { shortfallApproval: { originalShortfall: input.facts.original, targetShortfall: input.facts.target, reason } };
}
import { z } from 'zod';
const targetFacts = z.object({ original: z.object({ closure: z.object({ plannedTotal: z.string(), active: z.string(), shortfall: z.string() }) }),
  inventory: z.array(z.object({ original: z.object({ deliveryCode: z.string(), model: z.string(), name: z.string() }) })) });
export function checkAssociationTargetProducts(context: unknown, target: Candidate): void {
  checkAssociationGroupProducts([context], target);
}
export function checkAssociationGroupProducts(contexts: unknown[], target: Candidate): void {
  const actual = new Map<string, number>();
  for (const context of contexts) {
    const facts = targetFacts.parse(context); const closure = facts.original.closure;
    if (count(closure.active) !== facts.inventory.length || count(closure.plannedTotal) - count(closure.active) !== count(closure.shortfall))
      throw new Error('原结束计划、有效包或短装差额不自洽，请重新核对。');
    for (const row of facts.inventory) actual.set(productKey(row.original), (actual.get(productKey(row.original)) ?? 0) + 1);
  }
  const planned = new Map(target.products.map(row => [productKey(row), count(row.plannedCount)]));
  for (const [key, quantity] of actual) if (quantity > (planned.get(key) ?? 0))
    throw new Error('原有效包包含异产品或超过目标产品计划，不能以短装批准放行。');
}
