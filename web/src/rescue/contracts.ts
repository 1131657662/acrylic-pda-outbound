import { z } from 'zod';

const targetSchema = z.object({ source: z.enum(['FORMAL', 'TEMP']), localOrderId: z.uuid() }).strict();
export const requestReceiptSchema = z.object({ requestId: z.uuid(), requestSha256: z.string().regex(/^[0-9a-f]{64}$/) }).strict();
export const grantReceiptSchema = z.object({ grantId: z.uuid(), signedAuthorization: z.string().min(1).max(32768) }).strict();
export const revocationReceiptSchema = z.object({ grantId: z.uuid(), signedRevocation: z.string().min(1).max(32768) }).strict();
export const documentSchema = z.object({ purpose: z.literal('PDA_RESCUE_REQUEST_V1'), requestKey: z.uuid(),
  deviceId: z.uuid(), operatorId: z.uuid(), warehouseId: z.uuid(), rescuerId: z.uuid(),
  orderScope: z.array(targetSchema).min(1).max(100), reason: z.string().min(1).max(1000) }).strict();
export const materialSchema = z.object({ request: requestReceiptSchema, originalText: z.string().min(1).max(32768),
  deviceSignature: z.string().min(1).max(256), orderScope: z.array(targetSchema).min(1).max(100),
  status: z.enum(['REQUESTED', 'AUTHORIZED', 'EXPIRED', 'REVOKED']), authorization: grantReceiptSchema.nullable(),
  revocation: revocationReceiptSchema.nullable(), localDeletionAllowed: z.literal(false) }).strict().superRefine((value, context) => {
  let original: z.infer<typeof documentSchema>;
  try { original = documentSchema.parse(JSON.parse(value.originalText)); }
  catch { context.addIssue({ code: 'custom', message: '原设备申请正文无效' }); return; }
  if (original.operatorId === original.rescuerId || JSON.stringify(original.orderScope) !== JSON.stringify(value.orderScope))
    context.addIssue({ code: 'custom', message: '原人/范围与申请材料不符' });
  const keys = value.orderScope.map(row => `${row.source}:${row.localOrderId}`);
  if (new Set(keys).size !== keys.length || (value.status === 'REQUESTED') !== (value.authorization === null)
    || (value.status === 'REVOKED') !== (value.revocation !== null)
    || value.revocation && value.revocation.grantId !== value.authorization?.grantId)
    context.addIssue({ code: 'custom', message: '原授权/撤销状态与材料不符' });
});
export const approvalSchema = z.object({ expiresAt: z.iso.datetime({ offset: true }).nullable(), reason: z.string().trim().min(1).max(1000) }).strict();
export function newApproval(value: unknown): z.infer<typeof approvalSchema> {
  const body = approvalSchema.parse(value);
  if (body.expiresAt !== null && Date.parse(body.expiresAt) <= Date.now()) throw new Error('到期时间须晚于当前时间，服务端仍会核对签发时间。');
  return body;
}
export const revokeSchema = z.object({ reason: z.string().trim().min(1).max(1000) }).strict();
export type RescueMaterial = z.infer<typeof materialSchema>;
export type RescueDocument = z.infer<typeof documentSchema>;
export const statusLabels: Record<RescueMaterial['status'], string> = {
  REQUESTED: '待主管核准', AUTHORIZED: '已签发只读授权', EXPIRED: '只读授权已到期', REVOKED: '只读授权已撤销',
};
