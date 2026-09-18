import { ref } from 'vue';
import { z } from 'zod';
import { ApiError, errorText, request } from '../api';
import { passwordSchema, receiptSchema, type PendingUserRequest, type UserReceipt } from './contracts';
import { clearUserRequest, saveUserRequest } from './pending';

export function useUserCommand(initial: PendingUserRequest | null, done: (receipt: UserReceipt) => void) {
  const original = ref(initial); const busy = ref(false); const error = ref(''); const retryAllowed = ref(false);
  const rejected = ref(false);
  function finish(saved: PendingUserRequest, result: UserReceipt): void {
    const version = saved.kind === 'CREATE' ? '1' : String(BigInt(saved.command.expectedVersion) + 1n);
    const status = saved.kind === 'CREATE' ? 'ACTIVE' : saved.kind === 'UPDATE' ? saved.command.status : null;
    if (result.action !== saved.kind || result.rowVersion !== version || (status && result.status !== status) ||
      (saved.kind !== 'CREATE' && result.userId !== saved.targetId)) throw new Error('回执与原请求不匹配');
    clearUserRequest(saved); original.value = null; done(result);
  }
  async function lookup(): Promise<void> {
    const saved = original.value; if (!saved || busy.value) return;
    busy.value = true; error.value = ''; retryAllowed.value = false;
    try { finish(saved, await request(`/users/requests/${saved.key}?warehouseId=${saved.warehouseId}`, receiptSchema)); }
    catch (cause) {
      retryAllowed.value = cause instanceof ApiError && cause.code === 'USER_REQUEST_NOT_FOUND';
      error.value = retryAllowed.value ? '尚未查到回执，可核对后补发原请求；含密码操作需重新输入密码。' : errorText(cause);
    } finally { busy.value = false; }
  }
  async function send(prepare: () => PendingUserRequest, password: string): Promise<void> {
    if (busy.value || rejected.value || (original.value && !retryAllowed.value)) return;
    error.value = ''; let saved: PendingUserRequest;
    try {
      saved = original.value ?? prepare(); if (saved.kind !== 'UPDATE') passwordSchema.parse(password);
      saveUserRequest(saved); original.value = saved;
    } catch (cause) {
      error.value = cause instanceof z.ZodError ? cause.issues[0]?.message ?? '请核对输入' : '无法保存原请求，请恢复本标签页存储后再提交。请求尚未发送。'; return;
    }
    busy.value = true; retryAllowed.value = false;
    const suffix = saved.kind === 'CREATE' ? '' : `/${saved.targetId}${saved.kind === 'PASSWORD' ? '/password' : ''}`;
    try {
      const body = saved.kind === 'UPDATE' ? saved.command : { ...saved.command, password };
      finish(saved, await request(`/users${suffix}?warehouseId=${saved.warehouseId}`, receiptSchema, { method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': saved.key, 'X-Server-Recovery-Epoch': saved.epoch }, body: JSON.stringify(body) }));
    } catch (cause) {
      error.value = errorText(cause);
      if (cause instanceof ApiError && ['INVALID_REQUEST', 'USER_VERSION_CONFLICT', 'USER_SELF_CHANGE_DENIED', 'USER_ROLE_DENIED', 'USER_WAREHOUSE_DENIED', 'USER_NOT_FOUND', 'WRITE_CONFLICT'].includes(cause.code)) {
        try { clearUserRequest(saved); original.value = null; rejected.value = true; } catch { /* Keep the original request when storage fails. */ }
      }
    } finally { busy.value = false; }
  }
  return { original, busy, error, retryAllowed, rejected, send, lookup };
}
