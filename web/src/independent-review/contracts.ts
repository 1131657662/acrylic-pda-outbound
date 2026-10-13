import { z } from 'zod';

const count = z.string().regex(/^\d+$/); const hash = z.string().regex(/^[0-9a-f]{64}$/);
export const candidateSchema = z.object({
  id: z.uuid(), orderNo: z.string(), source: z.string(), status: z.string(), revision: count, rowVersion: count,
  revisionId: z.uuid(), vehicleNo: z.string(), containerNo: z.string(), plannedTotal: count, createdAt: z.string(),
  hasAssignment: z.boolean(), hasPcWork: z.boolean(), hasSubmission: z.boolean(), hasResult: z.boolean(),
  products: z.array(z.object({ deliveryCode: z.string(), productId: z.uuid(), model: z.string(), name: z.string(), plannedCount: count })),
  workFacts: z.array(z.object({ kind: z.enum(['ASSIGNMENT', 'PC', 'SUBMISSION', 'RESULT']), id: z.string().optional(),
    status: z.string().optional(), version: count.optional(), rowVersion: count.optional(), workRound: count.optional(),
    deviceId: z.string().optional(), operatorId: z.string().optional(), epoch: count.optional(), revision: count.optional(),
    issuedAt: z.string().nullable().optional(), expiresAt: z.string().nullable().optional(), downloadAckAt: z.string().nullable().optional(),
    nextSequence: count.optional(), submissionId: z.uuid().optional(), actualCount: count.optional(), weightGrams: count.optional(),
    inventoryDeducted: count.optional(), receiptSha256: hash.nullable().optional(), issuesSha256: hash.nullable().optional(),
    updatedAt: z.string().optional(), headerSha256: hash.optional() })),
  blockers: z.array(z.string()), associationAuthorized: z.literal(false),
});
const originalScan = z.object({ barcode: z.string(), deliveryCode: z.string(), model: z.string(), name: z.string(), weightGrams: count });
const inventorySchema = z.object({
  captureId: z.uuid(), sequence: count, barcode: z.string(), original: originalScan, blockers: z.array(z.string()),
  server: z.object({ baleId: z.uuid(), dispatchCycle: count, warehouseId: z.uuid(), snapshotId: z.string().nullable(),
    bookStatus: z.string().nullable(), blockedReason: z.string().nullable(), commitmentState: z.string().nullable(),
    commitmentOrderId: z.string().nullable(), commitmentCycle: count.nullable(), positionSnapshotId: z.string().nullable(),
    positionVersion: count.nullable(), upstreamPresent: z.boolean(), deliveryCode: z.string().nullable(),
    model: z.string().nullable(), name: z.string().nullable(), weightGrams: count.nullable() }).nullable(),
});
const peerSchema = z.object({
  deviceId: z.uuid(), taskId: z.uuid(), selectedTask: z.boolean(), closureDivergence: z.boolean(),
  crossTaskConflict: z.boolean(), crossDeviceConflict: z.boolean(), overlappingBarcodes: z.array(z.string()), closureDigests: z.array(hash),
  originals: z.array(z.object({ relayId: z.uuid(), exportId: z.uuid(), archiveSha256: hash, sourceTrust: z.string(),
    completeBoundaryVerified: z.boolean(), closureSha256: hash.nullable() })),
});
const base = z.object({
  contextSha256: hash, total: count, hasMore: z.boolean(), nextCursor: z.string().nullable(), checkedAt: z.string(),
  original: z.object({ warehouseId: z.uuid(), relayId: z.uuid(), taskId: z.uuid(), deviceId: z.uuid(), operatorId: z.uuid(),
    archiveSha256: hash, closureSha256: hash, task: z.object({ vehicleNo: z.string() }),
    closure: z.object({ active: count, void: count, shortfall: count, shortfallReason: z.string(), closedAt: z.string() }) }),
  review: z.object({ sourceTrust: z.literal('TRUSTED'), authorizationState: z.literal('VERIFIED'), completeBoundaryVerified: z.literal(true),
    currentSubjectState: z.string() }),
  summary: z.object({ candidateTotal: count, activeTotal: count, inventoryBlocked: count, peerTaskTotal: count,
    crossTaskConflicts: count, closureDivergences: count, fromDate: z.string(), throughDate: z.string(),
    onlyReceivedOriginalsObserved: z.literal(true), physicalVerified: z.literal(false), manuallyAssociated: z.literal(false) }),
  pendingReconciliation: z.literal(true), associationAuthorized: z.literal(false), stockAvailable: z.literal(false),
  businessProcessed: z.literal(false), releaseAllowed: z.literal(false), localDeletionAllowed: z.literal(false),
});
export const reviewSchema = z.discriminatedUnion('section', [
  base.extend({ section: z.literal('candidates'), items: z.array(candidateSchema) }),
  base.extend({ section: z.literal('inventory'), items: z.array(inventorySchema) }),
  base.extend({ section: z.literal('peers'), items: z.array(peerSchema) }),
]);
export type IndependentReview = z.infer<typeof reviewSchema>;
export type ReviewSection = IndependentReview['section'];
export function matchesOriginal(data: IndependentReview, expected: { relayId: string; warehouseId: string; section: ReviewSection }): boolean {
  return data.original.relayId === expected.relayId && data.original.warehouseId === expected.warehouseId && data.section === expected.section;
}
