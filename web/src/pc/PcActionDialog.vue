<script setup lang="ts">
import { computed, ref } from 'vue';
import type { Detail } from '../orders/contracts';
import type { Policy } from '../policies/contracts';
import type { PcView, Entry } from './contracts';
import type { Action } from './pending';
import { kilograms } from '../orders/submission-contracts';
export type DialogKind = 'start' | 'writer' | 'void' | 'submit' | 'approve' | 'reject' | 'resume';
const props = defineProps<{ kind: DialogKind; detail: Detail; view: PcView; policy: Policy | null; entry: Entry | null; writer: string; busy: boolean; pending: boolean; error: string }>();
const emit = defineEmits<{ cancel: []; confirm: [action: Action, body: object]; recover: [] }>();
const reason = ref(''); const confirmed = ref(false);
const titles: Record<DialogKind, string> = { start: '核对并开始PC作业', writer: '在本窗口继续PC录入', void: '撤销本次录入', submit: '结单核对', approve: '批准当前PC出库', reject: '退回PC作业并保留占用', resume: '核验实物并恢复原PC作业' };
const short = computed(() => props.view.products.some(p => p.actualCount < p.plannedCount));
const needsReason = computed(() => !['writer'].includes(props.kind) && !(props.kind === 'submit' && !short.value));
const valid = computed(() => confirmed.value && (!needsReason.value || !!reason.value.trim()) && !(props.kind === 'start' && props.detail.order.status === 'DRAFT' && !props.policy));
function command(): void {
  if (!valid.value || props.busy || props.pending) return;
  const work = props.view.work; const common = { writerId: props.writer, expectedVersion: work?.version ?? props.detail.order.rowVersion, fence: work?.fence ?? '1', barcode: '', entryId: null, reason: reason.value.trim(), physicalConfirmed: confirmed.value };
  if (props.kind === 'start' || props.kind === 'writer') emit('confirm', props.kind, { expectedVersion: common.expectedVersion, writerId: props.writer, policyVersion: props.policy?.version ?? '', reason: common.reason });
  else if (props.kind === 'approve' || props.kind === 'reject') emit('confirm', 'review', { payloadSha256: props.view.submission?.payloadSha256, decision: props.kind === 'approve' ? 'APPROVE' : 'REJECT', reason: common.reason });
  else if (props.kind === 'resume') emit('confirm', 'resume', { expectedVersion: work?.version, physicalEvidence: common.reason });
  else emit('confirm', props.kind, { ...common, entryId: props.kind === 'void' ? props.entry?.id : null });
}
</script>
<template>
  <el-dialog :model-value="true" :title="titles[kind]" width="680" :show-close="!busy && !pending" :close-on-click-modal="false" :close-on-press-escape="false" @close="emit('cancel')">
    <p><strong>{{ detail.plan.vehicleNo }}</strong> · {{ detail.order.orderNo }}</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="pending" title="原操作结果待核对，请恢复原请求；不要另建请求。" type="warning" :closable="false" />
    <template v-if="kind === 'start'"><p>当前计划 {{ detail.plan.plannedTotal }} 包。确认后本单由当前人员持有，PDA与其他人员不能并行领取。</p><p v-if="detail.order.status === 'DRAFT'">发布与开始PC录入会同时完成。出库规则 {{ policy ? `V${policy.version}，${policy.autoDeductInventory ? '自动扣库存' : '不自动扣库存'}` : '尚未设置，请先设置出库规则' }}。</p></template>
    <p v-if="kind === 'writer'">原录入和占用保持。确认后，其他窗口将停止写入；请先核对是否仍在其他电脑操作。</p>
    <template v-if="kind === 'void'"><p class="barcode">{{ entry?.barcode }} · {{ entry?.deliveryCode }} · {{ kilograms(entry?.weightGrams ?? '0') }} kg</p><p>核对实物未计入本车后撤销。后台将释放本次在线占用，原录入及撤销原因继续保留。</p></template>
    <template v-if="['submit', 'approve', 'reject'].includes(kind)">
      <el-table :data="view.products"><el-table-column prop="deliveryCode" label="代码" width="90" /><el-table-column prop="plannedCount" label="计划包数" /><el-table-column prop="actualCount" label="有效包数" /><el-table-column label="差额"><template #default="{ row }">{{ row.plannedCount - row.actualCount }} 包</template></el-table-column><el-table-column label="净重 kg"><template #default="{ row }">{{ kilograms(row.weightGrams) }}</template></el-table-column></el-table>
      <p v-if="kind === 'submit'">{{ short ? '存在产品短装，请填写原因。提交后须主管审核，不能直接放行。' : '提交后冻结录入，由后台核对占用和库存，确认出库后才能放行。' }}</p>
      <p v-else>{{ kind === 'approve' ? '请逐产品核对实物、车号及短装原因。批准仍需后台通过库存复核。' : '退回不会释放占用或自动恢复录入，后续需主管核验实物后恢复。' }}</p>
      <p v-if="view.submission?.shortReason">原短装原因：{{ view.submission.shortReason }}</p>
      <p v-for="(issue, index) in view.submission?.issues" :key="index">{{ issue.barcode }} {{ issue.reason }}</p>
    </template>
    <p v-if="kind === 'resume'">仅恢复原人员的PC作业，全部原有效包与占用保留。请逐包核实实物仍在原车；此操作不会办理换人、卸货或减包，原冻结提交不改写。</p>
    <el-form-item v-if="needsReason" :label="kind === 'resume' ? '实物核验凭据' : kind === 'submit' ? '短装原因' : '操作原因'"><el-input v-model="reason" :aria-label="kind === 'resume' ? '实物核验凭据' : kind === 'submit' ? '短装原因' : '操作原因'" type="textarea" :maxlength="kind === 'resume' ? 1000 : 500" :disabled="busy || pending" /></el-form-item>
    <el-checkbox v-model="confirmed" :disabled="busy || pending">已核对本单、实物及上述操作影响</el-checkbox>
    <template #footer><el-button :disabled="busy || pending" @click="emit('cancel')">返回核对</el-button><el-button type="primary" :loading="busy" :disabled="!pending && !valid" @click="pending ? emit('recover') : command()">{{ pending ? '恢复原请求' : kind === 'submit' ? '确认结单并提交校验' : kind === 'start' && detail.order.status === 'DRAFT' ? '发布并开始PC录入' : '确认此操作' }}</el-button></template>
  </el-dialog>
</template>
