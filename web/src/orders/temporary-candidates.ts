import { z } from 'zod';

const day = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);
export const temporaryCandidateCheckSchema = z.object({ path: z.string(), advisoryOnly: z.literal(true) });
export const temporaryCandidatesSchema = z.object({
  orderId: z.string().uuid(), revision: z.number().int().positive(), orderVersion: z.string(),
  range: z.object({ fromDate: day, throughDate: day }), checkedAt: z.string(),
  candidates: z.object({ items: z.array(z.object({
    submissionId: z.string().uuid(), orderId: z.string().uuid(), orderNo: z.string(),
    status: z.enum(['SUCCEEDED', 'REVIEW_REQUIRED']), vehicleNo: z.string(), containerNo: z.string(),
    plannedTotal: z.string().regex(/^\d+$/), actualCount: z.string().regex(/^\d+$/), receivedAt: z.string(),
    sameVehicle: z.boolean(), overlappingProducts: z.boolean(),
  })), total: z.number().int().nonnegative(), page: z.number().int().positive(), pageSize: z.literal(20) }),
  hasFormalWork: z.boolean(), advisoryOnly: z.literal(true), releaseAllowed: z.literal(false),
});
export type TemporaryCandidates = z.infer<typeof temporaryCandidatesSchema>;
