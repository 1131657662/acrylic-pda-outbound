import { z } from 'zod';

const id = z.uuid();
const decimal = z.string().regex(/^(0|[1-9]\d*)$/);
const positive = z.string().regex(/^[1-9]\d*$/);
const hash = z.string().regex(/^[a-f0-9]{64}$/);
const location = z.string().trim().min(1).max(200).refine(value => !/[\u0000-\u001f\u007f]/.test(value));
export const faultExpectedSchema = z.object({ caseId: id, caseVersion: positive,
  status: z.string(), userAuthVersion: positive }).strict();
export const faultArchiveSchema = z.object({ sourceEvidenceSetId: id, verificationId: id, setSha256: hash,
  captureHighWatermark: decimal, auditThroughSeq: positive, orderScanHighWatermark: decimal }).strict();
export const faultControlSchema = z.object({ deviceState: z.literal('POWERED_OFF_IN_CUSTODY'),
  controlledAt: z.iso.datetime({ offset: true }), deviceLocation: location, goodsLocation: location,
  unresolvedInputCount: z.literal('0') }).strict();
export const faultCommandSchema = z.object({ commandVersion: z.literal(1), commandType: z.literal('FAULT_FENCE'),
  commandId: id, warehouseId: id, caseId: id, transportRecoveryEpoch: positive, expected: faultExpectedSchema,
  sourceDeviceId: id, physicalControl: faultControlSchema, archivedEvidence: faultArchiveSchema }).strict();
export const faultReceiptSchema = z.object({ receiptVersion: z.literal(1), commandType: z.literal('FAULT_FENCE'),
  receiptId: id, eventId: id, routeScope: z.literal('r07.handover.v1'),
  status: z.literal('RECORDED'), commandId: id, commandSha256: hash, recordedAt: z.iso.datetime({ offset: true }),
  signedReceipt: z.string().min(1), expected: faultExpectedSchema,
  actor: z.object({ principalId: id, deviceId: z.null(), sessionId: z.null(), warehouseId: id,
    permissionSnapshot: z.object({ effectivePermissions: z.array(z.string()).refine(p => p.includes('order:handover')) }) }),
  caseContext: z.object({ caseId: id, warehouseId: id, scopeId: id, submissionId: id, canonicalOrderId: id,
    requestedBy: id, fromWorkRound: positive, toWorkRound: positive, sourceKind: z.literal('TEMP'), stopState: z.literal('REQUESTED') }),
  businessDecision: z.object({ caseId: id, status: z.literal('FAULT_FENCED'), stopKind: z.literal('FAULT_FENCE'),
    sourceDeviceId: id, disabledDeviceVersion: positive, oldDeviceAcknowledged: z.literal(false), payloadSha256: hash,
    physicalControl: faultControlSchema, source: z.object({ operatorId: id, deviceId: id }),
    archivedEvidence: faultArchiveSchema.extend({ sourceKind: z.literal('TEMP'), status: z.literal('VERIFIED_COMPLETE_AT_STOP'),
      submissionId: id, authorizationId: id, workRound: positive, mayResumeScanning: z.literal(false), stockReleased: z.literal(false) }) }),
  capabilities: z.object({ writeConfirmationAvailable: z.literal(false), releaseAllowed: z.literal(false),
    mayResumeScanning: z.literal(false), stockReleased: z.literal(false), localDeletionAllowed: z.literal(false) }).strict(),
}).refine(value => value.actor.principalId !== value.caseContext.requestedBy
  && value.actor.principalId !== value.businessDecision.source.operatorId
  && value.businessDecision.sourceDeviceId === value.businessDecision.source.deviceId
  && Date.parse(value.businessDecision.physicalControl.controlledAt) <= Date.parse(value.recordedAt));
export type FaultCommand = z.infer<typeof faultCommandSchema>;
export type FaultReceipt = z.infer<typeof faultReceiptSchema>;
export type FaultArchive = z.infer<typeof faultArchiveSchema>;
export type FaultControl = z.infer<typeof faultControlSchema>;
