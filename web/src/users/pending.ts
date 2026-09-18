import { pendingSchema, type PendingUserRequest } from './contracts';

export interface UserRequestOwner { userId: string; warehouseId: string }
function key(owner: UserRequestOwner): string { return `acrylic.user-request.v1:${owner.userId}:${owner.warehouseId}`; }
export function loadUserRequest(owner: UserRequestOwner): PendingUserRequest | null {
  const value = sessionStorage.getItem(key(owner));
  if (value === null) return null;
  const saved = pendingSchema.parse(JSON.parse(value));
  if (saved.userId !== owner.userId || saved.warehouseId !== owner.warehouseId) throw new Error('恢复记录身份不匹配');
  return saved;
}
export function saveUserRequest(saved: PendingUserRequest): void {
  // Parsing strips unexpected fields, including any password accidentally supplied by a caller.
  sessionStorage.setItem(key(saved), JSON.stringify(pendingSchema.parse(saved)));
}
export function clearUserRequest(owner: UserRequestOwner): void { sessionStorage.removeItem(key(owner)); }
