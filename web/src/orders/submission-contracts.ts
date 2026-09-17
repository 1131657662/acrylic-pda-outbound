import { z } from 'zod';

export const submissionSchema = z.object({
  submissionId: z.string().uuid(), orderId: z.string().uuid(), status: z.enum(['REGISTERED', 'RECEIVING', 'REVIEW_REQUIRED', 'REJECTED', 'ABORTED', 'SUCCEEDED']),
  payloadSha256: z.string().nullable(), actualCount: z.string().optional(), actualWeightGrams: z.string().optional(),
  inventoryDeducted: z.boolean().optional(), completedAt: z.string().optional(),
  issues: z.array(z.object({ code: z.string(), barcode: z.string().optional(), reason: z.string().optional() })).optional(),
});
export const submissionDetailSchema = z.object({
  submission: submissionSchema.nullable(), shortShipmentReason: z.string().optional(), expectedCount: z.string().optional(), expectedWeightGrams: z.string().optional(),
  scans: z.array(z.object({ captureId: z.string(), orderScanSeq: z.string(), barcode: z.string(), deliveryCode: z.string(),
    weightGrams: z.string(), status: z.enum(['ACTIVE', 'VOID']) })).optional(),
});
export type SubmissionDetail = z.infer<typeof submissionDetailSchema>;
export function kilograms(grams: string): string {
  const value = BigInt(grams); const fraction = (value % 1000n).toString().padStart(3, '0').replace(/0+$/, '');
  return `${value / 1000n}${fraction ? `.${fraction}` : ''}`;
}
export const submissionLabel: Record<z.infer<typeof submissionSchema>['status'], string> = {
  REGISTERED: '提交已登记', RECEIVING: '接收中', REVIEW_REQUIRED: '待主管复核', REJECTED: '已退回，保留占用',
  ABORTED: '传输已取消，保留占用', SUCCEEDED: '云端已确认出库',
};
