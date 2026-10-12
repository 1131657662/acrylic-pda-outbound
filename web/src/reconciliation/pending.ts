import { z } from 'zod';
import { commandSchema, receiptSchema } from './contracts';
import type { ReconciliationCommand, ReconciliationReceipt } from './contracts';
export interface ReconciliationOwner { actor: string; warehouse: string }
const pendingSchema = z.object({ actor: z.uuid(), warehouse: z.uuid(), body: z.string().max(262144),
  receipt: receiptSchema.nullable(), rejection: z.object({ status: z.number(), message: z.string() }).nullable() }).strict();
export type ReconciliationPending = z.infer<typeof pendingSchema>;
function key(owner: ReconciliationOwner): string { return `acrylic.reconciliation.v1:${owner.actor}:${owner.warehouse}`; }
function sorted(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sorted);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0).map(([k,v]) => [k, sorted(v)]));
  return value;
}
export function canonical(command: ReconciliationCommand): string { return JSON.stringify(sorted(commandSchema.parse(command))); }
function validate(raw: unknown, owner: ReconciliationOwner): ReconciliationPending {
  const saved = pendingSchema.parse(raw); const body = commandSchema.parse(JSON.parse(saved.body));
  if (saved.actor !== owner.actor || saved.warehouse !== owner.warehouse || body.warehouseId !== owner.warehouse || canonical(body) !== saved.body)
    throw new Error('原差异请求身份或正文不一致，请保留资料。');
  if (saved.receipt && (saved.receipt.actor.principalId !== owner.actor || saved.receipt.actor.warehouseId !== owner.warehouse
      || saved.receipt.commandId !== body.commandId || body.commandType !== saved.receipt.commandType)) throw new Error('原差异回执身份不一致。');
  return saved;
}
export function loadPending(owner: ReconciliationOwner): ReconciliationPending | null {
  const raw = localStorage.getItem(key(owner)); return raw === null ? null : validate(JSON.parse(raw), owner);
}
export function savePending(pending: ReconciliationPending): void {
  const saved = validate(pending, pending); const body = commandSchema.parse(JSON.parse(saved.body)); const raw = JSON.stringify(saved);
  for (const name of [`${key(saved)}:${body.commandId}`, key(saved)]) {
    localStorage.setItem(name, raw); if (localStorage.getItem(name) !== raw) throw new Error('差异原请求未能完整保存。');
  }
}
export async function verifyResponse(original: ReconciliationPending, receipt: ReconciliationReceipt): Promise<void> {
  const command = commandSchema.parse(JSON.parse(original.body));
  const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(original.body));
  const digest = Array.from(new Uint8Array(bytes), byte => byte.toString(16).padStart(2, '0')).join('');
  if (receipt.commandId !== command.commandId || receipt.commandType !== command.commandType || receipt.commandSha256 !== digest
      || receipt.actor.principalId !== original.actor || receipt.actor.warehouseId !== original.warehouse
      || !command.commandType.startsWith('RECONCILE_QUARANTINE_') && receipt.orderId !== command.expected.orderId
      || command.commandType.startsWith('RECONCILE_QUARANTINE_') && (receipt.context?.baleId !== command.expected.baleId
        || receipt.context?.dispositionId !== command.expected.dispositionId || receipt.context?.orderId !== receipt.orderId)
      || 'caseId' in command && receipt.businessDecision.caseId !== command.caseId
      || JSON.stringify(sorted(receipt.expected)) !== JSON.stringify(sorted(command.expected))) throw new Error('返回结果与差异原请求不匹配。');
}
