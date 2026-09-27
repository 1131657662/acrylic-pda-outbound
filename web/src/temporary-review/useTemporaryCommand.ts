import { ref } from 'vue';
import { ApiError, currentRecoveryEpoch, errorText, postRaw, request } from '../api';
import { commandFactsSchema, commandReceiptSchema, type CommandFacts, type CommandKind, type CommandReceipt } from './command-contracts';

function newCommandId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const random = () => Math.floor(Math.random() * 0x10000).toString(16).padStart(4, '0');
  return `${random()}${random()}-${random()}-4${random().slice(1)}-a${random().slice(1)}-${random()}${random()}${random()}`;
}

/**
 * Client half of the temporary command chain. The commandId is the original key: it is minted once per intent
 * and reused while the user retries the same intent, so a retry after a lost response replays the stored
 * receipt instead of recording a second decision. It is only discarded after a receipt is returned (the
 * context has moved, so the next decision needs fresh facts) or when the user explicitly starts over.
 */
export function useTemporaryCommand(scope: () => { submissionId: string; warehouseId: string }) {
  const facts = ref<CommandFacts | null>(null); const busy = ref(false); const error = ref('');
  const receipt = ref<CommandReceipt | null>(null); const closed = ref(false);
  const keys = new Map<CommandKind, string>();

  function keyOf(kind: CommandKind): string {
    const existing = keys.get(kind);
    if (existing) return existing;
    const minted = newCommandId(); keys.set(kind, minted); return minted;
  }
  function startOver(kind: CommandKind): void { keys.delete(kind); receipt.value = null; error.value = ''; }
  function originalKey(kind: CommandKind): string { return keyOf(kind); }

  async function fetchFacts(): Promise<CommandFacts> {
    const current = scope();
    const params = new URLSearchParams({ warehouseId: current.warehouseId, submissionId: current.submissionId });
    const value = await request(`/temporary-commands/facts?${params}`, commandFactsSchema);
    facts.value = value; closed.value = false; return value;
  }

  async function loadFacts(): Promise<void> {
    busy.value = true; error.value = '';
    try { await fetchFacts(); }
    catch (cause) { if (cause instanceof ApiError && cause.status === 404) closed.value = true; else error.value = errorText(cause); }
    finally { busy.value = false; }
  }

  async function execute(kind: CommandKind, options: { reason?: string; candidateContextSha256?: string | null } = {}): Promise<void> {
    busy.value = true; error.value = '';
    try {
      const current = facts.value ?? await fetchFacts();
      const body = {
        commandVersion: 1, commandType: kind, commandId: keyOf(kind), transportRecoveryEpoch: currentRecoveryEpoch(),
        reason: options.reason ?? '',
        warehouseId: current.warehouseId, submissionId: current.submissionId, workRound: current.workRound,
        authorizationId: current.authorizationId, freezeDeviceId: current.freezeDeviceId,
        sourceRecoveryEpoch: current.sourceRecoveryEpoch, source: { ...current.source },
        expected: { ...current.expected, candidateContextSha256: kind === 'LINK' ? options.candidateContextSha256 ?? null : null },
      };
      receipt.value = await postRaw('/temporary-commands', body, commandReceiptSchema);
      keys.delete(kind); facts.value = null;
    } catch (cause) {
      if (cause instanceof ApiError && (cause.status === 404 || cause.code === 'COMMAND_SURFACE_CLOSED')) closed.value = true;
      else error.value = errorText(cause);
    } finally { busy.value = false; }
  }

  return { facts, busy, error, receipt, closed, originalKey, loadFacts, execute, startOver };
}
