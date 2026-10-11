import { z } from 'zod';
import { faultWithdrawalCommandSchema, faultWithdrawalSchema } from './fault-withdrawal-contracts';

const ownerSchema = z.object({ userId: z.uuid(), warehouseId: z.uuid(), orderId: z.uuid() }).strict();
const savedSchema = ownerSchema.extend({ key: z.uuid(), epoch: z.string().regex(/^[1-9][0-9]*$/),
  body: z.string().max(10000), receipt: faultWithdrawalSchema.nullable() }).strict();
export type WithdrawalOwner = z.infer<typeof ownerSchema>;
export type SavedWithdrawal = z.infer<typeof savedSchema>;
function key(owner: WithdrawalOwner): string {
  const value = ownerSchema.parse({ userId: owner.userId, warehouseId: owner.warehouseId, orderId: owner.orderId });
  return `acrylic.fault-withdrawal.v1:${value.userId}:${value.warehouseId}:${value.orderId}`;
}
function validate(value: unknown, owner: WithdrawalOwner): SavedWithdrawal {
  const saved = savedSchema.parse(value); faultWithdrawalCommandSchema.parse(JSON.parse(saved.body));
  if (saved.userId !== owner.userId || saved.orderId !== owner.orderId || saved.warehouseId !== owner.warehouseId)
    throw new Error('原故障撤回请求身份不匹配');
  if (saved.receipt && (saved.receipt.orderId !== owner.orderId || saved.receipt.resolvedBy !== owner.userId))
    throw new Error('原故障撤回回执身份不匹配');
  return saved;
}
export function loadWithdrawal(owner: WithdrawalOwner): SavedWithdrawal | null {
  const raw = localStorage.getItem(key(owner));
  return raw === null ? null : validate(JSON.parse(raw), owner);
}
export function saveWithdrawal(value: SavedWithdrawal): void {
  const saved = validate(value, { userId: value.userId, warehouseId: value.warehouseId, orderId: value.orderId });
  const name = key(saved); const raw = JSON.stringify(saved);
  localStorage.setItem(name, raw);
  if (localStorage.getItem(name) !== raw) throw new Error('原撤回请求未能完整保存，请保留浏览器资料后重试');
}
export function clearWithdrawal(owner: WithdrawalOwner): void {
  const name = key(owner); localStorage.removeItem(name);
  if (localStorage.getItem(name) !== null) throw new Error('原撤回请求整理失败，请先核对原结果');
}
export async function lockWithdrawal(owner: WithdrawalOwner, action: () => Promise<void>): Promise<void> {
  if (!navigator.locks) throw new Error('浏览器不能保护跨页原请求，本次撤回未发送');
  await navigator.locks.request(key(owner), action);
}
