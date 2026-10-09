import { z } from 'zod';
import { faultCommandSchema, faultReceiptSchema, type FaultCommand, type FaultReceipt } from './fault-contracts';

const ownerSchema = z.object({ userId: z.uuid(), warehouseId: z.uuid(), caseId: z.uuid() }).strict();
const savedSchema = ownerSchema.extend({ version: z.literal(1), json: z.string().max(20000),
  receipt: faultReceiptSchema.nullable(), refusal: z.object({ code: z.string(), message: z.string() }).nullable() }).strict();
export type FaultOwner = z.infer<typeof ownerSchema>;
export type SavedFault = z.infer<typeof savedSchema>;
export class FaultPersistenceError extends Error {}
export function faultResultUnresolved(code: string): boolean {
  return ['IDEMPOTENCY_CONTENT_MISMATCH', 'HANDOVER_RESULT_UNKNOWN', 'HANDOVER_RECEIPT_INTEGRITY_FAILURE', 'HANDOVER_RECEIPT_SHAPE'].includes(code);
}

function key(owner: FaultOwner): string {
  const value = ownerSchema.parse({ userId: owner.userId, warehouseId: owner.warehouseId, caseId: owner.caseId });
  return `acrylic.handover-fault.v1:${value.userId}:${value.warehouseId}:${value.caseId}`;
}
export function faultBody(saved: SavedFault): FaultCommand { return faultCommandSchema.parse(JSON.parse(saved.json)); }
function validate(saved: unknown, owner: FaultOwner): SavedFault {
  const value = savedSchema.parse(saved); const body = faultBody(value);
  if (value.userId !== owner.userId || value.warehouseId !== owner.warehouseId || value.caseId !== owner.caseId
      || body.warehouseId !== owner.warehouseId || body.caseId !== owner.caseId) throw new Error('Fault request owner changed');
  if (value.receipt) bind(value.receipt, body, owner);
  return value.refusal && faultResultUnresolved(value.refusal.code) ? { ...value, refusal: null } : value;
}
export function loadFault(owner: FaultOwner): SavedFault | null {
  try { const raw = localStorage.getItem(key(owner)); return raw === null ? null : validate(JSON.parse(raw), owner); }
  catch { throw new FaultPersistenceError('无法读取原故障请求，请保留浏览器存储并恢复后重试；未发送新请求。'); }
}
export function saveFault(value: SavedFault): void {
  try {
    validate(value, value); const name = key(value); const json = JSON.stringify(value);
    localStorage.setItem(name, json);
    if (localStorage.getItem(name) !== json) throw new Error('Fault storage readback failed');
  } catch { throw new FaultPersistenceError('无法保存原故障请求或回执，请恢复浏览器存储后重试；原键仍须保留。'); }
}
export function clearRefusedFault(owner: FaultOwner): void {
  const saved = loadFault(owner);
  if (!saved?.refusal || saved.receipt || faultResultUnresolved(saved.refusal.code)) throw new FaultPersistenceError('原操作结果未核明，不能换键。');
  try {
    const name = key(owner); localStorage.removeItem(name);
    if (localStorage.getItem(name) !== null) throw new Error('Fault storage removal failed');
  } catch { throw new FaultPersistenceError('无法整理已拒绝的原请求，请恢复浏览器存储。'); }
}
export async function lockFault(owner: FaultOwner, action: () => Promise<void>): Promise<void> {
  if (!navigator.locks) throw new FaultPersistenceError('浏览器无法保护跨页原请求，请使用支持此功能的浏览器；请求尚未发送。');
  await navigator.locks.request(key(owner), action);
}
function canonical(value: unknown): string {
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
  if (value !== null && typeof value === 'object') {
    const entries: [string, unknown][] = Object.entries(value);
    return '{' + entries.sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)
      .map(([name, item]) => JSON.stringify(name) + ':' + canonical(item)).join(',') + '}';
  }
  const encoded = JSON.stringify(value);
  if (encoded === undefined) throw new Error('Invalid fault JSON');
  return encoded;
}
export async function faultDigest(command: FaultCommand): Promise<string> {
  const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonical(command)));
  return Array.from(new Uint8Array(bytes), byte => byte.toString(16).padStart(2, '0')).join('');
}
export function bind(receipt: FaultReceipt, command: FaultCommand, owner: FaultOwner): void {
  const c = receipt.caseContext;
  if (receipt.commandId !== command.commandId || receipt.actor.principalId !== owner.userId
      || c.caseId !== command.caseId || c.warehouseId !== command.warehouseId
      || receipt.actor.warehouseId !== command.warehouseId || receipt.businessDecision.caseId !== command.caseId
      || receipt.businessDecision.sourceDeviceId !== command.sourceDeviceId
      || canonical(receipt.expected) !== canonical(command.expected)
      || canonical(receipt.businessDecision.attachments ?? null) !== canonical(command.attachments ?? null)
      || canonical(receipt.businessDecision.physicalControl) !== canonical(command.physicalControl))
    throw new FaultPersistenceError('故障回执与原请求不一致，原键已保留，请核对后重试。');
  const archive = receipt.businessDecision.archivedEvidence;
  const fields = new Map(Object.entries(archive));
  if (c.sourceKind !== ('sourceKind' in command.archivedEvidence ? 'FORMAL' : 'TEMP'))
    throw new FaultPersistenceError('故障归档来源与原请求不一致，原键已保留。');
  for (const [name, value] of Object.entries(command.archivedEvidence))
    if (fields.get(name) !== value)
      throw new FaultPersistenceError('故障归档凭据与原请求不一致，原键已保留。');
}
