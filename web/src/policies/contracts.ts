import { z } from 'zod';

export const policySchema = z.object({ id: z.string().uuid(), version: z.string(), autoDeductInventory: z.boolean(),
  schemaVersion: z.number().int(), offlineHours: z.number().int(), shortShipmentPolicy: z.string(),
  concurrencyMode: z.string(), scanMode: z.string(), reason: z.string(), publishedBy: z.string(), publishedAt: z.string() });
export const currentPolicySchema = z.object({ policy: policySchema.nullable() });
export type Policy = z.infer<typeof policySchema>;
