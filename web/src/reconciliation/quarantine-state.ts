import { ref } from 'vue';
import { ApiError, errorText } from '../api';
import { useSessionStore } from '../session';
import type { ReconciliationOwner, ReconciliationPending } from './pending';
import type { QuarantineList, QuarantinePreparation } from './quarantine-contracts';
export class QuarantineState {
  readonly session = useSessionStore(); readonly list = ref<QuarantineList | null>(null); readonly selected = ref<QuarantinePreparation | null>(null);
  readonly pending = ref<ReconciliationPending | null>(null); readonly busy = ref(false); readonly blocked = ref(false); readonly error = ref(''); alive = true;
  constructor(readonly owner: ReconciliationOwner) { }
  assertCurrent = (): void => {
    if (!this.alive || this.session.identity?.id !== this.owner.actor || this.session.warehouseId !== this.owner.warehouse)
      throw new Error('人员或仓库已变化，原请求未继续。');
  };
  failed(cause: unknown): void { if (this.alive) this.error.value = cause instanceof ApiError ? errorText(cause) : '操作未确认，请保留原请求并核对。'; }
}
