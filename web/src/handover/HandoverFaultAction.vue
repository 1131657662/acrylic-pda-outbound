<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { useSessionStore } from '../session';
import type { CaseContext } from './contracts';
import { faultArchiveSchema, faultControlSchema } from './fault-contracts';
import { faultBody } from './pending-fault';
import { useFaultCommand } from './useFaultCommand';
import HandoverFaultEvidence from './HandoverFaultEvidence.vue';
const props = defineProps<{ detail: CaseContext }>();
const emit = defineEmits<{ refresh: [] }>();
const session = useSessionStore(); const dialog = ref(false); const confirmed = ref(false);
const deviceLocation = ref(''); const goodsLocation = ref(''); const controlledAt = ref('');
const archive = ref({ sourceEvidenceSetId: '', verificationId: '', setSha256: '', captureHighWatermark: '', auditThroughSeq: '', orderScanHighWatermark: '' });
const { original, busy, error, restore, execute, recheck } = useFaultCommand(
  () => ({ userId: session.identity?.id ?? '', warehouseId: session.warehouseId, caseId: props.detail.context.caseId }), () => props.detail);
const allowed = computed(() => session.identity?.permissions.includes('order:handover'));
const receipt = computed(() => props.detail.fault.receipt);
const valid = computed(() => {
  if (!confirmed.value || !controlledAt.value || Number.isNaN(Date.parse(controlledAt.value))) return false;
  return faultArchiveSchema.safeParse(archive.value).success && faultControlSchema.safeParse(control()).success;
});
function control() {
  return { deviceState: 'POWERED_OFF_IN_CUSTODY' as const, controlledAt: new Date(controlledAt.value).toISOString(),
    deviceLocation: deviceLocation.value, goodsLocation: goodsLocation.value, unresolvedInputCount: '0' as const };
}
function open(): void {
  restore(); dialog.value = true;
  if (original.value) {
    const body = faultBody(original.value); archive.value = body.archivedEvidence;
    deviceLocation.value = body.physicalControl.deviceLocation; goodsLocation.value = body.physicalControl.goodsLocation;
    const date = new Date(body.physicalControl.controlledAt);
    controlledAt.value = new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 23);
    confirmed.value = true;
  }
}
async function submit(): Promise<void> {
  if (!original.value && !valid.value) return;
  const input = original.value ? faultBody(original.value) : { physicalControl: control(), archivedEvidence: faultArchiveSchema.parse(archive.value) };
  await execute(input);
  if (original.value?.receipt && !error.value) { dialog.value = false; emit('refresh'); }
}
async function edit(): Promise<void> { await recheck(); if (!error.value) { confirmed.value = false; } }
onMounted(restore);
</script>
<template>
  <HandoverFaultEvidence v-if="receipt" :receipt="receipt" />
  <section v-if="allowed && !original?.receipt && (detail.fault.entryAvailable || original || error)" class="panel">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-button :disabled="busy || (!!error && !original)" @click="open">{{ original ? '核对原故障请求' : '主管确认故障围栏' }}</el-button>
    <p>仅原机已断电收回、实物位置明确、原输入全部核明且有可信归档截点时可申请；服务端将再次核对原提交终态。</p>
  </section>
  <el-dialog v-model="dialog" class="fault-dialog" title="主管确认故障围栏" width="min(620px, 94vw)" :close-on-click-modal="false" :show-close="!busy">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="original && !original.receipt" :title="original.refusal ? `原请求已拒绝：${original.refusal.message}` : '原结果未确认，请重试同一原请求；原键和内容已保留。'" type="warning" :closable="false" />
    <p>故障设备：{{ detail.fault.sourceDeviceId }}</p>
    <el-form label-position="top" :disabled="busy || !!original">
      <el-form-item label="断电收回保管时间"><el-input v-model="controlledAt" type="datetime-local" aria-label="断电收回保管时间" step="0.001" /></el-form-item>
      <el-form-item label="设备保管位置"><el-input v-model="deviceLocation" aria-label="设备保管位置" maxlength="200" /></el-form-item>
      <el-form-item label="货物位置"><el-input v-model="goodsLocation" aria-label="货物位置" maxlength="200" /></el-form-item>
      <el-form-item label="归档集合编号"><el-input v-model="archive.sourceEvidenceSetId" aria-label="归档集合编号" /></el-form-item>
      <el-form-item label="归档验证编号"><el-input v-model="archive.verificationId" aria-label="归档验证编号" /></el-form-item>
      <el-form-item label="归档原文摘要"><el-input v-model="archive.setSha256" aria-label="归档原文摘要" /></el-form-item>
      <el-form-item label="采集截至"><el-input v-model="archive.captureHighWatermark" aria-label="采集截至" inputmode="numeric" /></el-form-item>
      <el-form-item label="审计截至"><el-input v-model="archive.auditThroughSeq" aria-label="审计截至" inputmode="numeric" /></el-form-item>
      <el-form-item label="扫描截至"><el-input v-model="archive.orderScanHighWatermark" aria-label="扫描截至" inputmode="numeric" /></el-form-item>
      <el-form-item><label class="fault-custody"><input v-model="confirmed" type="checkbox" :disabled="busy || !!original" />
        <span>确认原机已断电收回保管、货物位置已核对，且没有未核明的输入</span></label></el-form-item>
    </el-form>
    <p v-if="original">原请求编号：{{ faultBody(original).commandId }}</p>
    <template #footer>
      <el-button :disabled="busy" @click="dialog = false">返回核对</el-button>
      <el-button v-if="original?.refusal" :disabled="busy" @click="edit">重新核对资料</el-button>
      <el-button type="primary" :loading="busy" :disabled="busy || (!original && !valid)" @click="submit">{{ original ? '重试原请求' : '确认提交故障围栏' }}</el-button>
    </template>
  </el-dialog>
</template>
<style scoped>
.fault-custody { display: flex; align-items: flex-start; gap: var(--el-font-size-base); }
.fault-custody input { flex: none; accent-color: var(--el-color-primary); }
.fault-custody span { white-space: normal; overflow-wrap: anywhere; }
</style>
