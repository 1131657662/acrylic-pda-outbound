<script setup lang="ts">
import { computed, ref } from 'vue';
import { z } from 'zod';
import { currentRecoveryEpoch, errorText, request } from '../api';
import { localTime } from '../orders/contracts';
import { pairingSchema, pairingReceiptSchema, pendingPairingSchema, rememberPairing, restorePairing } from './pairing-contracts';
import type { PairingContext } from './pairing-contracts';
const props = defineProps<{ userId: string; warehouseId: string; warehouseName: string }>();
const emit = defineEmits<{ sending: [boolean]; changed: [] }>();
const context = ref<PairingContext | null>(null); const code = ref(''); const error = ref(''); const blocked = ref(false);
const busy = ref(false); const reviewed = ref(false); const confirming = ref(false);
const deviceCode = ref(''); const name = ref('');
try { context.value = restorePairing(props.userId, props.warehouseId); }
catch { blocked.value = true; error.value = '本标签页登记记录无法读取，请保留页面并联系管理员核对。'; }
const application = computed(() => context.value?.application);
const pending = computed(() => context.value?.pending);
const status = computed(() => ({ WAITING: '等待管理员确认', PENDING: '等待原PDA核对并确认', COMPLETED: '原PDA已确认，登记完成',
  EXPIRED: '申请已过期，请原PDA核对后重新申请', CANCELLED: '申请已取消' })[application.value?.status ?? 'WAITING']);
