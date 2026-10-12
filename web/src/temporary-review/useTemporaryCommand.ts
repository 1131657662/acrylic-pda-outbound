import { ref, type Ref } from 'vue';
import { ApiError, currentRecoveryEpoch, errorText, postRaw, request } from '../api';
import { commandFactsSchema, commandReceiptSchema, type CommandFacts, type CommandKind, type CommandReceipt } from './command-contracts';
import { duplicatePageSchema } from './duplicate-contracts';
import { exclusionInputSchema, type ExclusionInput } from './exclusion-contracts';
import { dispositionFactsSchema, dispositionHash, dispositionManifestSchema, type DispositionSelection } from './disposition-contracts';
import { clearTemporaryCommand, commandOwner, loadTemporaryCommand, lockTemporaryCommand, savedCommandBody,
  saveTemporaryCommand, TemporaryPersistenceError, type SavedTemporaryCommand, type TemporaryCommandOwner } from './pending-command';

interface ExecuteOptions { reason?: string; candidateContextSha256?: string | null; exclusion?: ExclusionInput; disposition?: DispositionSelection }
interface TemporaryCommandHook {
  facts: Ref<CommandFacts | null>; busy: Ref<boolean>; error: Ref<string>; receipt: Ref<CommandReceipt | null>;
  closed: Ref<boolean>; original: Ref<SavedTemporaryCommand | null>;
  restore: (kind: CommandKind) => void; originalKey: (kind: CommandKind) => string | null;
  loadFacts: () => Promise<void>; execute: (kind: CommandKind, options?: ExecuteOptions) => Promise<void>;
  startOver: (kind: CommandKind) => Promise<void>;
}

/** One mounted command lifecycle; persistence and cross-page serialization live in pending-command. */
class TemporaryCommandLifecycle {
  readonly facts = ref<CommandFacts | null>(null);
  readonly busy = ref(false);
  readonly error = ref('');
  readonly receipt = ref<CommandReceipt | null>(null);
  readonly closed = ref(false);
  readonly original = ref<SavedTemporaryCommand | null>(null);
  private readonly scope: () => TemporaryCommandOwner;

  constructor(scope: () => TemporaryCommandOwner) { this.scope = scope; }

