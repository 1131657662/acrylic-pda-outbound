import { pendingSchema } from './contracts';
import type { PendingDeviceRequest } from './contracts';

export interface DeviceRequestOwner { userId: string; warehouseId: string }
function storageKey(owner: DeviceRequestOwner): string {
  return `acrylic.device-request.v1:${owner.userId}:${owner.warehouseId}`;
}
export function loadDeviceRequest(owner: DeviceRequestOwner): PendingDeviceRequest | null {
  const value = sessionStorage.getItem(storageKey(owner));
  if (value === null) return null;
  const saved = pendingSchema.parse(JSON.parse(value));
  if (saved.userId !== owner.userId || saved.warehouseId !== owner.warehouseId)
    throw new Error('本标签页设备请求的人员或仓库不匹配');
  return saved;
}
export function saveDeviceRequest(request: PendingDeviceRequest): void {
  sessionStorage.setItem(storageKey(request), JSON.stringify(pendingSchema.parse(request)));
}
export function clearDeviceRequest(owner: DeviceRequestOwner): void {
  sessionStorage.removeItem(storageKey(owner));
}
