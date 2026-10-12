import { ApiError, currentRecoveryEpoch, getRaw, postRaw } from '../api';
import { commandSchema, receiptSchema } from './contracts';
import type { ReconciliationEvidence } from './contracts';
import type { QuarantineKind } from './quarantine-contracts';
import { canonical, loadPending, savePending, verifyResponse } from './pending';
import type { ReconciliationPending } from './pending';
import type { QuarantineState } from './quarantine-state';
export class QuarantineCommand {
  constructor(private readonly state: QuarantineState, private readonly reload: () => Promise<void>) { }
  private async transmit(original: ReconciliationPending): Promise<void> {
    const s = this.state; s.assertCurrent(); s.pending.value = original; const command = commandSchema.parse(JSON.parse(original.body));
    try {
      const receipt = await postRaw('/corrections', { json: original.body, recoveryEpoch: command.transportRecoveryEpoch, assertCurrent: s.assertCurrent }, receiptSchema);
      s.assertCurrent(); await verifyResponse(original, receipt); savePending({ ...original, receipt }); s.pending.value = { ...original, receipt }; await this.reload();
    } catch (cause) {
      if (cause instanceof ApiError && [400,403,409].includes(cause.status) && !['CORRECTION_RESULT_UNKNOWN', 'WRITE_CONFLICT'].includes(cause.code)) {
        s.assertCurrent(); const rejected = { ...original, rejection: { status: cause.status, message: cause.message } }; savePending(rejected); s.pending.value = rejected;
      }
      throw cause;
    }
  }
  act = async (kind: QuarantineKind, reason: string, evidence?: ReconciliationEvidence): Promise<void> => {
    const s = this.state; if (s.busy.value || s.blocked.value || !s.selected.value) return; s.busy.value = true; s.error.value = '';
    try {
      if (!navigator.locks) throw new Error('浏览器不能保护原请求');
      await navigator.locks.request(`reconciliation:${s.owner.actor}:${s.owner.warehouse}`, async () => {
        s.assertCurrent(); const old = loadPending(s.owner); if (old && !old.receipt && !old.rejection) throw new Error('先核对原请求');
        const context = s.selected.value; if (!context) throw new Error('隔离上下文缺失');
        const command = commandSchema.parse({ commandVersion: 1, commandId: crypto.randomUUID(), commandType: kind, warehouseId: s.owner.warehouse,
          transportRecoveryEpoch: currentRecoveryEpoch(), reason, expected: context.expected,
          ...(kind.endsWith('_DISCOVER') ? { baleId: context.context.baleId } : { caseId: context.context.case?.caseId }),
          ...(kind.endsWith('_SUBMIT') ? { evidence } : {}) });
        const saved = { ...s.owner, body: canonical(command), receipt: null, rejection: null }; savePending(saved); await this.transmit(saved);
      });
    } catch (cause) { s.failed(cause); } finally { if (s.alive) s.busy.value = false; }
  };
  recover = async (): Promise<void> => {
    const s = this.state; if (s.busy.value) return; s.busy.value = true;
    try {
      if (!navigator.locks) throw new Error('浏览器不能保护原请求');
      await navigator.locks.request(`reconciliation:${s.owner.actor}:${s.owner.warehouse}`, async () => {
        s.assertCurrent(); const original = loadPending(s.owner); if (!original || original.receipt) return;
        const command = commandSchema.parse(JSON.parse(original.body));
        try {
          const receipt = await getRaw(`/corrections/commands/${command.commandId}?warehouseId=${s.owner.warehouse}&commandType=${command.commandType}`, receiptSchema);
          s.assertCurrent(); await verifyResponse(original, receipt); savePending({ ...original, receipt }); await this.reload();
        } catch (cause) { if (!(cause instanceof ApiError) || cause.status !== 404) throw cause; await this.transmit(original); }
      });
    } catch (cause) { s.failed(cause); } finally { if (s.alive) s.busy.value = false; }
  };
}
