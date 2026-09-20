import { z } from 'zod';

const version = z.string().regex(/^[1-9][0-9]*$/);
const status = z.enum(['ACTIVE', 'DISABLED']);
const reason = z.string().trim().min(1, '请填写操作原因').max(500);
const profile = z.object({ displayName: z.string().trim().min(1, '请填写人员姓名').max(100),
  roles: z.array(z.string().min(1)).min(1, '请选择角色').max(20), warehouseIds: z.array(z.uuid()).min(1, '请选择仓库').max(50), reason });
export const warehouseSchema = z.object({ id: z.uuid(), code: z.string(), name: z.string() });
export const optionsSchema = z.object({ roles: z.array(z.object({ code: z.string(), name: z.string(),
  permissions: z.array(z.string()), permissionNames: z.array(z.string()) })), warehouses: z.array(warehouseSchema) });
export const userSchema = z.object({ id: z.uuid(), username: z.string(), displayName: z.string(), status,
  rowVersion: version, roles: z.array(z.string()), warehouses: z.array(warehouseSchema) });
export const pageSchema = z.object({ items: z.array(userSchema), total: z.number().int().nonnegative(), page: z.number().int().positive(), pageSize: z.number().int().positive() });
export const receiptSchema = z.object({ id: z.uuid(), userId: z.uuid(), rowVersion: version, status,
  action: z.enum(['CREATE', 'UPDATE', 'PASSWORD']), createdAt: z.string() });
export const historySchema = z.object({ items: z.array(z.object({ id: z.uuid(), action: z.string(), actorName: z.string(), reason: z.string(), rowVersion: version, createdAt: z.string() })), nextCursor: z.string().nullable() });
const base = z.object({ userId: z.uuid(), warehouseId: z.uuid(), epoch: version, key: z.uuid() });
export const pendingSchema = z.discriminatedUnion('kind', [
  base.extend({ kind: z.literal('CREATE'), command: profile.extend({ username: z.string().regex(/^[A-Za-z0-9_-]{1,64}$/, '账号限字母、数字、下划线和短横线') }) }),
  base.extend({ kind: z.literal('UPDATE'), targetId: z.uuid(), username: z.string(), command: profile.extend({ status, expectedVersion: version }) }),
  base.extend({ kind: z.literal('PASSWORD'), targetId: z.uuid(), username: z.string(), command: z.object({ expectedVersion: version, reason }) }),
]);
export const passwordSchema = z.string().min(8, '密码至少8位').refine(value => new TextEncoder().encode(value).length <= 72, '密码最多72个UTF-8字节');
export type ManagedUser = z.infer<typeof userSchema>;
export type UserOptions = z.infer<typeof optionsSchema>;
export type PendingUserRequest = z.infer<typeof pendingSchema>;
export type UserReceipt = z.infer<typeof receiptSchema>;
export type UserMode = PendingUserRequest['kind'];
export function actionLabel(action: string): string {
  return ({ CREATE: '新增人员', UPDATE: '调整人员授权', PASSWORD: '重置密码' } as Record<string, string>)[action] ?? '人员操作';
}
