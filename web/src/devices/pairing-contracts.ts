import { z } from 'zod';

export const pairingApprovalSchema = z.object({
  applicationId: z.string().uuid(), warehouseId: z.string().uuid(), warehouseName: z.string(),
  deviceCode: z.string(), name: z.string(), keySha256: z.string().regex(/^[0-9a-f]{64}$/), serverRecoveryEpoch: z.string(),
});
export const pairingSchema = z.object({
  applicationId: z.string().uuid(), status: z.enum(['WAITING', 'PENDING', 'COMPLETED', 'EXPIRED', 'CANCELLED']),
  shortCode: z.string().regex(/^\d{8}$/), model: z.string(), createdAt: z.string(), expiresAt: z.string(),
  keySha256: z.string().regex(/^[0-9a-f]{64}$/), serverRecoveryEpoch: z.string(),
  approvalSha256: z.string().nullable(), approval: pairingApprovalSchema.nullable(), deviceId: z.string().uuid().nullable(),
});
export const pairingReceiptSchema = z.object({ applicationId: z.string().uuid(), approvalSha256: z.string(), approval: pairingApprovalSchema });
export const pendingPairingSchema = z.object({
  applicationId: z.string().uuid(), key: z.string().uuid(), epoch: z.string().min(1),
  body: z.object({ deviceCode: z.string().regex(/^[A-Za-z0-9_-]{1,40}$/, '设备编号须为1至40位字母、数字、下划线或短横线'),
    name: z.string().min(1, '请填写设备名称').max(100) }),
});
export const pairingContextSchema = z.object({ userId: z.string().uuid(), warehouseId: z.string().uuid(),
  application: pairingSchema, pending: pendingPairingSchema.nullable() });
export type Pairing = z.infer<typeof pairingSchema>;
export type PairingContext = z.infer<typeof pairingContextSchema>;

export function restorePairing(userId: string, warehouseId: string): PairingContext | null {
  const stored = sessionStorage.getItem(`acrylic.pairing.v1:${userId}:${warehouseId}`);
  if (!stored) return null;
  const parsed = pairingContextSchema.parse(JSON.parse(stored));
  if (parsed.userId !== userId || parsed.warehouseId !== warehouseId) throw new Error('登记请求归属不符');
  return parsed;
}
export function rememberPairing(value: PairingContext): void {
  sessionStorage.setItem(`acrylic.pairing.v1:${value.userId}:${value.warehouseId}`, JSON.stringify(pairingContextSchema.parse(value)));
}
