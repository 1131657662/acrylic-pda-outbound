<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { currentRecoveryEpoch, errorText } from '../api';
import { useSessionStore } from '../session';
import type { IndependentReview } from './contracts';
import type { PhysicalOwner } from './physical-contracts';
import { loadPhysical } from './physical-storage';
import { associationEvidenceSchema, type AssociationResult } from './association-contracts';
import { loadAssociation, lockAssociation, saveAssociation, type AssociationSaved } from './association-storage';
import { allCandidates, checkAssociationPhysical, readAssociation, sendAssociation, type Candidate } from './association-api';
import { checkAssociationConflict } from './association-conflict-api';
const props = defineProps<{ data: IndependentReview }>(); const session = useSessionStore();
const busy = ref(false); const error = ref(''); const saved = ref<AssociationSaved | null>(null);
const result = ref<AssociationResult | null>(null); const candidates = ref<Candidate[] | null>(null);
const target = ref(''); const check = ref(''); const reason = ref(''); const exclusions = ref<Record<string, string>>({});
const conflict = ref('');
let generation = 0;
const owner = computed<PhysicalOwner>(() => ({ userId: session.identity?.id ?? '', warehouseId: session.warehouseId, relayId: props.data.original.relayId }));
const selfCheck = computed(() => owner.value.userId === props.data.original.operatorId);
const frozen = computed(() => busy.value || Boolean(saved.value?.command) || Boolean(result.value));
function assertScope(captured: PhysicalOwner, token: number): void {
  if (token !== generation || JSON.stringify(captured) !== JSON.stringify(owner.value)) throw new Error('身份、仓库或原件已变化，停止发送。');
}
async function refresh(captured: PhysicalOwner, token: number): Promise<void> {
  const state = loadAssociation(captured); const remote = await readAssociation(captured);
  assertScope(captured, token); saved.value = state; result.value = remote;
}
async function run(action: (captured: PhysicalOwner, assertCurrent: () => void) => Promise<void>): Promise<void> {
  if (busy.value) return; const captured = { ...owner.value }; const token = generation; busy.value = true; error.value = '';
  try {
    await lockAssociation(captured, async () => { assertScope(captured, token); await action(captured, () => assertScope(captured, token)); });
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
  if (loadAssociation(captured).command) throw new Error('原关联请求已保存，请按原键恢复。');
  const rows = await allCandidates(props.data, assertCurrent); assertCurrent();
  candidates.value = rows; exclusions.value = {}; target.value = '';
  check.value ||= loadPhysical(captured).command?.result?.receiptId ?? '';
}
async function record(captured: PhysicalOwner, assertCurrent: () => void): Promise<void> {
  const state = loadAssociation(captured);
  if (!state.command) {
    if (selfCheck.value || !candidates.value || !candidates.value.some(row => row.id === target.value)) throw new Error('须由独立主管载入完整候选并选择目标。');
    if (props.data.summary.crossTaskConflicts !== '0' && !conflict.value.trim()) throw new Error('须显式引用覆盖全部跨任务冲突的原决定。');
    const parsed = associationEvidenceSchema.safeParse({ contextSha256: props.data.contextSha256, physicalCheckId: check.value,
      ...(conflict.value.trim() ? { conflictDecisionId: conflict.value.trim() } : {}),
      targetOrderId: target.value, reason: reason.value, candidateExclusions: candidates.value.filter(row => row.id !== target.value)
        .map(row => ({ orderId: row.id, reason: exclusions.value[row.id] ?? '' })) });
    if (!parsed.success) throw new Error('请填写有效实物回执、关联说明和其余每个候选的排除理由。');
    await checkAssociationPhysical(captured, parsed.data); assertCurrent();
    await checkAssociationConflict(captured, parsed.data); assertCurrent();
    state.command = { key: crypto.randomUUID(), epoch: currentRecoveryEpoch(), body: parsed.data, receipt: null }; saveAssociation(state);
  }
  assertCurrent(); await sendAssociation(captured, assertCurrent);
}
watch(() => [owner.value.userId, owner.value.warehouseId, owner.value.relayId], () => {
  generation++; saved.value = null; result.value = null; candidates.value = null; exclusions.value = {};
  target.value = ''; check.value = ''; conflict.value = ''; reason.value = ''; busy.value = false; error.value = '';
  const captured = { ...owner.value }; const token = generation;
  void refresh(captured, token).catch(cause => { if (token === generation) error.value = cause instanceof Error ? cause.message : errorText(cause); });
}, { immediate: true });
onUnmounted(() => { generation++; });
</script>
<template>
  <section class="association-panel">
    <h2>主管人工关联</h2>
    <p>引用已登记实物依据，选择正式单并逐项说明其余候选。跨任务重码须引用完整原冲突决定；短装与多任务仍须专门处置。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="selfCheck">原采集者须由另一名有权主管核对。</p>
    <template v-if="result">
      <p role="status">人工关联已登记，仍待对账；未记账、未放行。回执 {{ result.receipt.associationId }}</p>
      <p>正式单 {{ result.receipt.targetOrderId }} · 实物回执 {{ result.receipt.physicalCheckId }}</p>
      <p>原关联说明：{{ result.evidence.reason }}</p>
      <p v-if="result.conflictEvidence">原冲突决定 {{ result.conflictEvidence.receipt.decisionId }} · 其余任务 {{ result.conflictEvidence.originalContext.peers.length }} 个；仍待对账。</p>
      <p>原关联主管 {{ result.receipt.supervisorId }} · {{ result.receipt.recordedAt }}</p>
    </template>
    <template v-else-if="saved?.command">
      <p>原关联结果未明。已保存原请求，刷新后仍按原键恢复。</p>
      <p>原目标 {{ saved.command.body.targetOrderId }} · {{ saved.command.body.reason }}</p>
      <el-button :disabled="busy || selfCheck" @click="run(record)">按原键恢复关联结果</el-button>
    </template>
    <template v-else>
      <el-button :disabled="frozen || selfCheck || !saved" @click="run(prepare)">载入全部候选并人工选择</el-button>
      <template v-if="candidates">
        <p>完整候选 {{ candidates.length }}单，选择一单，其余逐项排除。</p>
        <el-input v-model="check" aria-label="关联实物回执编号" placeholder="已登记实物检查UUID" maxlength="36" :disabled="frozen" />
        <el-input v-model="conflict" aria-label="关联冲突决定编号" placeholder="有跨任务冲突时，填写完整原冲突决定UUID" maxlength="36" :disabled="frozen" />
        <el-input v-model="reason" aria-label="人工关联说明" type="textarea" maxlength="500" :disabled="frozen" />
        <el-radio-group v-model="target" aria-label="人工关联目标" :disabled="frozen">
          <article v-for="row in candidates" :key="row.id" class="association-candidate">
            <el-radio :value="row.id">{{ row.orderNo }} · {{ row.vehicleNo }} · {{ row.plannedTotal }}包</el-radio>
            <p>{{ row.status }} · {{ row.source }} · {{ row.blockers.join('、') || '候选尚须核对产品与实物' }}</p>
            <p v-for="product in row.products" :key="product.deliveryCode">{{ product.deliveryCode }} · {{ product.model }} · {{ product.name }} · {{ product.plannedCount }}包</p>
            <el-input v-if="row.id !== target" v-model="exclusions[row.id]" :aria-label="`排除候选 ${row.orderNo}`" placeholder="逐项说明排除依据" maxlength="500" :disabled="frozen" />
          </article>
        </el-radio-group>
        <el-button type="primary" :disabled="frozen || !target || selfCheck" @click="run(record)">保存人工关联依据</el-button>
      </template>
    </template>
  </section>
</template>
<style scoped>
.association-panel { border-top: 1px solid var(--separator); margin-top: var(--space-6); padding-top: var(--space-4); }
.association-panel :deep(.el-input), .association-panel :deep(.el-textarea) { margin-bottom: var(--space-2); width: 100%; }
.association-panel :deep(.el-radio-group) { display: block; width: 100%; }
.association-candidate { margin-block: var(--space-4); padding: var(--space-4); border: 1px solid var(--separator); border-radius: var(--radius-control); }
.association-candidate :deep(.el-radio) { height: auto; white-space: normal; }
.association-candidate :deep(.el-radio__label) { white-space: normal; overflow-wrap: anywhere; }
</style>
