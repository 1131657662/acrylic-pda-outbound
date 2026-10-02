import { z } from 'zod';
import { commandFactsSchema, commandReceiptSchema, type CommandKind } from './command-contracts';

const ownerSchema = z.object({ userId: z.uuid(), warehouseId: z.uuid(), submissionId: z.uuid() });
export type TemporaryCommandOwner = z.infer<typeof ownerSchema>;
const bodySchema = commandFactsSchema.strict().extend({
  commandVersion: z.literal(1), commandType: z.enum(['LINK', 'NORMAL_CONFIRM']), commandId: z.uuid(),
  transportRecoveryEpoch: z.string().regex(/^[0-9]+$/), reason: z.string().max(500),
  source: commandFactsSchema.shape.source.strict(),
  expected: commandFactsSchema.shape.expected.strict().extend({ candidateContextSha256: z.string().regex(/^[a-f0-9]{64}$/) }),
});
const savedSchema = ownerSchema.extend({
  version: z.literal(1), kind: z.enum(['LINK', 'NORMAL_CONFIRM']), json: z.string().max(32000),
  receipt: commandReceiptSchema.nullable(),
}).strict();
export type SavedTemporaryCommand = z.infer<typeof savedSchema>;

export class TemporaryPersistenceError extends Error {}

export function commandOwner(value: TemporaryCommandOwner): TemporaryCommandOwner {
  const parsed = ownerSchema.safeParse(value);
  if (!parsed.success) throw new TemporaryPersistenceError('登录身份或提交范围未加载，原请求尚未发送。');
  return parsed.data;
}

function storageKey(owner: TemporaryCommandOwner, kind: CommandKind): string {
  return `acrylic.temporary-command.v1:${owner.userId}:${owner.warehouseId}:${owner.submissionId}:${kind}`;
}

export function savedCommandBody(saved: SavedTemporaryCommand): z.infer<typeof bodySchema> {
  return bodySchema.parse(JSON.parse(saved.json));
}

function validated(saved: unknown, owner: TemporaryCommandOwner, kind: CommandKind): SavedTemporaryCommand {
  const value = savedSchema.parse(saved); const body = savedCommandBody(value);
  if (value.userId !== owner.userId || value.warehouseId !== owner.warehouseId || value.submissionId !== owner.submissionId ||
      value.kind !== kind || body.commandType !== kind || body.warehouseId !== owner.warehouseId || body.submissionId !== owner.submissionId)
    throw new Error('Original request scope mismatch');
  if (value.receipt && (value.receipt.commandId !== body.commandId || value.receipt.commandType !== kind))
    throw new Error('Original receipt mismatch');
  return value;
}

export function loadTemporaryCommand(owner: TemporaryCommandOwner, kind: CommandKind): SavedTemporaryCommand | null {
  try {
    const json = localStorage.getItem(storageKey(commandOwner(owner), kind));
    return json === null ? null : validated(JSON.parse(json), owner, kind);
  } catch {
    throw new TemporaryPersistenceError('无法读取原请求记录，请保留浏览器存储并恢复后重试；未发送新请求。');
  }
}

export function saveTemporaryCommand(saved: SavedTemporaryCommand): void {
  try {
    const value = validated(saved, commandOwner(saved), saved.kind); const key = storageKey(saved, saved.kind);
    const json = JSON.stringify(value);
    localStorage.setItem(key, json);
    if (localStorage.getItem(key) !== json) throw new Error('Persistence readback mismatch');
  } catch {
    throw new TemporaryPersistenceError('无法保存原请求或回执，请恢复浏览器存储后重试；原键仍须保留。');
  }
}

export function clearTemporaryCommand(owner: TemporaryCommandOwner, kind: CommandKind): void {
  try {
    const key = storageKey(commandOwner(owner), kind);
    localStorage.removeItem(key);
    if (localStorage.getItem(key) !== null) throw new Error('Persistence removal failed');
  } catch {
    throw new TemporaryPersistenceError('无法清理已完成记录，暂不能准备新的决定。');
  }
}

export async function lockTemporaryCommand(owner: TemporaryCommandOwner, kind: CommandKind, action: () => Promise<void>): Promise<void> {
  if (typeof navigator === 'undefined' || !navigator.locks)
    throw new TemporaryPersistenceError('浏览器无法保护跨页原请求，请使用支持此功能的浏览器；请求尚未发送。');
  await navigator.locks.request(storageKey(commandOwner(owner), kind), action);
}
