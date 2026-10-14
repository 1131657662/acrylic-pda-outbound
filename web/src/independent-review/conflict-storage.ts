import { z } from 'zod';
import { conflictEvidenceSchema, conflictResultSchema, type ConflictResult } from './conflict-contracts';
import type { PhysicalOwner } from './physical-contracts';
const ownerSchema = z.object({ userId: z.uuid(), warehouseId: z.uuid(), relayId: z.uuid() });
const commandSchema = z.object({ key: z.uuid(),
  epoch: z.string().regex(/^[1-9][0-9]*$/), body: conflictEvidenceSchema,
  receipt: conflictResultSchema.shape.receipt.nullable() }).strict();
const savedSchema = ownerSchema.extend({ version: z.literal(1), command: commandSchema.nullable(),
  history: z.array(commandSchema).max(199).optional(), predecessor: conflictResultSchema.shape.receipt.optional() }).strict();
export type ConflictSaved = z.infer<typeof savedSchema>;
function canonicalize(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(canonicalize);
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([name, entry]) => [name, canonicalize(entry)]));
  }
  return value;
}
export function sameConflictReceipt(left: unknown, right: unknown): boolean {
  return JSON.stringify(canonicalize(conflictResultSchema.shape.receipt.parse(left))) ===
    JSON.stringify(canonicalize(conflictResultSchema.shape.receipt.parse(right)));
}
function key(owner: PhysicalOwner): string {
  ownerSchema.parse(owner); return `acrylic.independent-conflict.v1:${owner.userId}:${owner.warehouseId}:${owner.relayId}`;
}
function validate(value: unknown, owner: PhysicalOwner): ConflictSaved {
  const saved = savedSchema.parse(value);
  if (saved.userId !== owner.userId || saved.warehouseId !== owner.warehouseId || saved.relayId !== owner.relayId)
    throw new Error('原冲突请求范围不一致');
  const commands = [...saved.history ?? [], ...saved.command ? [saved.command] : []];
  if (new Set(commands.map(command => command.key)).size !== commands.length || saved.history?.some(command => !command.receipt))
    throw new Error('原冲突历史含未明结果或重复原键');
  for (const command of commands) {
    const r = command.receipt;
    if (r && (r.supervisorId !== owner.userId || r.relayId !== owner.relayId || r.warehouseId !== owner.warehouseId))
      throw new Error('原冲突回执身份不一致');
  }
  const previous = saved.predecessor;
  if (previous && (previous.relayId !== owner.relayId || previous.warehouseId !== owner.warehouseId ||
      saved.command && saved.command.body.predecessorDecisionId !== previous.decisionId))
    throw new Error('追加请求与原前驱范围不一致');
  return saved;
}
export function prepareConflictExtension(owner: PhysicalOwner, previous: ConflictResult['receipt']): ConflictSaved {
  const saved = loadConflict(owner); const command = saved.command;
  if (command && (!command.receipt || !sameConflictReceipt(command.receipt, previous)))
    throw new Error('原冲突结果未明或与读取决定不一致，请按原键恢复。');
  const history = [...saved.history ?? [], ...command ? [command] : []];
  if (history.length > 199) throw new Error('本机责任链历史达到容量，请保留原记录专门核对。');
  const next = { ...saved, history, command: null, predecessor: previous }; saveConflict(next); return next;
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
