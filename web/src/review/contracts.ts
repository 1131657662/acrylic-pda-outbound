import { z } from 'zod';

export const reminderSchema = z.object({
  id: z.string().uuid(), submissionId: z.string().uuid(), orderId: z.string().uuid(),
  orderNo: z.string(), vehicleNo: z.string(), submittedAt: z.iso.datetime(),
  dueAt: z.iso.datetime(), remindedAt: z.iso.datetime(), sourceType: z.enum(['FORMAL', 'TEMP']),
});
export type Reminder = z.infer<typeof reminderSchema>;
