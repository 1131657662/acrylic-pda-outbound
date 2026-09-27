import { z } from 'zod';
import { temporaryReviewSchema } from './contracts';

const count = z.string().regex(/^[0-9]+$/);
const hash = z.string().regex(/^[a-f0-9]{64}$/);
const product = z.object({ deliveryCode: z.string(), productId: z.string().uuid(), model: z.string(), name: z.string(), plannedCount: count });
const context = temporaryReviewSchema.pick({ source: true, submissionId: true, payloadSha256: true, planSha256: true, workRound: true,
  currentSubmissionId: true, current: true, status: true, orderRevision: true, vehicleNo: true, containerNo: true, products: true,
  inventorySnapshotId: true, sourceRecoveryEpoch: true, serverRecoveryEpoch: true, serviceMode: true, reviewerPermissionVersion: true,
  receivedAt: true, closedAt: true }).extend({ canonicalOrderId: z.string().uuid() });
export const duplicateCandidateSchema = z.object({
  order: z.object({ id: z.string().uuid(), orderNo: z.string(), status: z.string(), revision: z.number().int().positive(), rowVersion: count,
    revisionId: z.string().uuid(), vehicleNo: z.string(), containerNo: z.string(), createdAt: z.iso.datetime(), updatedAt: z.iso.datetime(),
    inventorySnapshotId: z.string().uuid(), mappingRevision: count, hasAssignment: z.boolean(), hasPcWork: z.boolean(),
    hasSubmission: z.boolean(), hasResult: z.boolean() }),
  products: z.array(product), signals: z.array(z.enum(['SAME_VEHICLE', 'SHARED_PRODUCT'])), blockers: z.array(z.enum([
    'TARGET_NOT_READY', 'PDA_WORK_EXISTS', 'PC_WORK_EXISTS', 'SUBMISSION_EXISTS', 'RESULT_EXISTS', 'PLAN_DIFFERS',
    'INVENTORY_VERSION_DIFFERS', 'SOURCE_NOT_PENDING',
  ])), linkAvailable: z.boolean(),
});
export const duplicatePageSchema = z.object({ context, range: z.object({ fromDate: z.iso.date(), throughDate: z.iso.date() }),
  checkedAt: z.iso.datetime(), contextSha256: hash, candidateSetSha256: hash, total: z.number().int().nonnegative().safe(),
  items: z.array(duplicateCandidateSchema).max(200), nextCursor: z.string().nullable(), hasMore: z.boolean(),
  writeAvailable: z.boolean(), releaseAllowed: z.literal(false) });
export type DuplicatePage = z.infer<typeof duplicatePageSchema>;
export type DuplicateCandidate = z.infer<typeof duplicateCandidateSchema>;

export function duplicateReason(code: string): string {
  const labels: Record<string, string> = {
    SAME_VEHICLE: '同车号', SHARED_PRODUCT: '有相同产品', TARGET_NOT_READY: '正式单不是待领取状态',
    PDA_WORK_EXISTS: '已有 PDA 领取／作业记录', PC_WORK_EXISTS: '已有 PC 作业记录', SUBMISSION_EXISTS: '已有提交批次',
    RESULT_EXISTS: '已有出库结果，不能关联成功事实', PLAN_DIFFERS: '产品、名称／型号或计划包数不一致',
    INVENTORY_VERSION_DIFFERS: '库存版本不同，须核对原资料', SOURCE_NOT_PENDING: '原临时批次不是当前待确认批次',
  };
  return labels[code] ?? code;
}

export function duplicateRangeError(from: string, through: string): string {
  const valid = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(`${value}T00:00:00Z`))
    && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value;
  if (!valid(from) || !valid(through)) return '请填写完整、有效的首尾日期。';
  if (from < '2000-01-01' || through > '9998-12-31' || from > through) return '请核对日期先后，年份须为 2000 至 9998。';
  if ((Date.parse(through) - Date.parse(from)) / 86_400_000 >= 366) return '一次查询最多 366 天，请缩小范围后继续核对。';
  return '';
}
