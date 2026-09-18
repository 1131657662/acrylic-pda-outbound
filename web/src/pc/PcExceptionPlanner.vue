<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { kilograms } from '../orders/submission-contracts';
import { dispositionLabels, type Stopped, type DispositionLine } from './exception-contracts';
const props = defineProps<{ stopped: Stopped; version: string; disabled: boolean }>();
const emit = defineEmits<{ submit: [body: object]; dirty: [dirty: boolean] }>();
const target = ref(''); const outcome = ref<'CONTINUE' | 'CANCEL'>('CONTINUE'); const reason = ref(''); const confirmed = ref(false); const page = ref(1);
const rows = ref(props.stopped.snapshot.entries.filter(e => e.status === 'ACTIVE').map(entry => ({ entry, action: '' as DispositionLine['action'] | '', physicalEvidence: '' })));
const visible = computed(() => rows.value.slice((page.value - 1) * 20, page.value * 20));
const reviewed = computed(() => rows.value.filter(row => row.action && (row.action === 'RETAIN' || row.physicalEvidence.trim())).length);
const valid = computed(() => target.value.trim() && reason.value.trim() && confirmed.value && reviewed.value === rows.value.length && (outcome.value !== 'CANCEL' || rows.value.every(row => row.action !== 'RETAIN')));
watch([target, outcome, reason, rows, confirmed], () => emit('dirty', true), { deep: true });
function submit(): void {
  if (!valid.value || props.disabled) return;
  emit('submit', { caseId: props.stopped.id, snapshotSha256: props.stopped.snapshotSha256, expectedVersion: props.version,
    targetUsername: target.value.trim(), outcome: outcome.value, reason: reason.value.trim(), physicalConfirmed: true,
    lines: rows.value.map(row => ({ entryId: row.entry.id, expectedCycle: row.entry.cycle, action: row.action, physicalEvidence: row.physicalEvidence.trim() })) });
}
</script>
<template>
  <form class="pc-exception-planner" @submit.prevent="submit" @input="emit('dirty', true)">
    <h2>逐包核对处置方案</h2><p>已核对 {{ reviewed }} / {{ rows.length }} 包。每包明确选择去向；审批后仍由指定人员确认交接才会生效。</p>
    <div class="pc-exception-fields">
      <el-form-item label="接续账号"><el-input v-model="target" aria-label="接续账号" maxlength="100" :disabled="disabled" placeholder="填写接续人员的登录账号" /></el-form-item>
      <el-form-item label="交接后安排"><el-select v-model="outcome" aria-label="交接后安排" :disabled="disabled"><el-option label="继续本单录入" value="CONTINUE" /><el-option label="全部卸下，结束本单" value="CANCEL" /></el-select></el-form-item>
    </div>
    <p v-if="outcome === 'CANCEL'" class="muted">结束本单不得保留任何包，不创建出库结果或扣减库存；隔离占用继续有效。</p>
    <div class="pc-exception-table"><el-table :data="visible" row-key="entry.id">
      <el-table-column label="原包 / 产品" min-width="180"><template #default="{ row }"><strong class="barcode">{{ row.entry.barcode }}</strong><div>{{ row.entry.deliveryCode }} · {{ row.entry.model }}</div><div>{{ kilograms(row.entry.weightGrams) }} kg · 原序号 {{ row.entry.sequence }}</div></template></el-table-column>
      <el-table-column label="实物去向" min-width="185"><template #default="{ row }"><el-select v-model="row.action" :aria-label="`处置 ${row.entry.barcode}`" :disabled="disabled" placeholder="逐包选择"><el-option v-for="(label, action) in dispositionLabels" :key="action" :value="action" :label="label" /></el-select></template></el-table-column>
      <el-table-column label="实物位置、结果及核验凭据" min-width="260"><template #default="{ row }"><el-input v-model="row.physicalEvidence" :aria-label="`核验凭据 ${row.entry.barcode}`" type="textarea" :rows="2" maxlength="1000" :disabled="disabled" :placeholder="row.action === 'RETAIN' ? '可补充本车保留核对记录' : '卸货或隔离时必填'" /></template></el-table-column>
    </el-table></div>
    <el-pagination v-if="rows.length > 20" layout="total, prev, pager, next" :total="rows.length" :page-size="20" v-model:current-page="page" />
    <el-form-item label="交接及处置原因"><el-input v-model="reason" aria-label="交接及处置原因" type="textarea" :rows="2" maxlength="1000" :disabled="disabled" /></el-form-item>
    <el-checkbox v-model="confirmed" :disabled="disabled">已逐包核对全部原有效包、实物去向和接续账号</el-checkbox>
    <div class="page-actions"><el-button native-type="submit" type="primary" :disabled="disabled || !valid">提交处置方案</el-button></div>
  </form>
</template>
