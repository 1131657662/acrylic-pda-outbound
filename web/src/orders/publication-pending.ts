import { z } from 'zod';
import { receiptSchema } from './contracts';

const ownerSchema = z.object({ userId: z.uuid(), warehouseId: z.uuid(), orderId: z.uuid() }).strict();
export const publicationCommandSchema = z.object({ expectedVersion: z.string().regex(/^[1-9][0-9]*$/),
  expectedPolicyVersion: z.string().regex(/^[1-9][0-9]*$/), reason: z.string().trim().min(1).max(500) }).strict();
const savedSchema = ownerSchema.extend({ key: z.uuid(), epoch: z.string().regex(/^[1-9][0-9]*$/),
  body: z.string().max(10000), receipt: receiptSchema.nullable() }).strict();
export type PublicationOwner = z.infer<typeof ownerSchema>;
export type SavedPublication = z.infer<typeof savedSchema>;

function name(owner: PublicationOwner): string {
  const value = ownerSchema.parse({ userId: owner.userId, warehouseId: owner.warehouseId, orderId: owner.orderId });
  return `acrylic.publication.v1:${value.userId}:${value.warehouseId}:${value.orderId}`;
}
function validate(value: unknown, owner: PublicationOwner): SavedPublication {
  const saved = savedSchema.parse(value); publicationCommandSchema.parse(JSON.parse(saved.body));
  if (saved.userId !== owner.userId || saved.warehouseId !== owner.warehouseId || saved.orderId !== owner.orderId)
    throw new Error('原发布请求身份不匹配');
  if (saved.receipt && saved.receipt.orderId !== owner.orderId) throw new Error('原发布回执订单不匹配');
  return saved;
}
export function loadPublication(owner: PublicationOwner): SavedPublication | null {
  const raw = localStorage.getItem(name(owner));
  return raw === null ? null : validate(JSON.parse(raw), owner);
}
export function savePublication(value: SavedPublication): void {
  const saved = validate(value, value); const key = name(saved); const raw = JSON.stringify(saved);
  localStorage.setItem(key, raw);
  if (localStorage.getItem(key) !== raw) throw new Error('原发布请求未完整保存，请保留浏览器资料后重试');
}
export function clearPublication(owner: PublicationOwner): void {
  const key = name(owner); localStorage.removeItem(key);
  if (localStorage.getItem(key) !== null) throw new Error('原发布请求整理失败，请先核对原结果');
}
export async function lockPublication(owner: PublicationOwner, action: () => Promise<void>): Promise<void> {
  if (!navigator.locks) throw new Error('浏览器不能保护跨页原请求，本次发布未发送');
  await navigator.locks.request(name(owner), action);
}
