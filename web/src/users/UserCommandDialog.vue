<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { currentRecoveryEpoch } from '../api';
import { pendingSchema, actionLabel, type ManagedUser, type PendingUserRequest, type UserMode, type UserOptions, type UserReceipt } from './contracts';
import { useUserCommand } from './useUserCommand';
import UserFields from './UserFields.vue';
const props = defineProps<{ userId: string; warehouseId: string; options: UserOptions; mode: UserMode; item: ManagedUser | null; pending: PendingUserRequest | null }>();
const emit = defineEmits<{ close: []; saved: [UserReceipt]; sending: [boolean] }>();
const command = useUserCommand(props.pending, value => emit('saved', value));
const { original, busy, error, retryAllowed, rejected } = command;
const saved = props.pending; const mode = saved?.kind ?? props.mode;
const storedProfile = saved && saved.kind !== 'PASSWORD' ? saved.command : null;
const username = ref(saved?.kind === 'CREATE' ? saved.command.username : saved?.username ?? props.item?.username ?? '');
const displayName = ref(storedProfile?.displayName ?? props.item?.displayName ?? '');
const roles = ref(storedProfile?.roles ?? props.item?.roles ?? []);
const warehouses = ref(storedProfile?.warehouseIds ?? props.item?.warehouses.map(w => w.id) ?? [props.warehouseId]);
const status = ref(saved?.kind === 'UPDATE' ? saved.command.status : props.item?.status ?? 'ACTIVE');
const reason = ref(saved?.command.reason ?? ''); const password = ref(''); const confirmed = ref(false);
const title = actionLabel(mode);
const passwordNeeded = computed(() => mode !== 'UPDATE');
function prepare(): PendingUserRequest {
  const base = { userId: props.userId, warehouseId: props.warehouseId, epoch: currentRecoveryEpoch(), key: crypto.randomUUID() };
  const profile = { displayName: displayName.value.trim(), roles: roles.value, warehouseIds: warehouses.value, reason: reason.value.trim() };
  if (mode === 'CREATE') return pendingSchema.parse({ ...base, kind: mode, command: { ...profile, username: username.value.trim() } });
  return pendingSchema.parse({ ...base, kind: mode, targetId: props.item?.id, username: username.value,
    command: mode === 'PASSWORD' ? { expectedVersion: props.item?.rowVersion, reason: reason.value.trim() }
      : { ...profile, status: status.value, expectedVersion: props.item?.rowVersion } });
}
async function submit(): Promise<void> {
  if (!confirmed.value && !original.value) return;
  await command.send(prepare, password.value); password.value = '';
}
watch(busy, value => emit('sending', value), { flush: 'sync' });
</script>
<template>
  <el-dialog :model-value="true" :title="title" width="min(680px, 94vw)" :close-on-click-modal="false" :show-close="!busy" :close-on-press-escape="!busy" @close="emit('close')">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="rejected" title="本次操作未执行。请关闭窗口后重新发起操作，并核对最新人员资料。" type="info" :closable="false" />
    <el-alert v-if="original" title="操作结果待核对。原请求已保存在当前标签页，密码未保存。" type="warning" :closable="false" />
    <el-form label-position="top" :disabled="rejected" @submit.prevent="submit">
      <el-form-item label="登录账号" required><el-input v-model="username" aria-label="登录账号" maxlength="64" :disabled="mode !== 'CREATE' || busy || !!original" autocomplete="off" /></el-form-item>
      <UserFields v-if="mode !== 'PASSWORD'" v-model:display-name="displayName" v-model:roles="roles" v-model:warehouses="warehouses" :options="options" :current-warehouse="warehouseId" :disabled="busy || !!original" />
      <el-form-item v-if="mode === 'UPDATE'" label="账号状态"><el-switch :model-value="status === 'ACTIVE'" active-text="已启用" inactive-text="已停用" :disabled="busy || !!original" @update:model-value="status = $event ? 'ACTIVE' : 'DISABLED'" /></el-form-item>
      <el-form-item v-if="passwordNeeded" label="新密码" required><el-input v-model="password" aria-label="新密码" type="password" show-password autocomplete="new-password" :disabled="busy || (!!original && !retryAllowed)" maxlength="72" /><span class="muted">至少12位，最多72个UTF-8字节</span></el-form-item>
      <el-form-item label="操作原因" required><el-input v-model="reason" aria-label="操作原因" type="textarea" :rows="2" maxlength="500" :disabled="busy || !!original" /></el-form-item>
      <p class="muted">{{ mode === 'CREATE' ? '新人员只获得所选角色和仓库权限。' : '保存后该人员须重新登录。离线设备回网后核验变更，原订单归属、作业记录和占用保留。' }}</p>
      <el-checkbox v-if="!original && !rejected" v-model="confirmed" class="user-confirm" :disabled="busy">已核对人员、授权范围和操作影响</el-checkbox>
    </el-form>
    <template #footer><el-button :disabled="busy" @click="emit('close')">{{ original ? '稍后核对' : '取消' }}</el-button>
      <el-button v-if="original" :loading="busy" @click="command.lookup">查询原提交结果</el-button>
      <el-button v-if="(!original || retryAllowed) && !rejected" type="primary" :loading="busy" :disabled="!original && !confirmed" @click="submit">{{ original ? '补发原请求' : title === '调整人员授权' ? '保存人员授权' : title }}</el-button>
    </template>
  </el-dialog>
</template>
