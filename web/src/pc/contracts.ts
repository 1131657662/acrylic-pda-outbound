import { z } from 'zod';
const numberText = z.string().regex(/^\d+$/);
export const workSchema = z.object({ id: z.string().uuid(), warehouse: z.string().uuid(), order: z.string().uuid(), revision: z.number(),
  operator: z.string().uuid(), operatorName: z.string(), epoch: numberText, status: z.enum(['ACTIVE', 'REVIEW_REQUIRED', 'REJECTED', 'SUCCEEDED']),
  version: numberText, writerId: z.string().uuid(), fence: numberText, nextSequence: z.number(), round: z.number(), deduct: z.boolean() });
export const productSchema = z.object({ deliveryCode: z.string(), model: z.string(), name: z.string(), plannedCount: z.number(), actualCount: z.number(), weightGrams: numberText });
export const submissionSchema = z.object({ id: z.string().uuid(), round: z.number(), status: z.enum(['REVIEW_REQUIRED', 'REJECTED', 'SUCCEEDED']),
  payloadSha256: z.string(), actualCount: numberText, weightGrams: numberText, shortReason: z.string(), createdAt: z.string(),
  issues: z.array(z.object({ code: z.string(), barcode: z.string().optional(), reason: z.string().optional() })),
  receipt: z.object({ status: z.literal('SUCCEEDED'), inventoryDeducted: z.boolean(), resultVersion: numberText }).passthrough().nullable() });
export const viewSchema = z.object({ work: workSchema.nullable(), products: z.array(productSchema), submission: submissionSchema.nullable() });
export const candidateSchema = z.object({ baleId: z.string().uuid(), barcode: z.string(), deliveryCode: z.string(), model: z.string(), name: z.string(),
  weightGrams: numberText, availability: z.string() });
export const groupSchema = z.object({ deliveryCode: z.string(), total: z.number(), page: z.number(), pageSize: z.number(), items: z.array(candidateSchema) });
export const candidatesSchema = z.object({ query: z.string(), matchedCodes: z.number(), total: z.number(), groups: z.array(groupSchema) });
export const entrySchema = z.object({ id: z.string().uuid(), baleId: z.string().uuid(), barcode: z.string(), sequence: numberText, cycle: z.number(),
  status: z.enum(['ACTIVE', 'VOIDED']), deliveryCode: z.string(), weightGrams: numberText, model: z.string(), name: z.string(),
  operatorName: z.string(), createdAt: z.string(), voidReason: z.string().nullable() });
export const entriesSchema = z.object({ items: z.array(entrySchema), total: z.number(), page: z.number(), pageSize: z.number() });
export const receiptSchema = z.object({ receiptId: z.string().uuid(), requestKey: z.string(), orderId: z.string().uuid(), action: z.string(), result: z.unknown() });
export type Work = z.infer<typeof workSchema>;
export type PcView = z.infer<typeof viewSchema>;
export type Product = z.infer<typeof productSchema>;
export type Candidate = z.infer<typeof candidateSchema>;
export type Candidates = z.infer<typeof candidatesSchema>;
export type Entry = z.infer<typeof entrySchema>;
export type Submission = z.infer<typeof submissionSchema>;
export const workLabels: Record<Work['status'], string> = { ACTIVE: 'PC 录入中', REVIEW_REQUIRED: '待主管审核', REJECTED: '已退回，保留占用', SUCCEEDED: '后台已确认出库' };
export const availabilityLabels: Record<string, string> = { AVAILABLE: '可录入', RECORDED: '本单已录入', DISPATCHED: '已确认出库', QUARANTINED: '实物已隔离', RESERVED: '已有作业占用', UNAVAILABLE: '库存不可用', CHANGED: '库存资料已变化', PLAN_FULL: '该产品计划已满' };
