<script setup lang="ts">
import { computed, ref } from 'vue';
import { z } from 'zod';
import { ApiError, currentRecoveryEpoch, errorText, request } from '../api';
import { pendingSchema, receiptSchema } from './contracts';
import type { Device, DeviceReceipt, PendingDeviceRequest } from './contracts';
import { clearDeviceRequest, saveDeviceRequest } from './pending';
const props = defineProps<{ userId: string; warehouseId: string; warehouseName: string; device: Device | null;
  pending: PendingDeviceRequest | null; action?: 'STATUS' | 'TEMPORARY_ORDER' }>();
const emit = defineEmits<{ close: []; saved: [DeviceReceipt]; sending: [boolean] }>();
const original = ref(props.pending); const busy = ref(false); const error = ref(''); const reviewed = ref(false);
const device = props.pending && props.pending.kind !== 'REGISTER' ? props.pending.device : props.device;
const temporary = (props.pending?.kind ?? props.action) === 'TEMPORARY_ORDER';
const enabled = props.pending?.kind === 'TEMPORARY_ORDER' ? props.pending.command.enabled : !device?.temporaryOrderEnabled;
const registration = props.pending?.kind === 'REGISTER' ? props.pending.command : null;
const form = ref({ deviceCode: registration?.deviceCode ?? '', name: registration?.name ?? '', publicKey: registration?.publicKey ?? '',
  reason: props.pending && props.pending.kind !== 'REGISTER' ? props.pending.command.reason : '' });
const target = props.pending?.kind === 'STATUS' ? props.pending.command.status : device?.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
const title = temporary ? (enabled ? '开启临时作业准入' : '关闭临时作业准入')
  : device ? (target === 'DISABLED' ? '停用 PDA 设备' : '重新启用 PDA 设备') : '登记 PDA 设备';
const ready = computed(() => original.value !== null || (reviewed.value && (device ? !!form.value.reason.trim()
  : !!form.value.deviceCode.trim() && !!form.value.name.trim() && !!form.value.publicKey.trim())));
