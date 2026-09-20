import { z } from 'zod';

const count = z.string().regex(/^[0-9]+$/);
export const temporaryReviewItemSchema = z.object({
  submissionId: z.string().uuid(), orderId: z.string().uuid(), orderNo: z.string(), vehicleNo: z.string(),
  receivedAt: z.iso.datetime(), expectedCount: count, expectedWeightGrams: count, status: z.literal('REVIEW_REQUIRED'),
});
const issueSchema = z.object({ code: z.string(), count });
export const temporaryReviewSchema = z.object({
  submissionId: z.string().uuid(), orderId: z.string().uuid(), orderNo: z.string(), temporaryNo: z.string(),
  status: z.string(), current: z.boolean(), currentSubmissionId: z.string().uuid().nullable(),
  vehicleNo: z.string(), containerNo: z.string(), reason: z.string(), shortShipmentReason: z.string().nullable(),
  source: z.object({ kind: z.literal('TEMP'), creatorDeviceId: z.string().uuid(), clientOrderId: z.string().uuid() }),
  payloadSha256: z.string(), planSha256: z.string(), workRound: count, orderRevision: count,
  sourceRecoveryEpoch: count, serverRecoveryEpoch: count, serviceMode: z.string(), reviewerPermissionVersion: count,
  registeredAt: z.iso.datetime(), receivedAt: z.iso.datetime(), checkedAt: z.iso.datetime(), closedAt: z.iso.datetime(),
  operatorId: z.string().uuid(), operatorName: z.string(), deviceName: z.string(),
  expectedCount: count, expectedWeightGrams: count, inventorySnapshotId: z.string().uuid(), policyVersion: z.string(), autoDeductInventory: z.boolean(),
  writeConfirmationAvailable: z.literal(false), releaseAllowed: z.literal(false),
  authorization: z.object({ authorizationId: z.string().uuid(), issuedAt: z.iso.datetime(), expiresAt: z.iso.datetime(),
    userVersion: count, deviceVersion: count, recoveryEpoch: count }),
  products: z.array(z.object({ deliveryCode: z.string(), productId: z.string().uuid(), model: z.string(), name: z.string(),
    plannedCount: count, actualCount: count, shortCount: count, weightGrams: count })),
  receivedIssues: z.array(issueSchema), currentIssues: z.array(issueSchema),
});
export const temporaryScanSchema = z.object({ captureId: z.string().uuid(), orderScanSeq: count, barcode: z.string(),
  deliveryCode: z.string(), model: z.string(), name: z.string(), weightGrams: count, expectedDispatchCycle: count,
  status: z.enum(['ACTIVE', 'VOID']), savedAt: z.iso.datetime(), currentIssue: z.string().nullable() });
export type TemporaryReview = z.infer<typeof temporaryReviewSchema>;
export type TemporaryReviewItem = z.infer<typeof temporaryReviewItemSchema>;

export function issueLabel(code: string): string {
  const labels: Record<string, string> = {
    TEMPORARY_ORDER_CONFIRMATION_REQUIRED: '待独立确认临时出库', TEMPORARY_AUTH_EXPIRED: '原离线资格已到期，需核对原作业',
    TEMPORARY_ACTIVATION_UNACKNOWLEDGED: '原设备资料激活声明尚未确认', TEMPORARY_AUTH_VERSION_CHANGED: '原人员或设备版本已变化',
    TEMPORARY_OPERATOR_DISABLED: '原作业人员已停用', TEMPORARY_DEVICE_DISABLED: '原设备已停用', TEMPORARY_ADMISSION_DISABLED: '原设备临时作业准入已关闭',
    TEMPORARY_OPERATOR_PERMISSION_CHANGED: '原人员当前作业权限已变化', TEMPORARY_OPERATOR_WAREHOUSE_CHANGED: '原人员当前仓库授权已变化',
    TEMPORARY_RECOVERY_REQUIRED: '原资料恢复代次已变化，须对账', SERVER_RECOVERY_REQUIRED: '后台正在恢复对账',
    SUBMISSION_NOT_CURRENT: '此批次已不是当前批次', SUBMISSION_NOT_PENDING: '此批次已不处于待确认状态',
    SHORT_SHIPMENT_APPROVAL_REQUIRED: '短装仍须独立主管审批，临时确认权限不能代替', DISPATCH_CYCLE_CONFLICT: '包的出库周期已变化',
    BALE_ALREADY_COMMITTED: '包已被其他作业占用', INVENTORY_UNAVAILABLE: '当前库存不可用', INVENTORY_BASELINE_CHANGED: '当前库存资料与原记录不一致',
    TEMPORARY_RESERVATION_RECHECK_REQUIRED: '原批次占用须重新核对',
  };
  return labels[code] ?? `待核对事项（${code}）`;
}
