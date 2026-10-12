import { onBeforeUnmount, onMounted } from 'vue';
import type { ReconciliationOwner } from './pending';
import { QuarantineState } from './quarantine-state';
import { QuarantineRead } from './quarantine-read';
import { QuarantineCommand } from './quarantine-command';
export function useQuarantine(owner: ReconciliationOwner) {
  const state = new QuarantineState(owner); const read = new QuarantineRead(state); const command = new QuarantineCommand(state, read.load);
  onMounted(() => { void read.load(); }); onBeforeUnmount(() => { state.alive = false; });
  return { list: state.list, selected: state.selected, pending: state.pending, error: state.error, busy: state.busy, blocked: state.blocked,
    load: read.load, select: read.select, upload: read.upload, download: read.download, source: read.source, act: command.act, recover: command.recover };
}