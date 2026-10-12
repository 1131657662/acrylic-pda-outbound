import { z } from 'zod';
import { commandSchema, receiptSchema } from './header-contracts';
import type { HeaderCommand, HeaderReceipt } from './header-contracts';

export interface HeaderOwner { actor: string; warehouse: string; order: string }
const ownerSchema = z.object({ actor: z.uuid(), warehouse: z.uuid(), order: z.uuid() });
const pendingSchema = ownerSchema.extend({ body: z.string().max(20000), receipt: receiptSchema.nullable(),
  rejection: z.object({ status: z.number(), message: z.string() }).nullable().default(null) }).strict();
export type HeaderPending = z.infer<typeof pendingSchema>;
function key(owner: HeaderOwner): string { ownerSchema.parse(owner); return `acrylic.header-correction.v1:${owner.actor}:${owner.warehouse}:${owner.order}`; }

export function canonicalHeaderCommand(command: HeaderCommand): string {
  const body = commandSchema.parse(command);
  const sorted = (value: Record<string, unknown>) => Object.fromEntries(Object.entries(value).sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0));
  return JSON.stringify(sorted({ ...body, expected: sorted(body.expected), ...(body.headerPatch ? { headerPatch: sorted(body.headerPatch) } : {}) }));
}

function validate(saved: unknown, owner: HeaderOwner): HeaderPending {
  const pending = pendingSchema.parse(saved); const body = commandSchema.parse(JSON.parse(pending.body));
  if (pending.actor !== owner.actor || pending.warehouse !== owner.warehouse || pending.order !== owner.order
      || body.warehouseId !== owner.warehouse || body.orderId && body.orderId !== owner.order
      || canonicalHeaderCommand(body) !== pending.body)
    throw new Error('原抬头更正请求身份不匹配，请保留浏览器资料。');
  if (pending.receipt && (pending.receipt.commandId !== body.commandId || pending.receipt.orderId !== owner.order
      || pending.receipt.actor.principalId !== owner.actor || pending.receipt.actor.warehouseId !== owner.warehouse))
    throw new Error('原抬头更正回执身份不匹配，请保留浏览器资料。');
  return pending;
}

export function loadHeaderPending(owner: HeaderOwner): HeaderPending | null {
  const raw = localStorage.getItem(key(owner)); return raw === null ? null : validate(JSON.parse(raw), owner);
}
export function saveHeaderPending(pending: HeaderPending): void {
  const checked = validate(pending, pending); const raw = JSON.stringify(checked); const name = key(pending);
  const command = commandSchema.parse(JSON.parse(checked.body));
  const archive = `${name}:${command.commandId}`;
  localStorage.setItem(archive, raw);
  if (localStorage.getItem(archive) !== raw) throw new Error('原抬头更正请求归档失败，本次操作未继续。');
  localStorage.setItem(name, raw);
  if (localStorage.getItem(name) !== raw) throw new Error('原抬头更正请求未能完整保存，本次操作未继续。');
}

export async function verifyHeaderResponse(pending: HeaderPending, receipt: HeaderReceipt): Promise<void> {
  const command = commandSchema.parse(JSON.parse(pending.body));
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonicalHeaderCommand(command)));
  const hash = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
  if (receipt.commandId !== command.commandId || receipt.commandType !== command.commandType || receipt.commandSha256 !== hash
      || receipt.orderId !== pending.order || receipt.actor.principalId !== pending.actor || receipt.actor.warehouseId !== pending.warehouse
      || command.documentId && command.documentId !== receipt.documentId
      || JSON.stringify(Object.entries(receipt.expected).sort()) !== JSON.stringify(Object.entries(command.expected).sort()))
    throw new Error('返回回执与原抬头更正请求不匹配，请保留原请求重试。');
}
