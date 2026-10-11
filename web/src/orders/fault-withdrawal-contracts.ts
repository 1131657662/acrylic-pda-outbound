import { z } from 'zod';

export const faultWithdrawalSchema = z.object({
  changeRequestId: z.uuid(), orderId: z.uuid(), assignmentId: z.uuid(), revision: z.number().int().positive(),
  restartRevision: z.number().int().positive(), issuedRecoveryEpoch: z.string().regex(/^[1-9][0-9]*$/),
  status: z.literal('WITHDRAWN'), orderStatus: z.literal('DRAFT'), reason: z.string(), resolvedBy: z.uuid(),
  originalDeviceStopReceived: z.literal(false), mayResumeScanning: z.literal(false), stockReleased: z.boolean(),
  physicalEvidence: z.string(), releasedBales: z.array(z.object({ baleId: z.uuid(), submissionId: z.uuid(), dispatchCycle: z.string().regex(/^[1-9][0-9]*$/) })),
  confirmedAt: z.string(), signedReceipt: z.string().min(1),
}).refine(value => value.restartRevision === value.revision + 1 && value.stockReleased === (value.releasedBales.length > 0),
  '撤回凭据与重发修订或释放明细不一致');
export const faultWithdrawalCommandSchema = z.object({ type: z.literal('FAULT_WITHDRAWAL'), expectedVersion: z.string().regex(/^[1-9][0-9]*$/),
  reason: z.string().trim().min(1).max(500), physicalChecked: z.boolean().optional(), physicalEvidence: z.string().max(1000).optional() }).strict();
export type FaultWithdrawal = z.infer<typeof faultWithdrawalSchema>;
