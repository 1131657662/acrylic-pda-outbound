import { ApiError, request } from '../api';
import { grantReceiptSchema, revocationReceiptSchema } from './contracts';
import type { RescueMaterial } from './contracts';
import { saveCommand } from './pending';
import type { SavedRescueCommand } from './pending';

interface SendInput { pending: SavedRescueCommand; material: RescueMaterial; assertCurrent: () => void }
async function exchange(input: SendInput): Promise<SavedRescueCommand> {
  const { pending, material, assertCurrent } = input;
  if (pending.requestId !== material.request.requestId) throw new Error('原命令不属于所读申请；未发送。');
  if (pending.action === 'REVOKE' && pending.grantId !== material.authorization?.grantId) throw new Error('原撤销命令不属于所读原授权；未发送。');
  if (pending.action === 'APPROVE' && pending.rejectedExpiry) throw new Error('原期限已被服务端明确拒绝，请先保留拒绝原件并重新核准。');
  const options = { method: 'POST', body: pending.body, headers: { 'Content-Type': 'application/json',
    'Idempotency-Key': pending.key, 'X-Server-Recovery-Epoch': pending.epoch } };
  const query = `?warehouseId=${pending.warehouseId}`;
  if (pending.action === 'APPROVE') {
      const receipt = await request(`/rescue-requests/${pending.requestId}/approve${query}`, grantReceiptSchema, options, assertCurrent);
      assertCurrent();
      if (material.authorization && JSON.stringify(receipt) !== JSON.stringify(material.authorization)) throw new Error('原授权与回执不一致');
      return { ...pending, receipt };
  } else {
      const receipt = await request(`/rescue-grants/${pending.grantId}/revoke${query}`, revocationReceiptSchema, options, assertCurrent);
      assertCurrent();
      if (receipt.grantId !== pending.grantId) throw new Error('原撤销回执不属于原授权');
      return { ...pending, receipt };
  }
}
export async function sendCommand(input: SendInput): Promise<SavedRescueCommand> {
  const { pending, assertCurrent } = input;
  try {
    const completed = await exchange(input); saveCommand(completed); return completed;
  } catch (cause) {
    assertCurrent();
    if (pending.action === 'APPROVE' && !pending.receipt && cause instanceof ApiError && cause.status === 400 && cause.code === 'RESCUE_EXPIRY_INVALID')
      saveCommand({ ...pending, rejectedExpiry: true });
    throw cause;
  }
}
