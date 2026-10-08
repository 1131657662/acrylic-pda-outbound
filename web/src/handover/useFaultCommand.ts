import { ref, onBeforeUnmount } from 'vue';
import { ApiError, currentRecoveryEpoch, errorText, postRaw } from '../api';
import { faultCommandSchema, faultReceiptSchema, type FaultCommand, type FaultControl, type FaultArchive } from './fault-contracts';
import { bind, clearRefusedFault, faultBody, faultDigest, loadFault, lockFault, saveFault,
  FaultPersistenceError, faultResultUnresolved, type FaultOwner, type SavedFault } from './pending-fault';
import type { CaseContext } from './contracts';
import type { AttachmentRef } from './attachment-contracts';
import { readUploads } from './attachment-storage';

interface FaultInput { physicalControl: FaultControl; archivedEvidence: FaultArchive; attachments?: AttachmentRef[] }

/** One mounted fault decision; exact request bytes remain bound to the original person, warehouse and case. */
export function useFaultCommand(scope: () => FaultOwner, detail: () => CaseContext) {
  const original = ref<SavedFault | null>(null); const busy = ref(false); const error = ref(''); const unreadable = ref(false);
  let mounted = true;
  onBeforeUnmount(() => { mounted = false; });
  function assertOwner(owner: FaultOwner): void {
    const now = scope();
    if (!mounted || now.userId !== owner.userId || now.warehouseId !== owner.warehouseId || now.caseId !== owner.caseId)
      throw new FaultPersistenceError('登录身份或交接范围已变化，请回到原身份核对原操作结果。');
  }
  function report(cause: unknown): void { error.value = cause instanceof FaultPersistenceError ? cause.message : errorText(cause); }
  function restore(): void {
    error.value = ''; original.value = null; unreadable.value = false;
    try { original.value = loadFault(scope()); } catch (cause) { unreadable.value = true; report(cause); }
  }
  function prepare(owner: FaultOwner, input: FaultInput): SavedFault {
    const uploads = readUploads(owner);
    if (uploads.some(row => !row.reference) || JSON.stringify(uploads.map(row => row.reference)) !== JSON.stringify(input.attachments ?? []))
      throw new FaultPersistenceError('原附件上传状态已变化，请重新打开表单核对全部原件。');
    const value = detail(); const context = value.context;
    if (!value.fault.entryAvailable || !value.fault.sourceDeviceId || context.caseId !== owner.caseId
        || context.warehouseId !== owner.warehouseId) throw new FaultPersistenceError('故障入口或交接资料已变化，请重新查询核对。');
    const command = faultCommandSchema.parse({ commandVersion: 1, commandType: 'FAULT_FENCE', commandId: crypto.randomUUID(),
      warehouseId: owner.warehouseId, caseId: owner.caseId, transportRecoveryEpoch: currentRecoveryEpoch(), expected: value.expected,
      sourceDeviceId: value.fault.sourceDeviceId, ...input });
    return { ...owner, version: 1, json: JSON.stringify(command), receipt: null, refusal: null };
  }
  async function receive(saved: SavedFault, body: FaultCommand): Promise<void> {
    const receipt = await postRaw('/handover-commands', { json: saved.json, recoveryEpoch: body.transportRecoveryEpoch,
      assertCurrent: () => assertOwner(saved) }, faultReceiptSchema);
    assertOwner(saved); bind(receipt, body, saved);
    if (receipt.commandSha256 !== await faultDigest(body)) throw new FaultPersistenceError('故障回执摘要不一致，原键已保留。');
    assertOwner(saved);
    const completed = { ...saved, receipt, refusal: null }; saveFault(completed); original.value = completed;
  }
  async function send(owner: FaultOwner, input: FaultInput): Promise<void> {
    assertOwner(owner);
    const saved = loadFault(owner) ?? prepare(owner, input); saveFault(saved); original.value = saved;
    if (saved.receipt) return;
    try { await receive(saved, faultBody(saved)); }
    catch (cause) {
      assertOwner(owner);
      if (cause instanceof ApiError && [400, 403, 404, 409].includes(cause.status)
          && !faultResultUnresolved(cause.code)) {
        const refused = { ...saved, refusal: { code: cause.code, message: cause.message } };
        saveFault(refused); original.value = refused;
      }
      throw cause;
    }
  }
  async function execute(input: FaultInput): Promise<void> {
    if (busy.value) return; busy.value = true; error.value = '';
    try { const owner = scope(); await lockFault(owner, () => send(owner, input)); }
    catch (cause) { if (mounted) report(cause); }
    finally { if (mounted) busy.value = false; }
  }
  async function recheck(): Promise<void> {
    if (busy.value) return; busy.value = true; error.value = '';
    try {
      const owner = scope();
      await lockFault(owner, async () => { assertOwner(owner); clearRefusedFault(owner); original.value = null; });
    } catch (cause) { report(cause); }
    finally { busy.value = false; }
  }
  return { original, busy, error, unreadable, restore, execute, recheck };
}
