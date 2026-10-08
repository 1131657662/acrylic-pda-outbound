import { z } from 'zod';
import { faultExpectedSchema, faultReceiptSchema } from './fault-contracts';

const id = z.string().uuid();
const decimal = z.string().regex(/^(0|[1-9]\d*)$/);
const state = z.string().min(1).max(40);
export const casePageSchema = z.object({
  warehouseId: id, orderId: id, orderNo: z.string(),
  items: z.array(z.object({ caseId: id, status: state, fromWorkRound: z.number().int().positive(),
    toWorkRound: z.number().int().positive(), requestedAt: z.string(), caseVersion: decimal })).max(100),
  hasMore: z.boolean(), nextCursor: z.string().min(1).max(300).nullable(),
}).refine(page => page.hasMore === (page.nextCursor !== null));

const optionalFact = z.union([id, z.literal('null'), z.null()]);
export const caseContextSchema = z.object({
  context: z.object({ caseId: id, warehouseId: id, canonicalOrderId: id, scopeId: id,
    caseVersion: decimal, status: state, sourceKind: z.enum(['FORMAL', 'TEMP']),
    fromWorkRound: decimal, toWorkRound: decimal, stopState: state, integrityState: state,
    originalResultState: state, physicalState: state, requestedBy: id,
    targetDeviceId: optionalFact, targetUserId: optionalFact, approvedBy: optionalFact }),
  originalCommand: z.object({ commandType: z.literal('REQUEST'), commandId: id, warehouseId: id,
    scopeId: id, reason: z.string() }),
  capabilities: z.record(z.string(), z.literal(false)),
  expected: faultExpectedSchema,
  fault: z.object({ entryAvailable: z.boolean(), attachmentsAvailable: z.boolean().optional().default(false),
    sourceDeviceId: id.nullable(), receipt: faultReceiptSchema.nullable() })
    .refine(f => !f.entryAvailable || (f.sourceDeviceId !== null && f.receipt === null)),
});
export type CasePage = z.infer<typeof casePageSchema>;
export type CaseContext = z.infer<typeof caseContextSchema>;

const labels: Record<string, string> = {
  REQUESTED: '已申请，等待核对', EVIDENCE_PENDING: '等待资料', REVIEW_READY: '待审核', APPROVED: '已批准准备',
  TARGET_PREPARED: '接收端已准备', ACTIVATED: '服务器已激活', RECONCILIATION_REQUIRED: '需要对账',
  REJECTED: '已拒绝', WITHDRAWN: '已撤回', CLOSED_INCOMPLETE: '已关闭，资料不完整',
  DEVICE_ACKED: '已收到停止证明', FAULT_FENCED: '故障隔离', COMPLETE: '资料完整', MISSING_RECORDS: '缺少记录',
  MISSING_AUDIT: '缺少审计', UNKNOWN_HIGH_WATERMARK: '记录截止位置未知', NONE_VERIFIED: '已核对无原结果',
  NONTERMINAL: '原提交尚未终结', ABORTED: '原提交已中止', SUCCEEDED: '原提交已成功', UNKNOWN: '尚未核明',
  UNCHECKED: '尚未核验', MATCHED: '核验一致', CONFLICT: '存在冲突', QUARANTINED: '实物隔离',
};
export function stateLabel(value: string): string { return labels[value] ?? '状态待核对'; }
export function factLabel(value: string | null): string { return !value || value === 'null' ? '尚未指定' : value; }
export function requestedTime(value: string): string {
  const date = new Date(`${value}Z`);
  return Number.isNaN(date.getTime()) ? '时间待核对' : date.toLocaleString('zh-CN', { hour12: false });
}
