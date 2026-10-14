import { z } from 'zod';
import { attachmentSchema, evidenceSchema, physicalResultSchema, type PhysicalOwner } from './physical-contracts';
const ownerSchema = z.object({ userId: z.uuid(), warehouseId: z.uuid(), relayId: z.uuid() });
const uploadSchema = z.object({ key: z.uuid(), epoch: z.string().regex(/^[1-9][0-9]*$/), name: z.string().max(255),
  sha: z.string().regex(/^[0-9a-f]{64}$/), size: z.number().int().min(1).max(10 * 1024 * 1024), reference: attachmentSchema.nullable() }).strict()
  .refine(row => !row.reference || (row.reference.evidenceSha256 === row.sha && Number(row.reference.sizeBytes) === row.size));
const savedSchema = ownerSchema.extend({ version: z.literal(1), uploads: z.array(uploadSchema).max(5),
  command: z.object({ key: z.uuid(), epoch: z.string().regex(/^[1-9][0-9]*$/), body: evidenceSchema, result: physicalResultSchema.shape.receipt.nullable() }).strict().nullable() }).strict();
export type PhysicalSaved = z.infer<typeof savedSchema>;
export type PhysicalUpload = PhysicalSaved['uploads'][number];
function key(owner: PhysicalOwner): string {
  ownerSchema.parse(owner); return `acrylic.independent-physical.v1:${owner.userId}:${owner.warehouseId}:${owner.relayId}`;
}
function validate(value: unknown, owner: PhysicalOwner): PhysicalSaved {
  const saved = savedSchema.parse(value);
  if (saved.userId !== owner.userId || saved.warehouseId !== owner.warehouseId || saved.relayId !== owner.relayId) throw new Error('原请求范围已变');
  if (new Set(saved.uploads.map(row => row.key)).size !== saved.uploads.length) throw new Error('附件原键重复');
  return saved;
}
export function loadPhysical(owner: PhysicalOwner): PhysicalSaved {
  try {
    const raw = localStorage.getItem(key(owner));
    return raw === null ? { ...owner, version: 1, uploads: [], command: null } : validate(JSON.parse(raw), owner);
  } catch { throw new Error('无法读取原实物请求，请保留浏览器存储并恢复后重试。'); }
}
export function savePhysical(saved: PhysicalSaved): void {
  const value = JSON.stringify(validate(saved, saved)); const name = key(saved); localStorage.setItem(name, value);
  if (localStorage.getItem(name) !== value) throw new Error('原请求持久化回读失败；未发送新的意图。');
}
export async function lockPhysical(owner: PhysicalOwner, action: () => Promise<void>): Promise<void> {
  if (!navigator.locks) throw new Error('浏览器无法保护跨页原请求，请使用支持此功能的浏览器。');
  await navigator.locks.request(key(owner), action);
}
