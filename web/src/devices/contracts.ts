import { z } from 'zod';

const statusSchema = z.enum(['ACTIVE', 'DISABLED']);
export const deviceSchema = z.object({
  id: z.string().uuid(), warehouseId: z.string().uuid(), deviceCode: z.string(), name: z.string(),
  publicKey: z.string(), keySha256: z.string().regex(/^[0-9a-f]{64}$/), status: statusSchema,
  rowVersion: z.string(), createdAt: z.string(), lastSeenAt: z.string().nullable(),
});
export const devicesSchema = z.object({ items: z.array(deviceSchema), nextCursor: z.string().uuid().nullable() });
export const receiptSchema = z.object({ id: z.string().uuid(), deviceId: z.string().uuid(), rowVersion: z.string(), status: statusSchema });
export const registerSchema = z.object({
  deviceCode: z.string().regex(/^[A-Za-z0-9_-]{1,40}$/, '设备编号只能包含字母、数字、下划线或短横线，最多40位'),
  name: z.string().min(1, '请填写设备名称').max(100),
  publicKey: z.string().min(1, '请粘贴PDA复制的登记公钥').max(200, '请使用PDA复制的公钥，不要粘贴私钥或证书文件'),
});
export const statusCommandSchema = z.object({
  expectedVersion: z.string().regex(/^[1-9][0-9]{0,17}$/), status: statusSchema,
  reason: z.string().min(1, '请填写启停原因').max(500),
});
const requestBase = { key: z.string().uuid(), epoch: z.string().min(1), warehouseId: z.string().uuid(), userId: z.string().uuid() };
export const pendingSchema = z.discriminatedUnion('kind', [
  z.object({ ...requestBase, kind: z.literal('REGISTER'), command: registerSchema }),
  z.object({ ...requestBase, kind: z.literal('STATUS'), device: deviceSchema, command: statusCommandSchema }),
]);
export type Device = z.infer<typeof deviceSchema>;
export type DeviceReceipt = z.infer<typeof receiptSchema>;
export type PendingDeviceRequest = z.infer<typeof pendingSchema>;