  private assertOwner(owner: TemporaryCommandOwner): void {
    const now = commandOwner(this.scope());
    if (now.userId !== owner.userId || now.warehouseId !== owner.warehouseId || now.submissionId !== owner.submissionId)
      throw new TemporaryPersistenceError('登录身份或提交范围已变化，请回到原身份核对原操作结果。');
  }
  private report(cause: unknown): void {
    if (cause instanceof ApiError && (cause.status === 404 || cause.code === 'COMMAND_SURFACE_CLOSED')) this.closed.value = true;
    this.error.value = cause instanceof TemporaryPersistenceError ? cause.message : errorText(cause);
  }
  restore(kind: CommandKind): void {
    this.error.value = ''; this.original.value = null; this.receipt.value = null;
    try {
      this.original.value = loadTemporaryCommand(commandOwner(this.scope()), kind);
      this.receipt.value = this.original.value?.receipt ?? null;
    } catch (cause) { this.report(cause); }
  }
  originalKey(kind: CommandKind): string | null {
    this.restore(kind);
    return this.original.value ? savedCommandBody(this.original.value).commandId : null;
  }
  private async fetchFacts(owner: TemporaryCommandOwner): Promise<CommandFacts> {
    const params = new URLSearchParams({ warehouseId: owner.warehouseId, submissionId: owner.submissionId });
    const value = await request(`/temporary-commands/facts?${params}`, commandFactsSchema);
    this.assertOwner(owner);
    if (value.warehouseId !== owner.warehouseId || value.submissionId !== owner.submissionId)
      throw new TemporaryPersistenceError('服务端事实与原提交范围不匹配，请重新核对。');
    this.facts.value = value; this.closed.value = false; return value;
  }
  async loadFacts(): Promise<void> {
    if (this.busy.value) return;
    this.busy.value = true; this.error.value = '';
    try { await this.fetchFacts(commandOwner(this.scope())); }
    catch (cause) { this.report(cause); }
    finally { this.busy.value = false; }
  }
  private async candidateContext(owner: TemporaryCommandOwner, provided?: string | null): Promise<string> {
    if (provided) return provided;
    const params = new URLSearchParams({ warehouseId: owner.warehouseId, limit: '1' });
    const page = await request(`/temporary-reviews/${owner.submissionId}/duplicate-candidates?${params}`, duplicatePageSchema);
    this.assertOwner(owner);
    if (!page.commandContextSha256) throw new Error('提交前必须先完成候选核对。');
    return page.commandContextSha256;
  }
  private async prepare(owner: TemporaryCommandOwner, kind: CommandKind, options: ExecuteOptions): Promise<SavedTemporaryCommand> {
    if (kind === 'CLOSE_DISPOSITION') return this.prepareDisposition(owner, options);
    const current = await this.fetchFacts(owner);
    const context = await this.candidateContext(owner, options.candidateContextSha256);
    const body = { commandVersion: 1, commandType: kind, commandId: crypto.randomUUID(),
      transportRecoveryEpoch: currentRecoveryEpoch(), reason: options.reason ?? '', ...current,
      expected: { ...current.expected, candidateContextSha256: context },
      ...(kind === 'DUPLICATE_EXCLUDE' ? { exclusion: exclusionInputSchema.parse(options.exclusion) } : {}) };
    return { ...owner, version: 1, kind, json: JSON.stringify(body), receipt: null };
  }
  private async prepareDisposition(owner: TemporaryCommandOwner, options: ExecuteOptions): Promise<SavedTemporaryCommand> {
    const selected = options.disposition;
    if (!selected || !options.reason?.trim()) throw new TemporaryPersistenceError('请核对全部实物并填写主管理由。');
    const params = new URLSearchParams({ warehouseId: owner.warehouseId, submissionId: owner.submissionId });
    const current = await request(`/temporary-commands/disposition-facts?${params}`, dispositionFactsSchema);
    this.assertOwner(owner);
    if (current.facts.warehouseId !== owner.warehouseId || current.facts.submissionId !== owner.submissionId
      || JSON.stringify(current.facts) !== JSON.stringify(selected.observed.facts)
      || JSON.stringify(current.items) !== JSON.stringify(selected.observed.items) || current.status !== selected.observed.status)
      throw new TemporaryPersistenceError('原停止证据或实物集合已变化，请重新核对全部实物。');
    const manifest = dispositionManifestSchema.parse(selected.manifest);
    if (JSON.stringify(manifest.items.map(item => item.baleId)) !== JSON.stringify(current.items.map(item => item.baleId)))
      throw new TemporaryPersistenceError('实物处置须逐包核对完整原集合。');
    const body = { commandVersion: 1, commandType: 'CLOSE_DISPOSITION', commandId: crypto.randomUUID(),
      transportRecoveryEpoch: currentRecoveryEpoch(), reason: options.reason.trim(), ...current.facts,
      expected: { ...current.facts.expected, dispositionManifestSha256: await dispositionHash(manifest), dispositionManifestVersion: '1' },
      dispositionManifest: manifest };
    this.assertOwner(owner);
    const json = JSON.stringify(body);
    if (new TextEncoder().encode(json).length > 262144) throw new TemporaryPersistenceError('完整处置原件超过命令限额，请保留原集合交主管核对。');
    return { ...owner, version: 1, kind: 'CLOSE_DISPOSITION', json, receipt: null };
  }
  private async send(owner: TemporaryCommandOwner, kind: CommandKind, options: ExecuteOptions): Promise<void> {
    this.assertOwner(owner);
    const saved = loadTemporaryCommand(owner, kind) ?? await this.prepare(owner, kind, options);
    this.assertOwner(owner); saveTemporaryCommand(saved); this.original.value = saved;
    if (saved.receipt) { this.receipt.value = saved.receipt; return; }
    const body = savedCommandBody(saved);
    const result = await postRaw('/temporary-commands', { json: saved.json, recoveryEpoch: body.transportRecoveryEpoch,
      assertCurrent: () => this.assertOwner(owner) }, commandReceiptSchema);
    this.assertOwner(owner);
    if (result.commandId !== body.commandId || result.commandType !== kind)
      throw new TemporaryPersistenceError('回执与原键或命令类型不匹配，原请求已保留，请核对后重试。');
    const completed = { ...saved, receipt: result };
    saveTemporaryCommand(completed); this.receipt.value = result; this.original.value = completed; this.facts.value = null;
  }
  async execute(kind: CommandKind, options: ExecuteOptions = {}): Promise<void> {
    if (this.busy.value) return;
    this.busy.value = true; this.error.value = ''; this.receipt.value = null;
    try {
      const owner = commandOwner(this.scope());
      await lockTemporaryCommand(owner, kind, () => this.send(owner, kind, options));
    } catch (cause) { this.report(cause); }
    finally { this.busy.value = false; }
  }
  private async reset(owner: TemporaryCommandOwner, kind: CommandKind): Promise<void> {
    this.assertOwner(owner);
    const saved = loadTemporaryCommand(owner, kind);
    if (saved && !saved.receipt) throw new TemporaryPersistenceError('原操作结果尚未核对，不能换键；请重试原请求。');
    clearTemporaryCommand(owner, kind);
    this.original.value = null; this.receipt.value = null; this.facts.value = null;
  }
  async startOver(kind: CommandKind): Promise<void> {
    if (this.busy.value) return;
    this.busy.value = true; this.error.value = '';
    try { const owner = commandOwner(this.scope()); await lockTemporaryCommand(owner, kind, () => this.reset(owner, kind)); }
    catch (cause) { this.report(cause); }
    finally { this.busy.value = false; }
  }
}

export function useTemporaryCommand(scope: () => TemporaryCommandOwner): TemporaryCommandHook {
  const state = new TemporaryCommandLifecycle(scope);
  return { facts: state.facts, busy: state.busy, error: state.error, receipt: state.receipt, closed: state.closed,
    original: state.original, restore: kind => state.restore(kind), originalKey: kind => state.originalKey(kind),
    loadFacts: () => state.loadFacts(), execute: (kind, options) => state.execute(kind, options),
    startOver: kind => state.startOver(kind) };
}
