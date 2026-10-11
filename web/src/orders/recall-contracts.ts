import { z } from 'zod';
import { continuationSchema } from './continuation-contracts';
import { dispositionSchema } from './disposition-contracts';
import { faultWithdrawalSchema } from './fault-withdrawal-contracts';

const decimal = z.string().regex(/^(0|[1-9][0-9]*)$/);
export const recoverySchema = z.object({ recoveryPackageId: z.string().uuid(), payloadSha256: z.string().regex(/^[a-f0-9]{64}$/),
  actualCount: decimal, actualWeightGrams: decimal, captureHighWatermark: decimal, auditThroughSeq: decimal,
  issues: z.array(z.object({ code: z.string(), barcode: z.string() })) });
export const recallProofSchema = z.object({ changeRequestId: z.string().uuid(), orderId: z.string().uuid(), assignmentId: z.string().uuid(),
  recoveryPackageId: z.string().uuid(), payloadSha256: z.string(), status: z.enum(['STOPPED', 'CANCELLED']),
  issuedRecoveryEpoch: decimal, actualCount: decimal, actualWeightGrams: decimal, captureHighWatermark: decimal,
  auditThroughSeq: decimal, mayResumeScanning: z.literal(false), stockReleased: z.literal(false), confirmedAt: z.string(), signedReceipt: z.string() });
export const recallSchema = z.object({ changeRequestId: z.string().uuid(), orderId: z.string().uuid(), assignmentId: z.string().uuid(),
  revision: z.number().int(), serverRecoveryEpoch: decimal, rowVersion: decimal, status: z.enum(['REQUESTED', 'STOPPED', 'CANCELLED', 'WITHDRAWN']),
  deviceId: z.string().uuid(), continuation: continuationSchema.optional(),
  previousSubmission: z.object({ id: z.string().uuid(), status: z.string(), workRound: decimal }).optional(),
  reason: z.string(), recovery: recoverySchema.nullable(), stopProof: recallProofSchema.optional(), resolutionProof: z.union([recallProofSchema, faultWithdrawalSchema]).optional() });
export const recallResponseSchema = z.object({ request: recallSchema.nullable() });
export const recoveryDetailSchema = z.object({ recovery: recoverySchema, captureSummary: z.record(z.string(), decimal),
  dispositions: z.array(dispositionSchema).default([]),
  scans: z.array(z.object({ captureId: z.string().uuid(), baleId: z.string().uuid(), orderScanSeq: decimal, barcode: z.string(), deliveryCode: z.string(), weightGrams: decimal, expectedDispatchCycle: decimal, status: z.enum(['ACTIVE', 'VOID']) })) });
export type Recall = z.infer<typeof recallSchema>;
export const recallLabel = { REQUESTED: '等待原机确认停止', STOPPED: '原机已停止 · 占用保留', CANCELLED: '主管已核验零包作废', WITHDRAWN: '后台已撤回原故障领取' };
