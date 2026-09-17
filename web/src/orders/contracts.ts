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
  const labels: Record<string, string> = { DRAFT: '草稿', READY: '待领取', ASSIGNED: '已分配', RECALL_PENDING: '待召回',
    REVIEW_REQUIRED: '待复核', COMPLETED: '已确认出库', CANCELLED: '已作废', CONTINUING: '续作中', LINKED: '已关联' };
  return labels[status] ?? status;
}
export function localTime(value: string): string { return new Date(value).toLocaleString('zh-CN', { hour12: false }); }
