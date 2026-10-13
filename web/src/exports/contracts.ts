import { z } from 'zod';

export const formatSchema = z.enum(['XLSX', 'SCAN_XLSX', 'PDF']);
export type ExportFormat = z.infer<typeof formatSchema>;
export const formatLabels: Record<ExportFormat, string> = { XLSX: 'Excel', SCAN_XLSX: '扫码记录 Excel', PDF: 'PDF' };
export const rangeSchema = z.object({ fromDate: z.iso.date(), throughDate: z.iso.date() });
export const exportSchema = z.object({
  id: z.string().uuid(), orderNo: z.string(), format: formatSchema, basis: z.enum(['CURRENT_EFFECTIVE', 'ORIGINAL_CONFIRMED', 'CORRECTED']),
  resultVersion: z.number().int().nonnegative(), revision: z.number().int().nonnegative(),
  status: z.enum(['PENDING', 'RUNNING', 'RETRY', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'INVALIDATED']),
  attempts: z.number().int().nonnegative(), errorCode: z.string().nullable(),
  createdAt: z.string(), expiresAt: z.string(), originalDay: z.string(), snapshotId: z.string(),
  sha256: z.string().nullable(), bytes: z.number().nullable(), downloadable: z.boolean(),
  orderId: z.string().uuid().nullable(), range: rangeSchema.nullable(), orderCount: z.number().int().nonnegative(),
  retryOf: z.string().uuid().nullable(),
  members: z.array(z.object({ orderId: z.string().uuid(), resultVersion: z.number().int().positive() })).default([]),
});
export const exportsSchema = z.object({ items: z.array(exportSchema), nextCursor: z.string().uuid().nullable() });
export const pendingSchema = z.object({ key: z.string().uuid(), format: formatSchema, epoch: z.string().min(1) });
export type ExportJob = z.infer<typeof exportSchema>;
export type PendingExport = z.infer<typeof pendingSchema>;
export const statusLabels: Record<ExportJob['status'], string> = {
  PENDING: '排队中', RUNNING: '生成中', RETRY: '等待重试', SUCCEEDED: '已生成', FAILED: '生成失败',
  EXPIRED: '已过期', INVALIDATED: '恢复代次已变化',
};
export const activeJob = (job: ExportJob): boolean => ['PENDING', 'RUNNING', 'RETRY'].includes(job.status);
export function basisLabel(job: ExportJob): string {
  if (job.basis === 'ORIGINAL_CONFIRMED') return '原确认 V1';
  if (job.basis === 'CORRECTED') return `指定更正净值 V${job.resultVersion}`;
  return job.range ? '截止时最新净值 · 各单版本已固定' : `截止时最新净值 V${job.resultVersion}`;
}
