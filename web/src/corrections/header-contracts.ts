import { z } from 'zod';

const positive = z.string().regex(/^[1-9][0-9]*$/);
const hash = z.string().regex(/^[a-f0-9]{64}$/);
export const kindSchema = z.enum(['CREATE', 'APPROVE', 'WITHDRAW', 'WITHDRAW_CONFIRM']);
export type HeaderKind = z.infer<typeof kindSchema>;
export const capabilitySchema = z.object({ writeConfirmationAvailable: z.literal(false), releaseAllowed: z.literal(false),
  mayResumeScanning: z.literal(false), stockReleased: z.literal(false), localDeletionAllowed: z.literal(false) }).strict();
export const patchSchema = z.object({ orderNo: z.string().max(100).optional(), vehicle: z.string().max(200).optional(),
  container: z.string().max(200).optional(), remark: z.string().max(1000).optional() }).strict();
export const headerSchema = patchSchema.required().catchall(z.unknown());
const orderFacts = z.object({ orderId: z.uuid(), warehouseId: z.uuid(), currentResultVersion: positive,
  currentResultEffectiveCount: z.string(), currentResultEffectiveWeightG: z.string() }).catchall(z.unknown());
export const documentSchema = z.object({ documentId: z.uuid(), orderId: z.uuid(), warehouseId: z.uuid(),
  type: z.literal('HEADER_CORRECTION'), status: z.enum(['SUBMITTED', 'EFFECTIVE', 'WITHDRAW_PENDING', 'WITHDRAWN']),
  documentSha256: hash, expectedResultVersion: positive, requestedBy: z.uuid(), approvedBy: z.string(),
}).catchall(z.unknown());
export const headersSchema = z.object({ context: orderFacts, header: headerSchema,
  items: z.array(z.object({ context: documentSchema, headerPatch: patchSchema, reason: z.string() })),
  nextCursor: z.uuid().nullable(), capabilities: capabilitySchema });
export const preparationSchema = z.object({ expected: z.record(z.string(), z.string()),
  context: z.object({ orderId: z.uuid(), warehouseId: z.uuid(), documentId: z.uuid().optional() }).catchall(z.unknown()),
  originalCommand: z.unknown().optional(), capabilities: capabilitySchema });
export const commandSchema = z.object({ commandVersion: z.literal(1), commandId: z.uuid(), commandType: kindSchema,
  warehouseId: z.uuid(), transportRecoveryEpoch: positive, reason: z.string().min(1).max(500),
  expected: z.record(z.string(), z.string()), orderId: z.uuid().optional(), documentId: z.uuid().optional(),
  type: z.literal('HEADER_CORRECTION').optional(), headerPatch: patchSchema.optional() }).strict();
export const receiptSchema = z.object({ commandId: z.uuid(), commandType: kindSchema, commandSha256: hash,
  orderId: z.uuid(), documentId: z.uuid(), status: z.literal('RECORDED'), signedReceipt: z.string().min(30),
  actor: z.object({ principalId: z.uuid(), warehouseId: z.uuid() }).catchall(z.unknown()),
  expected: z.record(z.string(), z.string()), capabilities: capabilitySchema,
  businessDecision: z.object({ status: z.string(), resultVersion: positive.optional(),
    headerRestoration: z.object({ restoredFields: z.array(z.string()), previousResultVersion: positive }).catchall(z.unknown()).optional(),
  }).catchall(z.unknown()),
}).catchall(z.unknown());
export type HeaderData = z.infer<typeof headersSchema>;
export type HeaderCommand = z.infer<typeof commandSchema>;
export type HeaderReceipt = z.infer<typeof receiptSchema>;
export const kindLabels: Record<HeaderKind, string> = { CREATE: '提交抬头更正', APPROVE: '批准抬头更正',
  WITHDRAW: '发起撤销补偿', WITHDRAW_CONFIRM: '确认撤销补偿' };
export const statusLabels: Record<string, string> = { SUBMITTED: '待主管审批', EFFECTIVE: '已生效',
  WITHDRAW_PENDING: '待原申请人确认撤销', WITHDRAWN: '已补偿撤销' };
