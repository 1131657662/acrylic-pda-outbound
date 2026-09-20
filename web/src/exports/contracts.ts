import { z } from 'zod';

export const formatSchema = z.enum(['XLSX', 'SCAN_XLSX', 'PDF']);
export type ExportFormat = z.infer<typeof formatSchema>;
export const formatLabels: Record<ExportFormat, string> = { XLSX: 'Excel', SCAN_XLSX: '扫码记录 Excel', PDF: 'PDF' };
export const exportSchema = z.object({
  id: z.string().uuid(), orderNo: z.string(), format: formatSchema, basis: z.literal('ORIGINAL_CONFIRMED'),
  resultVersion: z.literal(1), revision: z.number().int().positive(),
  status: z.enum(['PENDING', 'RUNNING', 'RETRY', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'INVALIDATED']),
  attempts: z.number().int().nonnegative(), errorCode: z.string().nullable(),
  createdAt: z.string(), expiresAt: z.string(), originalDay: z.string(), snapshotId: z.string(),
  sha256: z.string().nullable(), bytes: z.number().nullable(), downloadable: z.boolean(),
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