function remember(value: PairingContext): void { rememberPairing(value); context.value = value; }
async function run(action: () => Promise<void>): Promise<void> {
  if (busy.value || blocked.value) return;
  busy.value = true; emit('sending', true); error.value = '';
  try { await action(); } catch (cause) {
    error.value = cause instanceof z.ZodError ? cause.issues[0]?.message ?? '输入格式不正确' : errorText(cause);
  } finally { busy.value = false; emit('sending', false); }
}
async function lookup(): Promise<void> {
  if (pending.value) return;
  await run(async () => {
    const clean = code.value.replace(/\s/g, '');
    if (!/^\d{8}$/.test(clean)) { error.value = '请输入PDA显示的8位登记码'; return; }
    const found = await request(`/device-pairings?${new URLSearchParams({ warehouseId: props.warehouseId, code: clean })}`, pairingSchema);
    remember({ userId: props.userId, warehouseId: props.warehouseId, application: found, pending: null }); reviewed.value = false;
  });
}
async function refresh(): Promise<void> {
  if (!context.value) return;
  const original = context.value;
  await run(async () => {
    const found = await request(`/device-pairings/${original.application.applicationId}?warehouseId=${props.warehouseId}`, pairingSchema);
    if (found.applicationId !== original.application.applicationId || found.keySha256 !== original.application.keySha256) throw new Error('登记结果不匹配');
    const terminal = found.status === 'CANCELLED' || found.status === 'EXPIRED';
    remember({ ...original, application: found, pending: terminal ? null : original.pending }); if (found.status === 'COMPLETED') emit('changed');
  });
}
async function approve(): Promise<void> {
  confirming.value = false;
  await run(async () => {
    if (!context.value) return;
    if (!pending.value) {
      const saved = pendingPairingSchema.parse({ applicationId: context.value.application.applicationId, key: crypto.randomUUID(),
        epoch: currentRecoveryEpoch(), body: { deviceCode: deviceCode.value.trim(), name: name.value.trim() } });
      try { remember({ ...context.value, pending: saved }); }
      catch { error.value = '无法保存原请求，请允许本标签页存储后重试；请求尚未发送。'; return; }
    }
    const saved = context.value.pending; if (!saved) return;
    const receipt = await request(`/device-pairings/${saved.applicationId}/approval?warehouseId=${props.warehouseId}`, pairingReceiptSchema,
      { method: 'POST', body: JSON.stringify(saved.body), headers: { 'Content-Type': 'application/json', 'Idempotency-Key': saved.key, 'X-Server-Recovery-Epoch': saved.epoch } });
    if (receipt.applicationId !== saved.applicationId || receipt.approval.warehouseId !== props.warehouseId ||
        receipt.approval.keySha256 !== context.value.application.keySha256 || receipt.approval.deviceCode !== saved.body.deviceCode ||
        receipt.approval.name !== saved.body.name || receipt.approval.serverRecoveryEpoch !== saved.epoch) throw new Error('登记回执与原请求不匹配');
    remember({ ...context.value, pending: null, application: { ...context.value.application, status: 'PENDING',
      approval: receipt.approval, approvalSha256: receipt.approvalSha256 } });
  });
}
</script>
<template>
  <section class="panel pairing-panel">
    <div class="section-heading"><h2>短码配对登记</h2><span class="muted">两端须连接同一后台</span></div>
    <p class="muted">请在PDA点击“开始登记”，输入屏幕上的8位登记码。管理员确认后，仍须原PDA核对并确认绑定。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form inline @submit.prevent="lookup"><el-form-item label="PDA登记码"><el-input v-model="code" aria-label="PDA登记码" inputmode="numeric"
      maxlength="9" placeholder="例如 4827 1953" :disabled="busy || !!pending || blocked" /></el-form-item>
      <el-button :loading="busy" :disabled="!!pending || blocked" @click="lookup">查找登记申请</el-button></el-form>
    <template v-if="application">
      <el-alert :title="pending ? '原确认请求待核对，刷新本标签页后可按原内容恢复。' : status" :type="application.status === 'COMPLETED' ? 'success' : 'info'" :closable="false" />
      <p>型号：{{ application.model }} · 申请时间：{{ localTime(application.createdAt) }}</p>
      <p class="muted">有效至 {{ localTime(application.expiresAt) }} · 登记仓库：{{ warehouseName }}</p>
      <template v-if="application.approval"><p>设备：{{ application.approval.name }}（{{ application.approval.deviceCode }}）</p></template>
      <el-form v-else label-position="top" :disabled="busy || !!pending || blocked">
        <el-form-item label="配对设备编号"><el-input :model-value="pending?.body.deviceCode ?? deviceCode" @update:model-value="deviceCode = $event" aria-label="配对设备编号" maxlength="40" /></el-form-item>
        <el-form-item label="配对设备名称"><el-input :model-value="pending?.body.name ?? name" @update:model-value="name = $event" aria-label="配对设备名称" maxlength="100" /></el-form-item>
        <el-checkbox v-model="reviewed">已核对登记码、设备型号和当前仓库</el-checkbox>
      </el-form>
      <el-button :disabled="busy" @click="refresh">{{ application.approval ? '核对绑定结果' : '核对申请状态' }}</el-button>
      <el-button v-if="pending" type="primary" :loading="busy" @click="approve">恢复原配对确认</el-button>
      <el-button v-else-if="application.status === 'WAITING'" type="primary" :disabled="busy || blocked || !reviewed || !deviceCode.trim() || !name.trim()" @click="confirming = true">确认登记意图</el-button>
    </template>
    <el-dialog v-model="confirming" title="确认待绑定信息" width="min(520px, 94vw)">
      <p>仓库：{{ warehouseName }}</p><p>设备：{{ name }}（{{ deviceCode }}）</p><p>确认后信息固定，原PDA确认前不会成为已启用设备。</p>
      <template #footer><el-button @click="confirming = false">返回核对</el-button><el-button type="primary" @click="approve">发送给原PDA确认</el-button></template>
    </el-dialog>
  </section>
</template>
<style scoped>
.pairing-panel .el-alert, .pairing-panel p { margin-bottom: 16px; }
.pairing-panel .el-checkbox { height: auto; white-space: normal; margin-bottom: 16px; }
.pairing-panel :deep(.el-checkbox__label) { white-space: normal; }
</style>
