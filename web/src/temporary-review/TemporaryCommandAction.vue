<script setup lang="ts">
import { computed, ref } from 'vue';
import { commandKindLabel, commandStatusLabel, type CommandKind } from './command-contracts';
import { useTemporaryCommand } from './useTemporaryCommand';
const props = defineProps<{ kind: CommandKind; submissionId: string; warehouseId: string; candidateContextSha256?: string | null;
  available: boolean; unavailableReason: string; label: string; hint: string; reasonRequired?: boolean }>();
const { busy, error, receipt, closed, loadFacts, execute, startOver } = useTemporaryCommand(
  () => ({ submissionId: props.submissionId, warehouseId: props.warehouseId }));
const dialog = ref(false); const reason = ref('');
const reasonRequired = computed(() => props.reasonRequired === true);
const capabilityRows = computed<[string, boolean][]>(() => receipt.value ? [
  ['写确认可用', receipt.value.capabilities.writeConfirmationAvailable],
  ['允许放行', receipt.value.capabilities.releaseAllowed],
  ['可恢复扫码', receipt.value.capabilities.mayResumeScanning],
  ['已释放占用', receipt.value.capabilities.stockReleased],
  ['允许本机删除', receipt.value.capabilities.localDeletionAllowed],
] : []);
function open(): void { dialog.value = true; void loadFacts(); }
async function submit(): Promise<void> {
  await execute(props.kind, { reason: reason.value, candidateContextSha256: props.candidateContextSha256 ?? null });
  if (!error.value && !closed.value) dialog.value = false;
}
</script>
<template>
  <div class="command-action">
    <el-button v-if="available" type="primary" :loading="busy" @click="open">{{ label }}</el-button>
    <p v-else class="muted">{{ unavailableReason }}</p>
    <el-alert v-if="closed" title="该功能尚未开放：服务端命令开关处于关闭状态。" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <section v-if="receipt" class="panel">
      <h4>原回执</h4>
      <p>{{ commandKindLabel(receipt.commandType) }} · {{ commandStatusLabel(receipt.status) }} · 原键 {{ receipt.commandId }}</p>
      <ul><li v-for="row in capabilityRows" :key="row[0]">{{ row[0] }}：{{ row[1] ? '是' : '否' }}</li></ul>
      <p class="muted">原回执按原键保存；继续重试会返回同一份回执，不产生第二次决定。此回执不代表放行。</p>
      <el-button @click="startOver">准备新的决定</el-button>
    </section>
    <el-dialog v-model="dialog" :title="label" width="520px">
      <p>{{ hint }}</p>
      <el-alert v-if="closed" title="服务端命令开关未启用，提交将被拒绝。" type="info" :closable="false" />
      <label>人工核对理由<el-input v-model="reason" type="textarea" :rows="2" maxlength="500" placeholder="填写核对依据，便于留痕" /></label>
      <p v-if="reasonRequired && !reason.trim()" class="muted">关联必须填写人工核对理由。</p>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :disabled="busy || (reasonRequired && !reason.trim())" :loading="busy" @click="submit">确认提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>
