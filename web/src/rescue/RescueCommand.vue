<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { currentRecoveryEpoch, errorText } from '../api';
import { useSessionStore } from '../session';
import { newApproval, revokeSchema } from './contracts';
import type { RescueMaterial } from './contracts';
import { loadCommand, saveCommand, lockCommand, archiveRejectedApproval } from './pending';
import type { RescueOwner, SavedRescueCommand } from './pending';
import { sendCommand } from './commands';

const props = defineProps<{ material: RescueMaterial; originalOperator: string }>();
const emit = defineEmits<{ updated: []; busy: [value: boolean] }>();
const session = useSessionStore(); const busy = ref(false); const error = ref(''); const reason = ref('');
const mode = ref<'FINITE' | 'PERMANENT'>('FINITE'); const expires = ref('');
const approve = ref<SavedRescueCommand | null>(null); const revoke = ref<SavedRescueCommand | null>(null);
const allowed = computed(() => session.identity?.permissions.includes('rescue:authorize'));
const pendingApproval = computed(() => approve.value && !approve.value.receipt);
const pendingRevocation = computed(() => revoke.value && !revoke.value.receipt);
const rejectedApproval = computed(() => approve.value?.action === 'APPROVE' && approve.value.rejectedExpiry);
const canApprove = computed(() => allowed.value && session.identity?.id !== props.originalOperator
  && (props.material.status === 'REQUESTED' && !approve.value?.receipt || pendingApproval.value));
const canRevoke = computed(() => allowed.value && !pendingApproval.value && props.material.authorization
  && (props.material.status !== 'REVOKED' && !revoke.value?.receipt || pendingRevocation.value));
function owner(): RescueOwner {
  if (!session.identity || !session.warehouseId) throw new Error('请恢复原主管会话');
  return { userId: session.identity.id, warehouseId: session.warehouseId, requestId: props.material.request.requestId };
}
function assertOwner(expected: RescueOwner): void {
  const current = owner();
  if (current.userId !== expected.userId || current.warehouseId !== expected.warehouseId || current.requestId !== expected.requestId)
    throw new Error('主管身份或申请范围已变化，请核对原请求');
  if (!allowed.value) throw new Error('主管救援权限已变化，原请求保留');
}
function prepare(expected: RescueOwner, action: SavedRescueCommand['action']): SavedRescueCommand {
  const saved = loadCommand(expected, action); if (saved) return saved;
  const base = { ...expected, key: crypto.randomUUID(), epoch: currentRecoveryEpoch(), receipt: null };
  if (action === 'APPROVE') {
    const body = newApproval({ expiresAt: mode.value === 'PERMANENT' ? null : expires.value.trim(), reason: reason.value });
    const next = { ...base, action, body: JSON.stringify(body), rejectedExpiry: false }; saveCommand(next); return next;
  }
  const body = revokeSchema.parse({ reason: reason.value });
  const grantId = props.material.authorization?.grantId; if (!grantId) throw new Error('没有可撤销的原授权');
  const next = { ...base, action, grantId, body: JSON.stringify(body) }; saveCommand(next); return next;
}
async function send(expected: RescueOwner, action: SavedRescueCommand['action']): Promise<void> {
  assertOwner(expected); const pending = prepare(expected, action);
  if (pending.action === 'APPROVE') approve.value = pending; else revoke.value = pending;
  if (action === 'APPROVE' && expected.userId === props.originalOperator) throw new Error('原操作员不能核准自己的申请');
  const completed = await sendCommand({ pending, material: props.material, assertCurrent: () => assertOwner(expected) });
  if (completed.action === 'APPROVE') approve.value = completed; else revoke.value = completed;
  reason.value = ''; emit('updated');
}
async function confirm(action: SavedRescueCommand['action']): Promise<void> {
  if (busy.value || (action === 'APPROVE' ? !canApprove.value : !canRevoke.value)) return;
  const expected = owner(); busy.value = true; emit('busy', true); error.value = '';
  try { await lockCommand(expected, action, () => send(expected, action)); }
  catch (cause) {
    error.value = cause instanceof Error ? cause.message : errorText(cause);
    try { assertOwner(expected); approve.value = loadCommand(expected, 'APPROVE'); revoke.value = loadCommand(expected, 'REVOKE'); } catch { /* Original storage remains protected. */ }
  }
  finally { busy.value = false; emit('busy', false); }
}
async function restartRejected(): Promise<void> {
  if (busy.value || !rejectedApproval.value || props.material.status !== 'REQUESTED') return;
  const expected = owner(); busy.value = true; emit('busy', true);
  try { await lockCommand(expected, 'APPROVE', async () => { assertOwner(expected); archiveRejectedApproval(expected); approve.value = null; error.value = ''; }); }
  catch (cause) { error.value = cause instanceof Error ? cause.message : errorText(cause); }
  finally { busy.value = false; emit('busy', false); }
}
watch(() => [session.identity?.id, session.warehouseId, props.material.request.requestId], () => {
  approve.value = null; revoke.value = null; reason.value = ''; expires.value = ''; mode.value = 'FINITE'; error.value = '';
  if (!session.identity) return;
  try { const expected = owner(); approve.value = loadCommand(expected, 'APPROVE'); revoke.value = loadCommand(expected, 'REVOKE'); }
  catch { error.value = '原救援命令暂不可读取，请保留浏览器资料；未发送新命令。'; }
}, { immediate: true });
</script>
<template>
  <section class="panel">
    <h2>主管核准与撤销</h2>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="originalOperator === session.identity?.id">原操作员不能核准自己的跨人救援申请。</p>
    <p v-if="!allowed">此仓库操作须具有专用主管救援授权权限。</p>
    <p v-if="pendingApproval || pendingRevocation">结果未确认，原键、正文及恢复代次已保存；重试原命令不会新建授权。</p>
    <template v-if="canApprove && !pendingApproval">
      <el-radio-group v-model="mode" :disabled="busy" aria-label="救援授权期限">
        <el-radio value="FINITE">限定期限</el-radio><el-radio value="PERMANENT">明确永久只读</el-radio>
      </el-radio-group>
      <p v-if="mode === 'PERMANENT'">永久仅限申请中的原件；不授新作业权。原机无法核实时效时仍可只读取回，撤销须将签名材料交给原机。</p>
      <el-input v-if="mode === 'FINITE'" v-model="expires" aria-label="救援到期时间" placeholder="ISO时间，含时区，例如 2026-10-12T18:00:00+08:00" :disabled="busy" />
    </template>
    <el-input v-if="(canApprove || canRevoke) && !pendingApproval && !pendingRevocation" v-model="reason" type="textarea" maxlength="1000" aria-label="救援审批或撤销理由" placeholder="填写范围核对及审批/撤销理由" :disabled="busy" />
    <el-button v-if="canApprove && !rejectedApproval" type="primary" :disabled="busy" @click="confirm('APPROVE')">{{ pendingApproval ? '重试原救援核准' : '核准只读救援' }}</el-button>
    <el-button v-if="rejectedApproval && material.status === 'REQUESTED'" :disabled="busy" @click="restartRejected">保留拒绝原件并重新核准</el-button>
    <el-button v-if="canRevoke" type="danger" plain :disabled="busy" @click="confirm('REVOKE')">{{ pendingRevocation ? '重试原救援撤销' : '撤销原只读授权' }}</el-button>
    <p>离线设备不能即时知道服务端新撤权；请将已签名撤销原件交给原机核验。审批与撤销不会删除原作业记录。</p>
  </section>
</template>
