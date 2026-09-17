<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { request, ApiError, errorText, currentRecoveryEpoch } from '../api';
import { useSessionStore } from '../session';
import { physicalEvidenceResponse, dispositionLabels, pendingDispositionSchema } from './disposition-contracts';
import type { PhysicalEvidence, DispositionScan } from './disposition-contracts';
import type { Recall } from './recall-contracts';
import type { z } from 'zod';
const props = defineProps<{ warehouseId: string; recall: Recall; scans: DispositionScan[]; disabled: boolean }>();
const emit = defineEmits<{ evidence: [value: PhysicalEvidence[]]; ready: [value: boolean]; busy: [value: boolean] }>();
const session = useSessionStore(); const evidence = ref<PhysicalEvidence[]>([]); const error = ref(''); const busy = ref(false);
const dialog = ref(false); const checked = ref(false); const uncertain = ref(false);
const loaded = ref(false);
const actions = ref<Record<string, 'RETAIN' | 'RELEASE' | 'QUARANTINE'>>({}); const notes = ref<Record<string, string>>({});
let pending: z.infer<typeof pendingDispositionSchema> | null = null;
const allowed = computed(() => session.identity?.permissions.includes('order:dispose'));
const selected = computed(() => props.scans.filter(s => !approved(s.captureId) && actions.value[s.captureId] && actions.value[s.captureId] !== 'RETAIN'));
const storageKey = computed(() => `acrylic.disposition.v1:${session.identity?.id}:${props.warehouseId}:${props.recall.recovery?.recoveryPackageId}`);
function approved(id: string): PhysicalEvidence | undefined { return evidence.value.find(e => e.captureId === id); }
async function load(): Promise<void> {
  loaded.value = false; emit('ready', false); error.value = ''; busy.value = true;
  try {
    const id = props.recall.recovery?.recoveryPackageId; if (!id) return;
    const data = await request(`/recovery-packages/${id}/disposition-evidence?warehouseId=${props.warehouseId}`, physicalEvidenceResponse);
    evidence.value = data.evidence; emit('evidence', data.evidence);
    const saved = sessionStorage.getItem(storageKey.value);
    if (saved) { pending = pendingDispositionSchema.parse(JSON.parse(saved)); uncertain.value = true; dialog.value = true; checked.value = true; emit('busy', true); }
    loaded.value = true;
  } catch (cause) { error.value = errorText(cause); }
  finally { busy.value = false; }
}
function open(): void { dialog.value = true; checked.value = false; emit('busy', true); }
function close(): void { if (!busy.value && !uncertain.value) { dialog.value = false; emit('busy', false); } }
function prepare(): void {
  if (pending) return;
  const r = props.recall; if (!r.recovery) throw new Error('缺少完整恢复包');
  const body = JSON.stringify({ expectedVersion: r.rowVersion, payloadSha256: r.recovery.payloadSha256,
    dispositions: selected.value.map(s => ({ captureId: s.captureId, expectedDispatchCycle: s.expectedDispatchCycle,
      action: actions.value[s.captureId], physicalEvidence: notes.value[s.captureId]?.trim() })) });
  pending = { path: `/recovery-packages/${r.recovery.recoveryPackageId}/disposition-evidence?warehouseId=${props.warehouseId}`,
    body, key: crypto.randomUUID(), epoch: currentRecoveryEpoch() };
  sessionStorage.setItem(storageKey.value, JSON.stringify(pending));
}
async function confirm(): Promise<void> {
  if (busy.value || !checked.value) return; busy.value = true; error.value = '';
  try {
    prepare(); if (!pending) return;
    await request(pending.path, physicalEvidenceResponse, { method: 'POST', body: pending.body, headers: {
      'Content-Type': 'application/json', 'Idempotency-Key': pending.key, 'X-Server-Recovery-Epoch': pending.epoch } });
    sessionStorage.removeItem(storageKey.value); pending = null; uncertain.value = false; dialog.value = false;
    emit('busy', false); await load();
  } catch (cause) {
    uncertain.value = !(cause instanceof ApiError) || cause.status >= 500;
    if (!uncertain.value) { sessionStorage.removeItem(storageKey.value); pending = null; }
    error.value = errorText(cause);
  } finally { busy.value = false; }
}
watch(() => props.recall.recovery?.recoveryPackageId, () => { void load(); }, { immediate: true });
watch(() => [loaded.value, busy.value, uncertain.value, selected.value.length], () => {
  emit('ready', loaded.value && !busy.value && !uncertain.value && selected.value.length === 0);
});
</script>
<template>
  <div v-if="!recall.continuation">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-button v-if="error && !dialog" :loading="busy" @click="load">重试加载实物凭据</el-button>
    <p>逐包实物处置：未选择减少的包继续保留；卸货或隔离须先由主管核验批准。</p>
    <el-table :data="scans" max-height="320">
      <el-table-column prop="barcode" label="原包条码" min-width="150" />
      <el-table-column label="实物处置" min-width="180"><template #default="{ row }">
        <span v-if="approved(row.captureId)">{{ dispositionLabels[approved(row.captureId)!.action] }}</span>
        <el-select v-else v-model="actions[row.captureId]" :aria-label="`处置 ${row.barcode}`" placeholder="原车保留" :disabled="!allowed || busy || disabled || dialog">
          <el-option label="原车保留" value="RETAIN" /><el-option label="卸货释放" value="RELEASE" /><el-option label="卸货隔离" value="QUARANTINE" />
        </el-select>
      </template></el-table-column>
      <el-table-column label="核验凭据" min-width="250"><template #default="{ row }">
        <span v-if="approved(row.captureId)">{{ approved(row.captureId)!.physicalEvidence }}</span>
        <el-input v-else-if="actions[row.captureId] && actions[row.captureId] !== 'RETAIN'" v-model="notes[row.captureId]" :aria-label="`实物凭据 ${row.barcode}`"
          placeholder="卸货位置、结果及凭据编号" maxlength="1000" :disabled="busy || disabled || dialog" />
      </template></el-table-column>
    </el-table>
    <el-button v-if="allowed" :disabled="disabled || busy || !selected.length || selected.some(s => !notes[s.captureId]?.trim())" @click="open">主管批准实物处置凭据</el-button>
    <el-dialog :model-value="dialog" title="主管批准实物处置凭据" width="560" :show-close="false" :close-on-click-modal="false" :close-on-press-escape="false">
      <el-alert v-if="uncertain" title="结果未确认，原请求已保存在本标签页，请重试原请求。" type="warning" :closable="false" />
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <p>批准凭据不可改写。服务器将继续保留占用，直到完整替换批次接收成功；隔离包持续禁止出库。</p>
      <p v-for="s in selected" :key="s.captureId">{{ s.barcode }} · {{ actions[s.captureId] === 'RELEASE' ? '卸货释放' : '卸货隔离' }} · {{ notes[s.captureId] }}</p>
      <el-checkbox v-model="checked" :disabled="busy || uncertain">已现场逐包核验，确认实物移出原车且位置与凭据一致</el-checkbox>
      <template #footer><el-button :disabled="busy || uncertain" @click="close">返回核对</el-button><el-button type="primary" :loading="busy" :disabled="!checked" @click="confirm">{{ uncertain ? '重试原请求' : '确认批准实物处置' }}</el-button></template>
    </el-dialog>
  </div>
</template>
