import { ref, onBeforeUnmount } from 'vue';
import { currentRecoveryEpoch, errorText } from '../api';
import { attachmentHash, maxAttachmentBytes, maxAttachmentCommandBytes } from './attachment-contracts';
import { readUploads, saveUploads, type AttachmentUpload } from './attachment-storage';
import { uploadAttachment } from './attachment-api';
import { FaultPersistenceError, loadFault, lockFault, type FaultOwner } from './pending-fault';

/** File bytes stay in the picker; durable keys and hashes support safe reselection after reload. */
export function useFaultAttachments(scope: () => FaultOwner) {
  const rows = ref<AttachmentUpload[]>([]); const busy = ref(false); const error = ref('');
  const files = new Map<string, File>(); let mounted = true;
  onBeforeUnmount(() => { mounted = false; files.clear(); });
  function assertCurrent(owner: FaultOwner): void {
    const now = scope();
    if (!mounted || now.userId !== owner.userId || now.warehouseId !== owner.warehouseId || now.caseId !== owner.caseId)
      throw new FaultPersistenceError('登录身份或交接范围已变化，请回到原身份核对附件。');
  }
  function report(cause: unknown): void {
    if (mounted) error.value = cause instanceof TypeError ? errorText(cause) : cause instanceof Error ? cause.message : errorText(cause);
  }
  function restore(): void {
    error.value = ''; try { rows.value = readUploads(scope()); } catch (cause) { report(cause); }
  }
  async function add(owner: FaultOwner, selected: File[]): Promise<void> {
    const saved = readUploads(owner);
    if (loadFault(owner)) throw new FaultPersistenceError('原故障命令已保存，请先核对该原请求。');
    for (const file of selected) {
      const sha = await attachmentHash(file); assertCurrent(owner);
      let row = saved.find(item => item.sha === sha && item.size === file.size);
      if (!row) {
        if (saved.length === 5 || saved.reduce((sum, item) => sum + item.size, 0) + file.size > maxAttachmentCommandBytes)
          throw new Error('单条最多 5 个附件，单次总量最多 50MiB；原上传资料已保留。');
        row = { key: crypto.randomUUID(), name: file.name.slice(0, 255), size: file.size, sha,
          epoch: currentRecoveryEpoch(), reference: null }; saved.push(row);
        saveUploads(owner, saved); rows.value = saved;
      }
      files.set(row.key, file);
    }
    rows.value = saved;
  }
  async function run(owner: FaultOwner): Promise<void> {
    assertCurrent(owner);
    if (loadFault(owner)) throw new FaultPersistenceError('原故障命令已保存，附件保持原引用。');
    const saved = readUploads(owner);
    for (const row of saved) {
      if (row.reference) continue;
      const file = files.get(row.key);
      if (!file) throw new Error(`请重新选择原文件“${row.name}”；将继续使用已保存的原上传键。`);
      row.reference = await uploadAttachment({ owner, saved: row, file, assertCurrent: () => assertCurrent(owner) });
      assertCurrent(owner); saveUploads(owner, saved); rows.value = [...saved];
    }
  }
  async function select(selected: File[]): Promise<void> {
    if (busy.value) return; busy.value = true; error.value = '';
    try {
      if (selected.some(file => file.size < 1 || file.size > maxAttachmentBytes)) throw new Error('单个附件须为 1 字节至 10MiB。');
      const owner = scope(); await lockFault(owner, async () => { assertCurrent(owner); await add(owner, selected); await run(owner); });
    } catch (cause) { report(cause); }
    finally { if (mounted) busy.value = false; }
  }
  return { rows, busy, error, restore, select };
}
