import { z } from 'zod';
import { rangeSchema } from './contracts';
const grams = z.string().regex(/^\d+$/);
export const summarySchema = z.object({
  basis: z.literal('ORIGINAL_CONFIRMED'), range: rangeSchema, readAt: z.string(), orderCount: z.number().int().nonnegative(),
  actualCount: z.number().int().nonnegative(), weightGrams: grams,
  orders: z.array(z.object({ id: z.string().uuid(), orderNo: z.string(), revision: z.number().int().positive(),
    originalDay: z.iso.date(), vehicle: z.string(), actualCount: z.number().int().nonnegative(), weightGrams: grams })),
  products: z.array(z.object({ code: z.string(), name: z.string(), model: z.string(), plannedCount: z.number().int().nonnegative(),
    actualCount: z.number().int().nonnegative(), weightGrams: grams })),
});
export type Summary = z.infer<typeof summarySchema>;
export type ExportRange = z.infer<typeof rangeSchema>;
export function exactKg(value: string): string {
  const n = BigInt(value); const fraction = (n % 1000n).toString().padStart(3, '0').replace(/0+$/, '');
  return `${(n / 1000n).toLocaleString('zh-CN')}${fraction ? `.${fraction}` : ''}`;
}
export function shanghaiToday(): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts();
  return ['year', 'month', 'day'].map(type => parts.find(p => p.type === type)?.value ?? '').join('-');
}
