import { z } from 'zod';

export const importSchema = z.object({
  id: z.string().uuid(), warehouseId: z.string().uuid(), originalFilename: z.string(), fileSha256: z.string(),
  parserVersion: z.string(), status: z.enum(['READY_FOR_REVIEW', 'BLOCKED', 'NEEDS_DEDUP_CONFIRMATION']),
  totalRows: z.number().int(), validRows: z.number().int(), errorRows: z.number().int(),
  duplicateRows: z.number().int(), ignoredRows: z.number().int(), productCount: z.number().int(),
  totalWeightGrams: z.string().regex(/^\d+$/), createdAt: z.string(), publishedSnapshotId: z.string().nullable(),
});
export const rowSchema = z.object({
  lineNo: z.number().int(), rawText: z.string(), errorCode: z.string().nullable(), errorDetail: z.string().nullable(),
  parsedFields: z.object({ model: z.string(), name: z.string(), barcode: z.string(), deliveryCode: z.string(),
    rawWeight: z.string(), weightGrams: z.string().regex(/^\d+$/) }).nullable(),
});
export const importPageSchema = z.object({ items: z.array(importSchema), nextCursor: z.string().nullable(), hasMore: z.boolean() });
export const rowPageSchema = z.object({ items: z.array(rowSchema), nextCursor: z.string().nullable(), hasMore: z.boolean() });
export type ImportSummary = z.infer<typeof importSchema>;
export type ImportRow = z.infer<typeof rowSchema>;

export function weightKg(grams: string): string {
  const value = BigInt(grams);
  const fraction = (value % 1000n).toString().padStart(3, '0').replace(/0+$/, '');
  return `${(value / 1000n).toLocaleString('zh-CN')}${fraction ? `.${fraction}` : ''}`;
}
export function statusLabel(status: ImportSummary['status']): string {
  return { READY_FOR_REVIEW: '预检通过 · 未发布', BLOCKED: '存在错误 · 阻断', NEEDS_DEDUP_CONFIRMATION: '待确认去重' }[status];
}
export { formatDateTime as localTime } from '../shared/date-time';
