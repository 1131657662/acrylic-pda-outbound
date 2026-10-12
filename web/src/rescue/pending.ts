import { z } from 'zod';
import { approvalSchema, revokeSchema, grantReceiptSchema, revocationReceiptSchema } from './contracts';

const ownerSchema = z.object({ userId: z.uuid(), warehouseId: z.uuid(), requestId: z.uuid() }).strict();
const base = ownerSchema.extend({ key: z.uuid(), epoch: z.string().regex(/^[1-9][0-9]*$/), body: z.string().max(10000) });
const savedSchema = z.discriminatedUnion('action', [
  base.extend({ action: z.literal('APPROVE'), receipt: grantReceiptSchema.nullable(), rejectedExpiry: z.boolean().default(false) }).strict(),
  base.extend({ action: z.literal('REVOKE'), grantId: z.uuid(), receipt: revocationReceiptSchema.nullable() }).strict(),
]);
export type RescueOwner = z.infer<typeof ownerSchema>;
export type SavedRescueCommand = z.infer<typeof savedSchema>;
function key(owner: RescueOwner, action: SavedRescueCommand['action']): string {
  const value = ownerSchema.parse({ userId: owner.userId, warehouseId: owner.warehouseId, requestId: owner.requestId });
  return `acrylic.rescue.v1:${value.userId}:${value.warehouseId}:${value.requestId}:${action}`;
}
function validate(value: unknown, owner: RescueOwner): SavedRescueCommand {
  const saved = savedSchema.parse(value);
  if (saved.userId !== owner.userId || saved.warehouseId !== owner.warehouseId || saved.requestId !== owner.requestId)
    throw new Error('原救援命令身份不匹配');
  if (saved.action === 'APPROVE') {
    approvalSchema.parse(JSON.parse(saved.body));
    if (saved.rejectedExpiry && saved.receipt) throw new Error('拒绝记录不能包含成功授权回执');
  }
  else {
    revokeSchema.parse(JSON.parse(saved.body));
    if (saved.receipt && saved.receipt.grantId !== saved.grantId) throw new Error('原撤销回执不属于原授权');
  }
  return saved;
}
export function loadCommand(owner: RescueOwner, action: SavedRescueCommand['action']): SavedRescueCommand | null {
  const raw = localStorage.getItem(key(owner, action));
  if (raw === null) return null;
  const saved = validate(JSON.parse(raw), owner);
  if (saved.action !== action) throw new Error('原救援命令类型不一致');
  return saved;
}
export function saveCommand(value: SavedRescueCommand): void {
  const saved = validate(value, value); const name = key(saved, saved.action); const raw = JSON.stringify(saved);
  const previous = loadCommand(saved, saved.action);
  if (previous && (previous.key !== saved.key || previous.body !== saved.body || previous.epoch !== saved.epoch
    || previous.action === 'REVOKE' && saved.action === 'REVOKE' && previous.grantId !== saved.grantId
    || previous.action === 'APPROVE' && saved.action === 'APPROVE' && previous.rejectedExpiry && !saved.rejectedExpiry
    || previous.receipt && JSON.stringify(previous.receipt) !== JSON.stringify(saved.receipt)))
    throw new Error('原救援命令或回执不替换，请保留浏览器原件');
  localStorage.setItem(name, raw);
  if (localStorage.getItem(name) !== raw) throw new Error('救援命令未完整保存，尚未发送');
}
export function archiveRejectedApproval(owner: RescueOwner): void {
  const saved = loadCommand(owner, 'APPROVE');
  if (!saved || saved.action !== 'APPROVE' || !saved.rejectedExpiry || saved.receipt) throw new Error('原请求结果尚未明确拒绝，不能另建核准');
  const active = key(owner, 'APPROVE'); const archive = `${active}:rejected:${saved.key}`; const raw = JSON.stringify(saved);
  const previous = localStorage.getItem(archive);
  if (previous !== null && previous !== raw) throw new Error('拒绝原件不一致，请保留浏览器资料');
  localStorage.setItem(archive, raw);
  if (localStorage.getItem(archive) !== raw) throw new Error('拒绝原件未完整保留，不能另建核准');
  localStorage.removeItem(active);
  if (localStorage.getItem(active) !== null) throw new Error('原请求仍待核对，不能另建核准');
}
export async function lockCommand(owner: RescueOwner, action: SavedRescueCommand['action'], work: () => Promise<void>): Promise<void> {
  if (!navigator.locks) throw new Error('浏览器不能保护跨页原请求，本次命令未发送');
  await navigator.locks.request(key(owner, action), work);
}
