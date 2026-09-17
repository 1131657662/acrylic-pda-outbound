import { z } from 'zod';
import { importSchema, rowSchema } from './contracts';

export const baselineSchema = z.object({ activeSnapshotId: z.string().nullable(), mappingRevision: z.string() });
export const diffSchema = z.object({ imported: importSchema, baseline: baselineSchema, added: z.number(), removed: z.number(),
  changed: z.number(), unchanged: z.number(), mappingChangeCount: z.number() });
export const mappingSchema = z.object({ id: z.string(), deliveryCode: z.string(), version: z.string(), productId: z.string(),
  model: z.string(), name: z.string(), enabled: z.boolean(), reason: z.string(), current: z.boolean(), createdAt: z.string() });
export const mappingChangeSchema = z.object({ deliveryCode: z.string(), previous: mappingSchema.nullable(), model: z.string(), name: z.string(), enabled: z.boolean() });
export const changeSchema = z.object({ barcode: z.string(), type: z.string(), before: rowSchema.shape.parsedFields, after: rowSchema.shape.parsedFields });
export const receiptSchema = z.object({ id: z.string(), version: z.string(), importId: z.string(), rowCount: z.number(),
  totalWeightGrams: z.string(), mappingRevision: z.string(), manifestSha256: z.string(), publishedAt: z.string() });
export const baleSchema = z.object({ barcode: z.string(), dispatchCycle: z.string(), deliveryCode: z.string(), model: z.string(), name: z.string(),
  weightGrams: z.string(), rawWeight: z.string(), upstreamPresent: z.boolean(), bookStatus: z.string(), blockedReason: z.string().nullable(),
  lastSeenSnapshotId: z.string(), evaluatedSnapshotId: z.string() });
export const discrepancySchema = z.object({ id: z.string(), barcode: z.string(), type: z.string(), status: z.string(),
  oldSnapshotId: z.string().nullable(), newSnapshotId: z.string(), oldValues: z.string().nullable(), newValues: z.string().nullable(), createdAt: z.string() });
export function pageSchema<T extends z.ZodType>(item: T) {
  return z.object({ items: z.array(item), nextCursor: z.string().nullable(), hasMore: z.boolean() });
}
export type Diff = z.infer<typeof diffSchema>;
export type Mapping = z.infer<typeof mappingSchema>;
export type MappingChange = z.infer<typeof mappingChangeSchema>;
export type Bale = z.infer<typeof baleSchema>;
export type Receipt = z.infer<typeof receiptSchema>;
export function changeLabel(type: string): string {
  const labels: Record<string, string> = { ADDED: '新增', UPSTREAM_MISSING: '上游未列出', PRODUCT_CHANGED: '型号变更',
    WEIGHT_OR_NAME_CHANGED: '重量或名称变更', UPSTREAM_BOOK_CONFLICT: '上游与扣账不一致' };
  return labels[type] ?? type;
}
