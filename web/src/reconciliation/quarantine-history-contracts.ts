import { z } from 'zod';
const uuid = z.uuid(); const positive = z.string().regex(/^[1-9][0-9]*$/); const hash = z.string().regex(/^[a-f0-9]{64}$/);
const approval = z.object({ receiptId: uuid, commandId: uuid, orderId: uuid,
  actor: z.object({ principalId: uuid, warehouseId: uuid }).catchall(z.unknown()),
  recordedAt: z.iso.datetime({ offset: true }) }).catchall(z.unknown());
export const quarantineHistorySchema = z.array(z.object({
  result: z.object({ orderId: uuid, warehouseId: uuid, currentResultVersion: positive, orderStatus: z.literal('COMPLETED') }).catchall(z.unknown()),
  dispatch: z.object({ dispatchId: uuid, baleId: uuid, dispatchCycle: positive, count: z.literal('0'), weightG: z.literal('0') }).catchall(z.unknown()),
  ledger: z.array(z.object({ id: uuid, kind: z.string(), quantityDelta: z.string(), weightDeltaG: z.string() }).catchall(z.unknown())).min(1),
  reopening: z.object({ reopeningId: uuid, documentId: uuid, lineId: uuid, cycleBefore: positive, cycleAfter: positive,
    documentStatus: z.literal('EFFECTIVE'), type: z.enum(['PHYSICAL_RETURN', 'DISPATCH_CANCELLATION']),
    approvalReceipt: approval.extend({ commandType: z.literal('APPROVE') }) }).catchall(z.unknown()),
  stockRestorationReceipt: approval.extend({ commandType: z.literal('RECONCILE_RETURN_APPROVE'),
    businessDecision: z.object({ status: z.literal('RESOLVED'), bookStatus: z.literal('NOT_DEDUCTED'), dispatchCycle: positive }).catchall(z.unknown()) }),
  reopeningSha256: hash, returnReceiptSha256: hash, stockRestorationReceiptSha256: hash,
}).catchall(z.unknown()));
export type QuarantineHistory = z.infer<typeof quarantineHistorySchema>[number];
