import { z } from 'zod';
import { conflictEvidenceSchema, conflictResultSchema } from './conflict-contracts';
import type { PhysicalOwner } from './physical-contracts';
const ownerSchema = z.object({ userId: z.uuid(), warehouseId: z.uuid(), relayId: z.uuid() });
const savedSchema = ownerSchema.extend({ version: z.literal(1), command: z.object({ key: z.uuid(),
  epoch: z.string().regex(/^[1-9][0-9]*$/), body: conflictEvidenceSchema,
  receipt: conflictResultSchema.shape.receipt.nullable() }).strict().nullable() }).strict();
export type ConflictSaved = z.infer<typeof savedSchema>;
function key(owner: PhysicalOwner): string {
  ownerSchema.parse(owner); return `acrylic.independent-conflict.v1:${owner.userId}:${owner.warehouseId}:${owner.relayId}`;
}
function validate(value: unknown, owner: PhysicalOwner): ConflictSaved {
  const saved = savedSchema.parse(value);
  if (saved.userId !== owner.userId || saved.warehouseId !== owner.warehouseId || saved.relayId !== owner.relayId)
    throw new Error('原冲突请求范围不一致');
  const r = saved.command?.receipt;
  if (r && (r.supervisorId !== owner.userId || r.relayId !== owner.relayId || r.warehouseId !== owner.warehouseId))
    throw new Error('原冲突回执身份不一致');
  return saved;
}
export function loadConflict(owner: PhysicalOwner): ConflictSaved {
  try {
    const raw = localStorage.getItem(key(owner));
    return raw === null ? { ...owner, version: 1, command: null } : validate(JSON.parse(raw), owner);
  } catch { throw new Error('无法读取原冲突请求，请保留浏览器存储并恢复后重试。'); }
}
export function saveConflict(saved: ConflictSaved): void {
  const value = JSON.stringify(validate(saved, saved)); const name = key(saved); localStorage.setItem(name, value);
  if (localStorage.getItem(name) !== value) throw new Error('原冲突请求持久化回读失败；请保留记录。');
}
export async function lockConflict(owner: PhysicalOwner, action: () => Promise<void>): Promise<void> {
  if (!navigator.locks) throw new Error('浏览器无法保护跨页原请求，请使用支持此功能的浏览器。');
  await navigator.locks.request(key(owner), action);
}
