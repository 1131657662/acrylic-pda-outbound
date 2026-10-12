import { z } from 'zod';

const uuid = z.string().uuid();
const version = z.string().regex(/^[1-9][0-9]{0,17}$/);
const evidence = z.string().trim().min(1).max(1000).refine(value => !/[\u0000-\u001f\u007f]/.test(value));
const base = z.object({ orderId: uuid, revision: version, rowVersion: version, evidence });

export const exclusionInputSchema = z.discriminatedUnion('basis', [
  base.extend({ basis: z.literal('CANCELLED'), withdrawalRequestId: z.null() }).strict(),
  base.extend({ basis: z.literal('WITHDRAWN'), withdrawalRequestId: uuid }).strict(),
  base.extend({ basis: z.literal('UNRELATED'), withdrawalRequestId: z.null() }).strict(),
]);
export type ExclusionInput = z.infer<typeof exclusionInputSchema>;

export function exclusionBasisLabel(basis: ExclusionInput['basis']): string {
  return { CANCELLED: '已作废', WITHDRAWN: '已故障撤回、尚未重发', UNRELATED: '不是同一业务' }[basis];
}
