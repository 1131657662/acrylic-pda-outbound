<script setup lang="ts">
import { computed, ref } from 'vue';
import { useSessionStore } from '../session';
import { type DuplicateCandidate } from './duplicate-contracts';
import { exclusionInputSchema, exclusionBasisLabel, type ExclusionInput } from './exclusion-contracts';
import { savedCommandBody } from './pending-command';
import { useTemporaryCommand } from './useTemporaryCommand';
const props = defineProps<{ candidate: DuplicateCandidate; submissionId: string; warehouseId: string;
  commandContextSha256: string | null; available: boolean }>();
const session = useSessionStore();
const { busy, error, receipt, closed, original, restore, execute, startOver } = useTemporaryCommand(
  () => ({ userId: session.identity?.id ?? '', submissionId: props.submissionId, warehouseId: props.warehouseId }));
const dialog = ref(false); const reason = ref(''); const evidence = ref('');
const basis = ref<ExclusionInput['basis']>('CANCELLED'); const withdrawal = ref('');
const input = computed(() => exclusionInputSchema.safeParse({ orderId: props.candidate.order.id,
  revision: String(props.candidate.order.revision), rowVersion: props.candidate.order.rowVersion, basis: basis.value,
  evidence: evidence.value, withdrawalRequestId: basis.value === 'WITHDRAWN' ? withdrawal.value.trim() : null }));
const permitted = computed(() => session.identity?.permissions.includes('temporary_order:link') === true);
const pendingInput = computed(() => original.value ? savedCommandBody(original.value).exclusion : undefined);
function open(): void { restore('DUPLICATE_EXCLUDE'); dialog.value = true; }
async function submit(): Promise<void> {
  if (!original.value && (!input.value.success || !reason.value.trim())) return;
  await execute('DUPLICATE_EXCLUDE', { reason: reason.value, candidateContextSha256: props.commandContextSha256,
    exclusion: input.value.success ? input.value.data : undefined });
}
</script>
<template>
  <section class="command-action" aria-label="逐项排除候选">
    <el-button v-if="permitted && available && commandContextSha256" @click="open">核对并排除该候选</el-button>
    <p v-else class="muted">候选排除需本仓主管关联权限、有效待审原件及后台命令入口。</p>
    <el-dialog v-model="dialog" title="主管逐项核对候选排除" width="520px">
      <p>候选 {{ candidate.order.orderNo }} · {{ candidate.order.id }} · 计划 V{{ candidate.order.revision }} · 记录 {{ candidate.order.rowVersion }}</p>
      <p>排除只改变人工选择。原候选、占用及提交保留；有效作业或库存冲突不能通过理由排除。关联或集合变化后需重新核对。</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-alert v-if="closed" title="后台命令入口尚未开放，原请求保留。" type="info" :closable="false" />
      <template v-if="original">
        <p>已保存原键 {{ savedCommandBody(original).commandId }}，本仓该批次一次处理一条排除原请求。</p>
        <p v-if="pendingInput">原候选 {{ pendingInput.orderId }} · {{ exclusionBasisLabel(pendingInput.basis) }} · 依据 {{ pendingInput.evidence }}</p>
        <p>原理由 {{ savedCommandBody(original).reason }}</p>
        <p v-if="!original.receipt">结果尚未核对，重试保留原候选、原版本及正文；不会换成这张候选卡。</p>
      </template>
      <template v-else>
        <label>排除类别<el-select v-model="basis" :disabled="busy">
          <el-option label="已作废" value="CANCELLED" /><el-option label="已故障撤回、尚未重发" value="WITHDRAWN" />
          <el-option label="不是同一业务" value="UNRELATED" />
        </el-select></label>
        <label v-if="basis === 'WITHDRAWN'">原撤回请求 ID<el-input v-model="withdrawal" :disabled="busy" /></label>
        <label>逐单核对依据<el-input v-model="evidence" type="textarea" maxlength="1000" :disabled="busy" /></label>
        <label>主管理由<el-input v-model="reason" type="textarea" maxlength="500" :disabled="busy" /></label>
        <p v-if="!input.success || !reason.trim()">请完整填写类别、依据、理由及适用的原撤回请求。</p>
      </template>
      <p v-if="receipt">排除原回执已按原键保存，不代表关联完成、库存释放或允许放行。</p>
      <template #footer>
        <el-button @click="dialog = false">返回候选</el-button>
        <el-button v-if="original?.receipt" :disabled="busy" @click="startOver('DUPLICATE_EXCLUDE')">核对下一条／新的集合</el-button>
        <el-button v-else type="primary" :disabled="busy || (!original && (!input.success || !reason.trim()))" :loading="busy" @click="submit">
          {{ original ? '重试原排除请求' : '确认该项排除' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
