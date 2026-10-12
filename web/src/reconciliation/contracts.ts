import { z } from 'zod';
const uuid = z.uuid(); const positive = z.string().regex(/^[1-9][0-9]*$/); const hash = z.string().regex(/^[a-f0-9]{64}$/);
export const kindSchema = z.enum(['RECONCILE_DISCOVER', 'RECONCILE_SUBMIT', 'RECONCILE_APPROVE',
  'RECONCILE_RETURN_DISCOVER', 'RECONCILE_RETURN_SUBMIT', 'RECONCILE_RETURN_APPROVE']);
export type ReconciliationKind = z.infer<typeof kindSchema>;
export const capabilities = z.object({ writeConfirmationAvailable: z.literal(false), releaseAllowed: z.literal(false),
  mayResumeScanning: z.literal(false), stockReleased: z.literal(false), localDeletionAllowed: z.literal(false) }).strict();
export const attachmentSchema = z.object({ attachmentId: uuid, evidenceSha256: hash, sizeBytes: positive }).strict();
export const evidenceSchema = z.object({ observedAt: z.iso.datetime({ offset: true }), place: z.string().min(1).max(200),
  observation: z.enum(['DISPATCHED_CONFIRMED', 'RETURNED_IN_WAREHOUSE']), reference: z.string().min(1).max(500), attachments: z.array(attachmentSchema).min(1).max(5) }).strict();
const commandBase = z.object({ commandVersion: z.literal(1), commandId: uuid,
  warehouseId: uuid, transportRecoveryEpoch: positive, reason: z.string().min(1).max(500), expected: z.record(z.string(), z.string()),
}).strict();
export const commandSchema = z.discriminatedUnion('commandType', [
  commandBase.extend({ commandType: z.literal('RECONCILE_DISCOVER'), dispatchId: uuid }),
  commandBase.extend({ commandType: z.literal('RECONCILE_SUBMIT'), caseId: uuid,
    evidence: evidenceSchema.extend({ observation: z.literal('DISPATCHED_CONFIRMED') }) }),
  commandBase.extend({ commandType: z.literal('RECONCILE_APPROVE'), caseId: uuid }),
  commandBase.extend({ commandType: z.literal('RECONCILE_RETURN_DISCOVER'), dispatchId: uuid }),
  commandBase.extend({ commandType: z.literal('RECONCILE_RETURN_SUBMIT'), caseId: uuid,
    evidence: evidenceSchema.extend({ observation: z.literal('RETURNED_IN_WAREHOUSE') }) }),
  commandBase.extend({ commandType: z.literal('RECONCILE_RETURN_APPROVE'), caseId: uuid }),
]);
export const receiptSchema = z.object({ commandId: uuid, commandType: kindSchema, commandSha256: hash, orderId: uuid,
  signedReceipt: z.string().min(30), actor: z.object({ principalId: uuid, warehouseId: uuid }).catchall(z.unknown()),
  expected: z.record(z.string(), z.string()), capabilities, businessDecision: z.object({ caseId: uuid, status: z.string(),
    availability: z.enum(['UNCHANGED', 'REEVALUATE']), factsSha256: hash, ledgerId: uuid.optional() }).catchall(z.unknown()) }).catchall(z.unknown());
const anyOriginal = z.object({ actor: z.object({ principalId: uuid }).catchall(z.unknown()), commandSha256: hash }).catchall(z.unknown());
const reopeningSchema = z.object({ documentId: uuid, lineId: uuid, cycleBefore: positive, cycleAfter: positive,
  stateVersion: positive, evidenceSha256: hash, type: z.enum(['PHYSICAL_RETURN', 'DISPATCH_CANCELLATION']),
  approvalReceipt: anyOriginal.extend({ receiptId: uuid, recordedAt: z.iso.datetime({ offset: true }) }) }).catchall(z.unknown());
const factsSchema = z.object({ result: z.object({ currentResultVersion: positive, orderStatus: z.string() }).catchall(z.unknown()),
  dispatch: z.object({ dispatchId: uuid, count: z.string(), weightG: z.string(), dispatchCycle: positive }).catchall(z.unknown()),
  stock: z.object({ barcode: z.string(), bookStatus: z.string(), stockVersion: positive, upstreamPresent: z.union([z.number(), z.boolean()]),
    blockedReason: z.string().nullable(), snapshotId: uuid, activeSnapshotId: uuid.nullable() }).catchall(z.unknown()),
  owners: z.array(z.record(z.string(), z.unknown())), ledger: z.array(z.record(z.string(), z.unknown())),
  reopening: reopeningSchema.optional() }).catchall(z.unknown());
export const preparationSchema = z.object({ context: z.object({ orderId: uuid, dispatchId: uuid, warehouseId: uuid, factsSha256: hash,
  facts: factsSchema, case: z.object({ caseId: uuid, status: z.string() }).catchall(z.unknown()).optional(),
  discoveredReceipt: anyOriginal.optional(), submittedReceipt: anyOriginal.optional(),
  submittedCommand: z.object({ evidence: evidenceSchema }).catchall(z.unknown()).optional() }), expected: z.record(z.string(), z.string()), capabilities });
export const listSchema = z.object({ candidates: z.array(z.object({ dispatchId: uuid, orderId: uuid, barcode: z.string() })),
  returnedCandidates: z.array(z.object({ dispatchId: uuid, orderId: uuid, barcode: z.string() })).default([]),
  cases: z.array(z.object({ caseId: uuid, dispatchId: uuid, status: z.string(), kind: z.enum(['MISSING_POSTING', 'RETURNED_STOCK']).default('MISSING_POSTING') })),
  nextCursor: uuid.nullable(), nextCaseCursor: uuid.nullable(), nextReturnedCursor: uuid.nullable(), capabilities });
export type ReconciliationPreparation = z.infer<typeof preparationSchema>;
export type ReconciliationCommand = z.infer<typeof commandSchema>;
export type ReconciliationReceipt = z.infer<typeof receiptSchema>;
export type ReconciliationList = z.infer<typeof listSchema>;
export type ReconciliationEvidence = z.infer<typeof evidenceSchema>;
export const labels: Record<ReconciliationKind, string> = { RECONCILE_DISCOVER: '登记缺账差异', RECONCILE_SUBMIT: '提交核验凭据', RECONCILE_APPROVE: '主管批准补扣',
  RECONCILE_RETURN_DISCOVER: '登记返库恢复', RECONCILE_RETURN_SUBMIT: '提交返库核验凭据', RECONCILE_RETURN_APPROVE: '主管批准返库恢复' };
