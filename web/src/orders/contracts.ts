import { z } from 'zod';
import { baselineSchema, pageSchema } from '../inventory/master-contracts';

export const orderSchema = z.object({ id: z.string().uuid(), orderNo: z.string(), source: z.string(), status: z.string(),
  revision: z.number().int(), rowVersion: z.string(), vehicleNo: z.string(), containerNo: z.string(), plannedTotal: z.string(),
  createdBy: z.string(), createdAt: z.string(), updatedAt: z.string() });
export const revisionSchema = z.object({ id: z.string().uuid(), revision: z.number().int(), inventorySnapshotId: z.string().uuid(),
  mappingRevision: z.string(), vehicleNo: z.string(), containerNo: z.string(), notes: z.string(), plannedTotal: z.string(),
  reason: z.string(), createdBy: z.string(), createdAt: z.string() });
export const lineSchema = z.object({ deliveryCode: z.string(), mappingId: z.string(), productId: z.string(),
  model: z.string(), name: z.string(), plannedCount: z.number().int() });
export const detailSchema = z.object({ order: orderSchema, plan: revisionSchema, lines: z.array(lineSchema) });
export const orderPageSchema = z.object({ items: z.array(orderSchema), total: z.number().int().nonnegative(),
  page: z.number().int().positive(), pageSize: z.union([z.literal(20), z.literal(50), z.literal(100)]) });
export const productSchema = z.object({ deliveryCode: z.string(), mappingId: z.string(), productId: z.string(), model: z.string(),
  name: z.string(), enabled: z.boolean(), mappingVersion: z.string(), snapshotCount: z.number(), candidateCount: z.number() });
export const optionsSchema = z.object({ baseline: baselineSchema, products: pageSchema(productSchema) });
export const receiptSchema = z.object({ id: z.string().uuid(), orderId: z.string().uuid(), revision: z.number().int(),
  rowVersion: z.string(), status: z.string() });
export const preflightSchema = z.object({ orderVersion: z.string(), baseline: baselineSchema, checksPassed: z.boolean(),
  dispatchAvailable: z.boolean(), blockers: z.array(z.object({ code: z.string(), message: z.string() })),
  lines: z.array(z.object({ deliveryCode: z.string(), plannedCount: z.number(), mappingValid: z.boolean(),
    snapshotCount: z.number(), candidateCount: z.number(), shortage: z.number() })), checkedAt: z.string() });
export const eventSchema = z.object({ id: z.string(), type: z.string(), actor: z.string().nullable(), details: z.string(), serverTime: z.string() });
export type Order = z.infer<typeof orderSchema>;
export type Detail = z.infer<typeof detailSchema>;
export type Product = z.infer<typeof productSchema>;
export type Preflight = z.infer<typeof preflightSchema>;
export interface PlanLine { deliveryCode: string; plannedCount: number | undefined }
export function statusLabel(status: string): string {
  return Object.hasOwn(orderStatusLabels, status) ? orderStatusLabels[status]! : '状态待核对';
}
export const orderStatusLabels: Record<string, string> = { DRAFT: '草稿', READY: '待领取', ASSIGNED: '已领取',
  RECALL_PENDING: '已申请停止，待原机核对', REVIEW_REQUIRED: '待主管处理', COMPLETED: '后台已确认出库',
  CANCELLED: '已作废', CONTINUING: '恢复装车处理中', LINKED: '已关联正式订单' };
export function localTime(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '暂无记录' : date.toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' });
}
export function countLabel(value: string | number): string {
  return /^\d+$/.test(String(value)) ? BigInt(value).toLocaleString('zh-CN') : '暂无记录';
}
