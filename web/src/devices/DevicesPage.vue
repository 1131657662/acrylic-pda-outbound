<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import { useSessionStore } from '../session';
import { request } from '../api';
import { useCursorPage } from '../useCursorPage';
import CursorPager from '../components/CursorPager.vue';
import QueryFeedback from '../components/QueryFeedback.vue';
import { localTime } from '../orders/contracts';
import { devicesSchema } from './contracts';
import type { Device, DeviceReceipt, PendingDeviceRequest } from './contracts';
import { loadDeviceRequest } from './pending';
import DeviceCommandDialog from './DeviceCommandDialog.vue';
import DeviceIdentityDialog from './DeviceIdentityDialog.vue';
import DevicePairingPanel from './DevicePairingPanel.vue';
const session = useSessionStore();
const canManage = computed(() => !!session.identity?.permissions.includes('device:manage'));
const warehouseName = computed(() => session.identity?.warehouses.find(w => w.id === session.warehouseId)?.name ?? '');
const pending = ref<PendingDeviceRequest | null>(null); const storageError = ref(''); const sending = ref(false);
const pairingSending = ref(false);
const dialog = ref<{ device: Device | null; pending: PendingDeviceRequest | null } | null>(null);
const identity = ref<Device | null>(null); const receipt = ref<DeviceReceipt | null>(null);
function restore(): void {
  storageError.value = ''; pending.value = null;
  if (!session.identity) return;
  try { pending.value = loadDeviceRequest({ userId: session.identity.id, warehouseId: session.warehouseId }); }
  catch { storageError.value = '本标签页待处理记录无法读取，已暂停新设备操作。请保留此页面并联系管理员核对。'; }
}
const paging = useCursorPage(async (beforeId) => {
  if (!canManage.value || !session.warehouseId) return { items: [], nextCursor: null };
  return request(`/devices?${new URLSearchParams({ warehouseId: session.warehouseId, beforeId, limit: '50' })}`, devicesSchema);
});
const { rows: items, busy, error, page, nextCursor } = paging;
async function saved(value: DeviceReceipt): Promise<void> {
  receipt.value = value; dialog.value = null; restore(); await paging.reload();
}
function close(): void { dialog.value = null; restore(); }
watch(() => [session.warehouseId, session.identity?.id, canManage.value], () => {
  dialog.value = null; identity.value = null; receipt.value = null; restore(); void paging.reset();
}, { immediate: true });
onBeforeRouteLeave(() => !sending.value && !pairingSending.value);
</script>
<template>
  <WorkspaceLayout title="PDA 设备管理" :busy="busy || !!dialog || pairingSending">
    <div class="page-title"><div><h1>PDA 设备管理</h1><p class="muted">通过短码核对本仓库设备，管理登记身份及联网使用状态。</p></div>
      <div class="heading-actions"><a v-if="canManage" href="/pda-download" target="_blank" rel="noopener noreferrer">下载 PDA 应用</a>
      <el-button v-if="canManage" :disabled="busy || !!error || !!pending || !!storageError || pairingSending" @click="dialog = { device: null, pending: null }">高级公钥登记</el-button></div></div>
    <el-alert v-if="!canManage" title="当前账号没有设备管理权限，请联系仓库管理员。" type="warning" :closable="false" />
    <template v-else>
      <DevicePairingPanel v-if="session.identity && session.warehouseId" :key="`${session.identity.id}:${session.warehouseId}`"
        :user-id="session.identity.id" :warehouse-id="session.warehouseId" :warehouse-name="warehouseName" @sending="pairingSending = $event" @changed="paging.reload" />
      <el-alert v-if="storageError" :title="storageError" type="error" :closable="false" />
      <el-alert v-if="receipt" :title="`设备操作已确认（${receipt.status === 'ACTIVE' ? '启用' : '停用'}，回执版本${receipt.rowVersion}）。下方列表显示最新状态。`" type="success" :closable="false" />
      <section v-if="pending" class="panel"><div class="section-heading"><h2>有一项设备请求待核验</h2>
        <el-button type="warning" :disabled="busy" @click="dialog = { device: null, pending }">恢复原设备请求</el-button></div>
        <p class="muted">原内容已保存在本标签页。核验完成前暂停新设备操作；不会自动再次发送。关闭整个标签页会失去这里的恢复入口。</p></section>
      <section class="panel"><div class="section-heading"><h2>{{ warehouseName }} · 已登记设备</h2><el-button :loading="busy" @click="paging.reload">刷新设备列表</el-button></div>
        <p class="muted">最近联网时间不代表设备当前在线。停用不释放订单，离线设备也不能即时收到停用通知。</p>
        <QueryFeedback :busy="busy" :error="error" :count="items.length" empty="本仓库尚无已登记设备" @retry="paging.retry" />
        <el-table v-if="items.length" :data="items" v-loading="busy" row-key="id">
          <el-table-column prop="deviceCode" label="设备编号" min-width="150" />
          <el-table-column prop="name" label="名称" min-width="170" />
          <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '已启用' : '已停用' }}</el-tag></template></el-table-column>
          <el-table-column label="最近联网" min-width="180"><template #default="{ row }">{{ row.lastSeenAt ? localTime(row.lastSeenAt) : '尚无联网记录' }}</template></el-table-column>
          <el-table-column label="操作" width="200"><template #default="{ row }"><el-button link type="primary" @click="identity = row">查看身份</el-button>
            <el-button link :type="row.status === 'ACTIVE' ? 'danger' : 'primary'" :disabled="busy || !!error || !!pending || !!storageError" @click="dialog = { device: row, pending: null }">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button></template></el-table-column>
        </el-table>
        <CursorPager :page="page" :size="50" :busy="busy" :has-next="!!nextCursor" label="设备列表分页" @first="paging.first" @previous="paging.previous" @next="paging.next" />
      </section>
    </template>
    <DeviceCommandDialog v-if="dialog && session.identity" :warehouse-id="session.warehouseId" :warehouse-name="warehouseName" :user-id="session.identity.id"
      :device="dialog.device" :pending="dialog.pending" @close="close" @saved="saved" @sending="sending = $event" />
    <DeviceIdentityDialog v-if="identity" :device="identity" @close="identity = null" />
  </WorkspaceLayout>
</template>
