<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { currentRecoveryEpoch, errorText } from '../api';
import { useSessionStore } from '../session';
import type { IndependentReview } from './contracts';
import type { PhysicalOwner } from './physical-contracts';
import { loadPhysical } from './physical-storage';
import { conflictEvidenceSchema, type ConflictResult } from './conflict-contracts';
import { loadConflict, lockConflict, saveConflict, type ConflictSaved } from './conflict-storage';
import { allConflictPeers, checkConflictPhysical, readConflict, sendConflict, type Peer } from './conflict-api';
const props = defineProps<{ data: IndependentReview }>(); const session = useSessionStore();
const busy = ref(false); const error = ref(''); const saved = ref<ConflictSaved | null>(null);
const result = ref<ConflictResult | null>(null); const peers = ref<Peer[] | null>(null);
const check = ref(''); const reason = ref(''); const decisionId = ref('');
const references = ref<Record<string, { relayId: string; physicalCheckId: string }>>({});
let generation = 0;
const owner = computed<PhysicalOwner>(() => ({ userId: session.identity?.id ?? '', warehouseId: session.warehouseId, relayId: props.data.original.relayId }));
const selfCheck = computed(() => owner.value.userId === props.data.original.operatorId);
const frozen = computed(() => busy.value || Boolean(saved.value?.command) || Boolean(result.value));
function assertScope(captured: PhysicalOwner, token: number): void {
  if (token !== generation || JSON.stringify(captured) !== JSON.stringify(owner.value)) throw new Error('身份、仓库或原件已变化，停止发送。');
}
async function refresh(captured: PhysicalOwner, token: number): Promise<void> {
  const state = loadConflict(captured); const remote = await readConflict(captured);
  assertScope(captured, token); saved.value = state; if (remote) result.value = remote;
}
async function run(action: (captured: PhysicalOwner, assertCurrent: () => void) => Promise<void>): Promise<void> {
  if (busy.value) return; const captured = { ...owner.value }; const token = generation; busy.value = true; error.value = '';
  try {
    await lockConflict(captured, async () => { assertScope(captured, token); await action(captured, () => assertScope(captured, token)); });
    await refresh(captured, token);
  } catch (cause) {
    if (token === generation) {
      error.value = cause instanceof Error ? cause.message : errorText(cause);
      try { await refresh(captured, token); } catch (failure) {
        if (token === generation) { saved.value = null; result.value = null; error.value += ` ${failure instanceof Error ? failure.message : errorText(failure)}`; }
      }
    }
  } finally { if (token === generation) busy.value = false; }
}
async function prepare(captured: PhysicalOwner, assertCurrent: () => void): Promise<void> {
  if (loadConflict(captured).command) throw new Error('原冲突请求已保存，请按原键恢复。');
  const rows = await allConflictPeers(props.data, assertCurrent); assertCurrent();
  references.value = Object.fromEntries(rows.map(row => [`${row.deviceId}/${row.taskId}`, { relayId: '', physicalCheckId: '' }])); peers.value = rows;
  check.value ||= loadPhysical(captured).command?.result?.receiptId ?? '';
}
async function record(captured: PhysicalOwner, assertCurrent: () => void): Promise<void> {
  const state = loadConflict(captured);
  if (!state.command) {
    if (selfCheck.value || !peers.value) throw new Error('须由独立主管载入全部冲突任务。');
    const parsed = conflictEvidenceSchema.safeParse({ contextSha256: props.data.contextSha256, physicalCheckId: check.value,
      peerChecks: peers.value.map(row => references.value[`${row.deviceId}/${row.taskId}`]), reason: reason.value });
    if (!parsed.success) throw new Error('须选择每个冲突任务的原件并填写全部实物回执与决定说明。');
    await checkConflictPhysical({ owner: captured, body: parsed.data, peers: peers.value, assertCurrent }); assertCurrent();
    state.command = { key: crypto.randomUUID(), epoch: currentRecoveryEpoch(), body: parsed.data, receipt: null }; saveConflict(state);
  }
  assertCurrent(); await sendConflict(captured, assertCurrent);
}
async function readById(captured: PhysicalOwner, assertCurrent: () => void): Promise<void> {
  if (loadConflict(captured).command) throw new Error('已保存原冲突请求，请按原键恢复。');
  const remote = await readConflict(captured, decisionId.value.trim()); assertCurrent(); result.value = remote;
}
watch(() => [owner.value.userId, owner.value.warehouseId, owner.value.relayId], () => {
  generation++; saved.value = null; result.value = null; peers.value = null; references.value = {};
  check.value = ''; reason.value = ''; decisionId.value = ''; busy.value = false; error.value = '';
  const captured = { ...owner.value }; const token = generation;
  void refresh(captured, token).catch(cause => { if (token === generation) error.value = cause instanceof Error ? cause.message : errorText(cause); });
}, { immediate: true });
onUnmounted(() => { generation++; });
</script>
<template>
  <section class="conflict-panel">
    <h2>跨任务实物冲突决定</h2>
    <p>核对全部重叠任务，选定本任务已装载的重码，其余各方须已卸下并说明。原SCAN/VOID与结束事实保留，决定仍待对账、未记账或放行。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="selfCheck">原采集者须由另一名有权主管核对，各方采集者均不得代审。</p>
    <template v-if="result">
      <p role="status">冲突决定已登记，仍待对账；未关联、未记账、未放行。决定 {{ result.receipt.decisionId }}</p>
      <p>选定任务 {{ result.receipt.taskId }} · 其他任务 {{ result.originalContext.peers.length }} 个 · 重码 {{ result.originalContext.claims.length }} 包</p>
      <p>原决定说明：{{ result.evidence.reason }}</p><p>原主管 {{ result.receipt.supervisorId }} · {{ result.receipt.recordedAt }}</p>
    </template>
    <template v-else-if="saved?.command">
      <p>原冲突结果未明。已保存原请求，刷新后仍按原键恢复。</p><p>原决定说明：{{ saved.command.body.reason }}</p>
      <el-button :disabled="busy || selfCheck" @click="run(record)">按原键恢复冲突结果</el-button>
    </template>
    <template v-else>
      <el-input v-model="decisionId" aria-label="读取原冲突决定编号" placeholder="已有决定时，填写明确决定UUID" maxlength="36" :disabled="frozen" />
      <el-button :disabled="frozen || !decisionId.trim() || !saved" @click="run(readById)">按编号读取原冲突决定</el-button>
      <el-button :disabled="frozen || selfCheck || !saved" @click="run(prepare)">载入全部冲突任务并核对</el-button>
      <template v-if="peers">
        <p>全部重叠任务 {{ peers.length }} 个，逐项选择原件并引用实物回执。</p>
        <el-input v-model="check" aria-label="冲突选定方实物回执编号" placeholder="本任务已装载实物检查UUID" maxlength="36" :disabled="frozen" />
        <el-input v-model="reason" aria-label="冲突决定说明" type="textarea" maxlength="500" :disabled="frozen" />
        <article v-for="peer in peers" :key="`${peer.deviceId}/${peer.taskId}`" class="conflict-peer">
          <p>其他任务 {{ peer.taskId }} · 原设备 {{ peer.deviceId }}</p><p>重码 {{ peer.overlappingBarcodes.join('、') }}</p>
          <template v-if="references[`${peer.deviceId}/${peer.taskId}`]">
            <el-select v-model="references[`${peer.deviceId}/${peer.taskId}`]!.relayId" :aria-label="`冲突原件 ${peer.taskId}`" placeholder="显式选择该任务原件" :disabled="frozen">
              <el-option v-for="original in peer.originals" :key="original.relayId" :value="original.relayId" :label="original.relayId" />
            </el-select>
            <el-input v-model="references[`${peer.deviceId}/${peer.taskId}`]!.physicalCheckId" :aria-label="`冲突实物回执 ${peer.taskId}`" placeholder="该任务重码已卸下实物检查UUID" maxlength="36" :disabled="frozen" />
          </template>
        </article>
        <el-button type="primary" :disabled="frozen || selfCheck" @click="run(record)">保存完整冲突决定</el-button>
      </template>
    </template>
  </section>
</template>
<style scoped>
.conflict-panel { border-top: 1px solid var(--separator); margin-top: var(--space-6); padding-top: var(--space-4); }
.conflict-panel :deep(.el-input), .conflict-panel :deep(.el-textarea), .conflict-panel :deep(.el-select) { margin-bottom: var(--space-2); width: 100%; }
.conflict-panel :deep(.el-button) { height: auto; min-height: var(--control-height); white-space: normal; }
.conflict-peer { margin-block: var(--space-4); padding: var(--space-4); border: 1px solid var(--separator); border-radius: var(--radius-control); }
</style>