function prepare(): PendingDeviceRequest {
  const base = { userId: props.userId, warehouseId: props.warehouseId, epoch: currentRecoveryEpoch(), key: crypto.randomUUID() };
  if (temporary && device) return pendingSchema.parse({ ...base, kind: 'TEMPORARY_ORDER', device,
    command: { expectedVersion: device.rowVersion, enabled, reason: form.value.reason.trim() } });
  return pendingSchema.parse(device ? { ...base, kind: 'STATUS', device,
    command: { expectedVersion: device.rowVersion, status: target, reason: form.value.reason.trim() } }
    : { ...base, kind: 'REGISTER', command: { deviceCode: form.value.deviceCode.trim(), name: form.value.name.trim(), publicKey: form.value.publicKey.trim() } });
}
function remember(): boolean {
  if (original.value) return true;
  try { const value = prepare(); saveDeviceRequest(value); original.value = value; return true; }
  catch (cause) {
    error.value = cause instanceof z.ZodError ? cause.issues[0]?.message ?? '请核对输入格式。'
      : '无法保存待处理请求，请允许本标签页存储后重试；请求尚未发送。';
    return false;
  }
}
async function sendOriginal(saved: PendingDeviceRequest): Promise<DeviceReceipt> {
  const suffix = saved.kind === 'REGISTER' ? '' : `/${saved.device.id}/${saved.kind === 'STATUS' ? 'status' : 'temporary-order'}`;
  const result = await request(`/devices${suffix}?warehouseId=${saved.warehouseId}`, receiptSchema, {
    method: 'POST', body: JSON.stringify(saved.command), headers: { 'Content-Type': 'application/json',
      'Idempotency-Key': saved.key, 'X-Server-Recovery-Epoch': saved.epoch },
  });
  const expectedVersion = saved.kind === 'REGISTER' ? '1' : String(BigInt(saved.command.expectedVersion) + 1n);
  const expectedStatus = saved.kind === 'REGISTER' ? 'ACTIVE' : saved.kind === 'STATUS' ? saved.command.status : saved.device.status;
  const expectedTemporary = saved.kind === 'REGISTER' ? false : saved.kind === 'TEMPORARY_ORDER' ? saved.command.enabled : saved.device.temporaryOrderEnabled;
  if (result.rowVersion !== expectedVersion || result.status !== expectedStatus || result.temporaryOrderEnabled !== expectedTemporary
    || (saved.kind !== 'REGISTER' && result.deviceId !== saved.device.id))
    throw new Error('设备操作回执与原请求不匹配');
  return result;
}
async function submit(): Promise<void> {
  if (busy.value || !ready.value) return;
  error.value = ''; const recovering = original.value !== null;
  if (!remember() || !original.value) return;
  const saved = original.value; busy.value = true; emit('sending', true);
  try {
    const result = await sendOriginal(saved);
    clearDeviceRequest(saved); original.value = null; emit('saved', result);
  } catch (cause) {
    error.value = errorText(cause);
    if (!recovering && cause instanceof ApiError && cause.status >= 400 && cause.status < 500) {
      try { clearDeviceRequest(saved); original.value = null; } catch { /* Retain the original request if storage is unavailable. */ }
    }
  } finally { busy.value = false; emit('sending', false); }
}
</script>
<template>
  <el-dialog :model-value="true" :title="title" width="min(620px, 94vw)" :close-on-click-modal="false"
    :show-close="!busy" :close-on-press-escape="!busy" @close="emit('close')">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="original" title="原请求待核验。刷新本标签页后仍可恢复；重试会使用原内容和原请求编号。"
      type="warning" :closable="false" />
    <p class="muted">登记仓库：{{ warehouseName }}</p>
    <el-form label-position="top" :disabled="busy || !!original" @submit.prevent="submit">
      <template v-if="!device">
        <el-form-item label="设备编号"><el-input v-model="form.deviceCode" aria-label="设备编号" maxlength="40" placeholder="例如 PDA-01，仅字母、数字、下划线和短横线" /></el-form-item>
        <el-form-item label="设备名称"><el-input v-model="form.name" aria-label="设备名称" maxlength="100" placeholder="例如 东侧装车 PDA" /></el-form-item>
        <el-form-item label="本机登记公钥"><el-input v-model="form.publicKey" aria-label="本机登记公钥" type="textarea" :rows="4" maxlength="200" placeholder="在正式PDA首页复制本机登记公钥，并粘贴到这里" /></el-form-item>
        <p class="muted">登记后将设备ID填入这台PDA。公钥与仓库绑定；重装或密钥丢失须核对原订单占用，再登记新身份。</p>
      </template>
      <template v-else>
        <p><strong>{{ device.name }}</strong> · {{ device.deviceCode }}</p>
        <p v-if="temporary" class="muted">调整准入后，这台 PDA 须重新登录。开启准入后仍需指定人员的建单权限和完整准备资料，才能开始临时作业。原订单、授权和占用保留；离线设备不能立即收到关闭通知。</p>
        <p v-else class="muted">{{ target === 'DISABLED' ? '停用会阻止联网认证并作废现有登录会话。离线PDA不能立即收到停用通知。' : '重新启用后须重新登录，旧令牌不会恢复；原离线授权期限不延长。' }}订单占用和本机记录始终保留。</p>
        <el-form-item :label="temporary ? '准入调整原因' : '启停原因'"><el-input v-model="form.reason" :aria-label="temporary ? '准入调整原因' : '启停原因'" type="textarea" maxlength="500" :rows="3" /></el-form-item>
      </template>
      <el-checkbox v-model="reviewed" class="device-confirm">{{ temporary ? '已核对设备和影响，确认调整临时作业准入' : device ? '已核对设备和影响，确认此次启停' : '已核对当前仓库，并确认公钥来自这台PDA' }}</el-checkbox>
    </el-form>
    <template #footer><el-button :disabled="busy" @click="emit('close')">{{ original ? '稍后核对' : '取消' }}</el-button>
      <el-button type="primary" :loading="busy" :disabled="!ready" @click="submit">{{ original ? '重试原设备请求' : '确认' + title }}</el-button></template>
  </el-dialog>
</template>
<style scoped>
.el-alert, p { margin-bottom: 16px; }
.device-confirm { height: auto; white-space: normal; }
.device-confirm :deep(.el-checkbox__label) { white-space: normal; }
</style>
