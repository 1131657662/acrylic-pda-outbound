import { z } from 'zod';
import { capabilities, evidenceSchema } from './contracts';
const uuid = z.uuid(); const positive = z.string().regex(/^[1-9][0-9]*$/); const hash = z.string().regex(/^[a-f0-9]{64}$/);
export const quarantineKind = z.enum(['RECONCILE_QUARANTINE_DISCOVER', 'RECONCILE_QUARANTINE_SUBMIT', 'RECONCILE_QUARANTINE_APPROVE']);
export type QuarantineKind = z.infer<typeof quarantineKind>;
const original = z.object({ receiptId: uuid, signedReceipt: z.string().min(30), actor: z.object({ principalId: uuid }).catchall(z.unknown()),
  recordedAt: z.iso.datetime({ offset: true }) }).catchall(z.unknown());
const tempOrigin = z.object({ sourceKind: z.literal('TEMP').default('TEMP'), dispositionId: uuid, scopeId: uuid, submissionId: uuid,
  evidenceSetId: uuid, verificationId: uuid, manifestSha256: hash, cycle: positive, closeReason: z.string(),
  originalReceipt: original, originalReceiptSha256: hash }).catchall(z.unknown());
const pcProof = z.object({ proofType: z.literal('PC_DATABASE_ORIGINALS'), receiptId: uuid,
  actor: z.object({ principalId: uuid }), recordedAt: z.iso.datetime({ offset: true }),
  snapshot: z.record(z.string(), z.unknown()), plan: z.record(z.string(), z.unknown()),
  review: z.object({ principalId: uuid, decision: z.literal('APPROVE'), reason: z.string() }).catchall(z.unknown()),
  commands: z.object({ stop: z.record(z.string(), z.unknown()), plan: z.record(z.string(), z.unknown()),
    review: z.record(z.string(), z.unknown()), apply: z.record(z.string(), z.unknown()) }), handoverEvidence: z.string() });
const pcOrigin = z.object({ sourceKind: z.literal('PC'), dispositionId: uuid, workId: uuid, planId: uuid, caseId: uuid,
  entryId: uuid, cycle: positive, closeReason: z.string(), originalReceipt: pcProof, originalReceiptSha256: hash }).catchall(z.unknown());
export const preparationSchema = z.object({ context: z.object({ orderId: uuid.nullable(), baleId: uuid, dispositionId: uuid,
  warehouseId: uuid, factsSha256: hash, facts: z.object({
    origin: z.union([tempOrigin, pcOrigin]),
    stock: z.object({ barcode: z.string(), dispatchCycle: positive, bookStatus: z.string(), stockVersion: positive,
      snapshotId: uuid, activeSnapshotId: uuid.nullable(), upstreamPresent: z.union([z.number(), z.boolean()]),
      blockedReason: z.string().nullable() }).catchall(z.unknown()),
    owners: z.array(z.record(z.string(), z.unknown())), dispatches: z.array(z.record(z.string(), z.unknown())),
    ledger: z.array(z.record(z.string(), z.unknown())) }),
  case: z.object({ caseId: uuid, status: z.string() }).catchall(z.unknown()).optional(),
  discoveredReceipt: original.optional(), submittedReceipt: original.optional(),
  submittedCommand: z.object({ evidence: evidenceSchema }).catchall(z.unknown()).optional() }),
  expected: z.record(z.string(), z.string()), capabilities });
export const listSchema = z.object({ candidates: z.array(z.object({ baleId: uuid, dispositionId: uuid, barcode: z.string() })).max(50),
  cases: z.array(z.object({ caseId: uuid, baleId: uuid, kind: z.literal('QUARANTINE_RELEASE'), status: z.string() })).max(50),
  nextCursor: uuid.nullable(), nextCaseCursor: uuid.nullable(), capabilities });
export type QuarantinePreparation = z.infer<typeof preparationSchema>;
export type QuarantineList = z.infer<typeof listSchema>;
