import { request } from '../api';
import { attachmentSchema, digest, physicalResultSchema, verifyPhysicalResult, type PhysicalOwner } from './physical-contracts';
import { loadPhysical, savePhysical, type PhysicalUpload } from './physical-storage';
interface UploadInput { owner: PhysicalOwner; file: File; saved: PhysicalUpload; assertCurrent: () => void }
export function physicalPath(owner: PhysicalOwner, path: string): string {
  return `/original-relays/${owner.relayId}/physical/${path}?warehouseId=${owner.warehouseId}`;
}
export async function sendPhysicalAttachment(input: UploadInput): Promise<void> {
  const { owner, file, saved, assertCurrent } = input;
  if (file.size !== saved.size || await digest(await file.arrayBuffer()) !== saved.sha) throw new Error('请选择与原上传摘要相同的文件。');
  assertCurrent();
  const reference = await request(physicalPath(owner, 'attachments'), attachmentSchema, { method: 'POST', body: file,
    headers: { 'Content-Type': 'application/octet-stream', 'X-Evidence-SHA256': saved.sha,
      'X-Server-Recovery-Epoch': saved.epoch, 'Idempotency-Key': saved.key } }, assertCurrent);
  assertCurrent();
  if (reference.evidenceSha256 !== saved.sha || Number(reference.sizeBytes) !== saved.size) throw new Error('附件回执与原文件不一致。');
  const state = loadPhysical(owner); const row = state.uploads.find(value => value.key === saved.key);
  if (!row) throw new Error('附件原键丢失，请保留本地记录。'); row.reference = reference; savePhysical(state);
}
export async function sendPhysicalCheck(owner: PhysicalOwner, assertCurrent: () => void): Promise<void> {
  const state = loadPhysical(owner); const command = state.command;
  if (!command) throw new Error('尚无已保存的原实物请求。');
  const result = await request(physicalPath(owner, 'checks'), physicalResultSchema, { method: 'POST', body: JSON.stringify(command.body),
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key, 'X-Server-Recovery-Epoch': command.epoch } }, assertCurrent);
  await verifyPhysicalResult(result, owner, command.body); assertCurrent(); command.result = result.receipt; savePhysical(state);
}
