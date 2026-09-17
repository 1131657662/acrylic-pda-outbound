import { z } from 'zod';
import { dispositionSchema } from './disposition-contracts';

const decimal = z.string().regex(/^[1-9][0-9]*$/);
export const continuationApprovalSchema = z.object({ continuationId: z.string().uuid(), status: z.literal('APPROVED'),
  previousSubmissionId: z.string().uuid(), assignmentId: z.string().uuid(), assignmentEpoch: decimal,
  revision: z.number().int().positive(), workRound: decimal, nextOrderScanSeq: decimal, retainedManifestSha256: z.string().regex(/^[a-f0-9]{64}$/),
  recoveryPackageId: z.string().uuid(), issuedRecoveryEpoch: decimal, expiresAt: z.string(), mayResumeScanning: z.literal(false), stockReleased: z.literal(false), signedReceipt: z.string() });
export const continuationSchema = z.object({ continuationId: z.string().uuid(), changeRequestId: z.string().uuid(), type: z.literal('REOPEN_WORK'),
  orderId: z.string().uuid(), previousSubmissionId: z.string().uuid(), status: z.enum(['REQUESTED', 'APPROVED', 'ACTIVATED', 'COMPLETED']),
  workRound: decimal, revision: z.number().int().positive(), requestedDeviceId: z.string().uuid(), nextOrderScanSeq: decimal,
  retainedManifestSha256: z.string().regex(/^[a-f0-9]{64}$/), recoveryPackageId: z.string().uuid(), approval: continuationApprovalSchema.optional(),
  dispositions: z.array(dispositionSchema).default([]) });
export const continuationLabels = { REQUESTED: '已申请恢复，待主管核对', APPROVED: '主管已批准，待原 PDA 核验', ACTIVATED: '原 PDA 已核验恢复', COMPLETED: '本轮恢复装车已完成' };
