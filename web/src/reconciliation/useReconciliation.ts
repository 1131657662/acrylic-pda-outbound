import { onBeforeUnmount, onMounted, ref } from 'vue';
import { ApiError, currentRecoveryEpoch, errorText, getRaw, postRaw, request } from '../api';
import { useSessionStore } from '../session';
import { attachmentSchema, commandSchema, listSchema, preparationSchema, receiptSchema } from './contracts';
import type { ReconciliationEvidence, ReconciliationKind, ReconciliationList, ReconciliationPreparation } from './contracts';
import { canonical, loadPending, savePending, verifyResponse } from './pending';
import type { ReconciliationOwner, ReconciliationPending } from './pending';
import { downloadEvidence } from './download';

export function useReconciliation(owner: ReconciliationOwner) {
  const session = useSessionStore(); const list = ref<ReconciliationList | null>(null); const selected = ref<ReconciliationPreparation | null>(null);
  const pending = ref<ReconciliationPending | null>(null); const busy = ref(false); const blocked = ref(false); const error = ref(''); let alive = true;
  function assertCurrent(): void {
    if (!alive || session.identity?.id !== owner.actor || session.warehouseId !== owner.warehouse) throw new Error('人员或仓库已变化，原请求未继续。');
  }
  function failed(cause: unknown): void { if (alive) error.value = cause instanceof ApiError ? errorText(cause) : '操作未确认，请保留原请求并核对。'; }
  async function load(older: false | 'candidates' | 'cases' | 'returned' = false): Promise<void> {
    try {
      assertCurrent(); const saved = loadPending(owner); if (saved?.receipt) await verifyResponse(saved, saved.receipt); pending.value = saved;
      const review = session.identity?.permissions.includes('correction:approve');
      const cursor = older === 'candidates' && list.value?.nextCursor ? `&before=${list.value.nextCursor}`
        : older === 'cases' && list.value?.nextCaseCursor ? `&beforeCase=${list.value.nextCaseCursor}`
        : older === 'returned' && list.value?.nextReturnedCursor ? `&beforeReturned=${list.value.nextReturnedCursor}` : '';
      const data = await getRaw(`/corrections/reconciliation?warehouseId=${owner.warehouse}&review=${review}${cursor}`, listSchema);
      assertCurrent(); list.value = older && list.value ? { ...list.value,
        ...(older === 'candidates' ? { candidates: [...list.value.candidates, ...data.candidates], nextCursor: data.nextCursor }
          : older === 'returned' ? { returnedCandidates: [...list.value.returnedCandidates, ...data.returnedCandidates], nextReturnedCursor: data.nextReturnedCursor }
          : { cases: [...list.value.cases, ...data.cases], nextCaseCursor: data.nextCaseCursor }) } : data;
      error.value = ''; blocked.value = false;
    } catch (cause) { failed(cause); blocked.value = true; }
  }
  async function select(kind: ReconciliationKind, id: string): Promise<void> {
    if (busy.value) return; busy.value = true;
    selected.value = null;
    try {
      const target = kind.endsWith('_DISCOVER') ? `dispatchId=${id}` : `caseId=${id}`;
      const data = await getRaw(`/corrections/reconciliation/context?warehouseId=${owner.warehouse}&commandType=${kind}&${target}`, preparationSchema);
      assertCurrent();
      if (data.context.warehouseId !== owner.warehouse || data.expected.orderId !== data.context.orderId
          || data.expected.dispatchId !== data.context.dispatchId || data.expected.factsSha256 !== data.context.factsSha256
          || kind.endsWith('_DISCOVER') && data.context.dispatchId !== id
          || !kind.endsWith('_DISCOVER') && (data.context.case?.caseId !== id || data.expected.caseId !== id))
        throw new Error('差异上下文不匹配');
      selected.value = data; error.value = '';
    } catch (cause) { failed(cause); } finally { if (alive) busy.value = false; }
  }
  async function upload(file: File) {
    if (!file.size || file.size > 10 * 1024 * 1024) throw new Error('单文件必须为1字节至10MB');
    const bytes = await file.arrayBuffer(); const digest = await crypto.subtle.digest('SHA-256', bytes);
    const sha = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join(''); assertCurrent();
    return request(`/corrections/attachments/${crypto.randomUUID()}?warehouseId=${owner.warehouse}`, attachmentSchema,
      { method: 'PUT', body: bytes, headers: { 'Content-Type': 'application/octet-stream', 'X-Content-SHA256': sha } }, assertCurrent);
  }
  async function transmit(original: ReconciliationPending): Promise<void> {
    assertCurrent(); pending.value = original; const command = commandSchema.parse(JSON.parse(original.body));
    try {
      const receipt = await postRaw('/corrections', { json: original.body, recoveryEpoch: command.transportRecoveryEpoch, assertCurrent }, receiptSchema);
      assertCurrent(); await verifyResponse(original, receipt); savePending({ ...original, receipt }); pending.value = { ...original, receipt }; await load();
    } catch (cause) {
      if (cause instanceof ApiError && [400,403,409].includes(cause.status)
          && !['CORRECTION_RESULT_UNKNOWN', 'WRITE_CONFLICT'].includes(cause.code)) {
        assertCurrent();
        const rejected = { ...original, rejection: { status: cause.status, message: cause.message } }; savePending(rejected); pending.value = rejected;
      }
      throw cause;
    }
  }
  async function act(kind: ReconciliationKind, reason: string, evidence?: ReconciliationEvidence): Promise<void> {
    if (busy.value || blocked.value || !selected.value) return;
    busy.value = true; error.value = '';
    try {
      if (!navigator.locks) throw new Error('浏览器不能保护原请求');
      await navigator.locks.request(`reconciliation:${owner.actor}:${owner.warehouse}`, async () => {
        assertCurrent(); const old = loadPending(owner); if (old && !old.receipt && !old.rejection) throw new Error('先核对原请求');
        const context = selected.value; if (!context) throw new Error('差异上下文缺失');
        const command = commandSchema.parse({ commandVersion: 1, commandId: crypto.randomUUID(), commandType: kind, warehouseId: owner.warehouse,
          transportRecoveryEpoch: currentRecoveryEpoch(), reason, expected: context.expected,
          ...(kind.endsWith('_DISCOVER') ? { dispatchId: context.context.dispatchId } : { caseId: context.context.case?.caseId }),
          ...(kind.endsWith('_SUBMIT') ? { evidence } : {}) });
        const saved = { ...owner, body: canonical(command), receipt: null, rejection: null }; savePending(saved); await transmit(saved);
      });
    } catch (cause) { failed(cause); } finally { if (alive) busy.value = false; }
  }
  async function recover(): Promise<void> {
    if (busy.value) return; busy.value = true;
    try {
      if (!navigator.locks) throw new Error('浏览器不能保护原请求');
      await navigator.locks.request(`reconciliation:${owner.actor}:${owner.warehouse}`, async () => {
        assertCurrent(); const original = loadPending(owner); if (!original || original.receipt) return;
        const command = commandSchema.parse(JSON.parse(original.body));
        try {
          const receipt = await getRaw(`/corrections/commands/${command.commandId}?warehouseId=${owner.warehouse}&commandType=${command.commandType}`, receiptSchema);
          assertCurrent(); await verifyResponse(original, receipt); savePending({ ...original, receipt }); await load();
        } catch (cause) { if (!(cause instanceof ApiError) || cause.status !== 404) throw cause; await transmit(original); }
      });
    } catch (cause) { failed(cause); } finally { if (alive) busy.value = false; }
  }
  async function download(attachment: ReconciliationEvidence['attachments'][number]): Promise<void> {
    if (busy.value) return; busy.value = true;
    try { await downloadEvidence({ owner, attachment, review: true, assertCurrent }); }
    catch (cause) { failed(cause); } finally { if (alive) busy.value = false; }
  }
  onMounted(() => { void load(); }); onBeforeUnmount(() => { alive = false; });
  return { list, selected, pending, error, busy, blocked, load, select, upload, download, act, recover };
}
