<script setup lang="ts">
import { computed, nextTick, onScopeDispose, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Refresh } from '@element-plus/icons-vue';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import QueryFeedback from '../components/QueryFeedback.vue';
import { useSessionStore } from '../session';
import { errorText, request } from '../api';
import { optionsSchema, pageSchema, userSchema, type ManagedUser, type PendingUserRequest, type UserOptions, type UserMode, type UserReceipt } from './contracts';
import { loadUserRequest } from './pending';
import UserCommandDialog from './UserCommandDialog.vue';
import UserHistory from './UserHistory.vue';
const session = useSessionStore(); const route = useRoute(); const router = useRouter();
const rows = ref<ManagedUser[]>([]); const total = ref(0); const page = ref(1); const pageSize = ref(20);
const loaded = ref(false);
const busy = ref(false); const error = ref(''); const storageError = ref(''); const success = ref(''); const sending = ref(false);
const options = ref<UserOptions>({ roles: [], warehouses: [] }); const username = ref(''); const status = ref('');
const pending = ref<PendingUserRequest | null>(null);
const dialog = ref<{ mode: UserMode; item: ManagedUser | null; pending: PendingUserRequest | null } | null>(null);
const history = ref<ManagedUser | null>(null); let generation = 0;
let dialogSource: HTMLElement | null = null;
const canManage = computed(() => session.identity?.permissions.includes('user:manage'));
const owner = () => ({ userId: session.identity?.id ?? '', warehouseId: session.warehouseId });
function restore(): void {
  storageError.value = ''; pending.value = null;
  if (!session.identity) return;
  try { pending.value = loadUserRequest(owner()); }
  catch { storageError.value = '原人员请求记录无法读取，已暂停新操作。请保留本标签页并联系管理员核对。'; }
}
async function load(): Promise<void> {
  const run = ++generation; if (!canManage.value || !session.warehouseId) { busy.value = false; return; }
  busy.value = true; error.value = '';
  const params = new URLSearchParams({ warehouseId: session.warehouseId, username: String(route.query.username ?? ''),
    status: String(route.query.status ?? ''), page: String(route.query.page ?? 1), pageSize: String(route.query.pageSize ?? 20) });
  username.value = params.get('username')!; status.value = params.get('status')!;
  try {
    const [result, available] = await Promise.all([request(`/users?${params}`, pageSchema), request(`/users/options?warehouseId=${session.warehouseId}`, optionsSchema)]);
    if (run !== generation) return;
    rows.value = result.items; total.value = result.total; page.value = result.page; pageSize.value = result.pageSize; options.value = available; loaded.value = true;
    if (String(result.page) !== params.get('page')) await router.replace({ query: { ...route.query, page: String(result.page) } });
  } catch (cause) { if (run === generation) error.value = errorText(cause); }
  finally { if (run === generation) busy.value = false; }
}
function query(next = 1, size = pageSize.value): void {
  const value = { warehouse: session.warehouseId, username: username.value.trim() || undefined, status: status.value || undefined, page: String(next), pageSize: String(size) };
  if (router.resolve({ query: value }).fullPath === route.fullPath) void load();
  else void router.push({ query: value });
}
function paginate(next: number, size = pageSize.value): void {
  void router.push({ query: { ...route.query, page: String(next), pageSize: String(size) } });
}
async function open(mode: UserMode, item: ManagedUser | null): Promise<void> {
  if (busy.value || pending.value || storageError.value) return;
  dialogSource = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  if (!item) { dialog.value = { mode, item, pending: null }; return; }
  const run = ++generation; busy.value = true; error.value = '';
  try {
    const latest = await request(`/users/${item.id}?warehouseId=${session.warehouseId}`, userSchema);
    if (run === generation) dialog.value = { mode, item: latest, pending: null };
  } catch (cause) { if (run === generation) error.value = errorText(cause); }
  finally { if (run === generation) busy.value = false; }
}
function close(): void {
  dialog.value = null; restore();
  void nextTick(() => { if (dialogSource?.isConnected) dialogSource.focus({ preventScroll: true }); });
}
function recover(): void {
  if (!pending.value) return;
  dialogSource = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  dialog.value = { mode: pending.value.kind, item: null, pending: pending.value };
}
function saved(value: UserReceipt): void { close(); success.value = value.action === 'CREATE' ? '人员已新增。' : '人员操作已保存，相关人员须重新登录；原作业与占用保留。'; void load(); }
function roleNames(user: ManagedUser): string { return user.roles.map(code => options.value.roles.find(r => r.code === code)?.name ?? code).join('、'); }
watch(() => [session.warehouseId, session.identity?.id], () => { rows.value = []; total.value = 0; loaded.value = false; dialog.value = null; history.value = null; success.value = ''; restore(); });
watch(() => [route.fullPath, session.warehouseId, session.identity?.id, canManage.value], load, { immediate: true });
restore(); onScopeDispose(() => { generation++; });
</script>
<template>
  <WorkspaceLayout title="人员与权限" list :busy="sending || !!dialog">
    <div class="page-title"><h1>人员与权限</h1><el-button v-if="canManage" type="primary" :disabled="busy || !!error || !!pending || !!storageError" @click="open('CREATE', null)">新增人员</el-button></div>
    <el-alert v-if="success" :title="success" type="success" closable @close="success = ''" />
    <el-alert v-if="storageError" :title="storageError" type="error" :closable="false" />
    <div v-if="pending" class="section-heading"><span>有一项人员操作结果待核对</span><el-button type="warning" @click="recover">恢复原人员请求</el-button></div>
    <section class="panel auxiliary-list">
      <form class="user-filter" @submit.prevent="query()">
        <label>登录账号<el-input v-model="username" aria-label="查询登录账号" placeholder="完整账号" maxlength="64" clearable /></label>
        <label>账号状态<el-select v-model="status" aria-label="查询账号状态" placeholder="全部状态"><el-option value="" label="全部状态" /><el-option value="ACTIVE" label="已启用" /><el-option value="DISABLED" label="已停用" /></el-select></label>
        <el-button type="primary" native-type="submit" :loading="busy">查询</el-button>
        <el-tooltip content="刷新人员列表"><el-button :icon="Refresh" aria-label="刷新人员列表" :disabled="busy" @click="load" /></el-tooltip>
      </form>
      <p class="muted">当前仓库内、全部授权范围均由您管理的人员</p>
      <QueryFeedback :busy="busy" :error="error" :count="rows.length" empty="没有符合条件的人员" @retry="load" />
      <div v-if="rows.length" class="auxiliary-table"><el-table :data="rows" v-loading="busy" row-key="id" height="100%">
        <el-table-column prop="username" label="登录账号" min-width="160" /><el-table-column prop="displayName" label="人员姓名" min-width="130" />
        <el-table-column label="角色" min-width="160"><template #default="{ row }">{{ roleNames(row) }}</template></el-table-column>
        <el-table-column label="仓库" min-width="160"><template #default="{ row }">{{ row.warehouses.map((w: { name: string }) => w.name).join('、') }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '已启用' : '已停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="230"><template #default="{ row }">
          <el-button link type="primary" :disabled="busy || !!error || !!pending || !!storageError || row.id === session.identity?.id" @click="open('UPDATE', row)">授权与启停</el-button>
          <el-button link :disabled="busy || !!error || !!pending || !!storageError" @click="open('PASSWORD', row)">重置密码</el-button>
          <el-button link @click="history = row">记录</el-button>
        </template></el-table-column>
      </el-table></div>
      <nav v-if="loaded" class="pagination" aria-label="人员列表分页"><span class="muted">共 {{ total }} 人 · 第 {{ page }} 页</span>
        <el-select :model-value="pageSize" aria-label="每页人数" class="user-page-size" :disabled="busy" @update:model-value="paginate(1, $event)"><el-option v-for="size in [20, 50, 100]" :key="size" :value="size" :label="`${size} 人/页`" /></el-select>
        <el-pagination :current-page="page" :page-size="pageSize" :total="total" :pager-count="5" layout="prev, pager, next" :disabled="busy" @current-change="paginate($event)" />
      </nav>
    </section>
    <UserCommandDialog v-if="dialog && session.identity" :user-id="session.identity.id" :warehouse-id="session.warehouseId" :options="options" v-bind="dialog" @close="close" @saved="saved" @sending="sending = $event" />
    <el-drawer :model-value="!!history" :title="`${history?.displayName ?? ''} · 操作记录`" size="640px" class="auxiliary-drawer" @close="history = null"><UserHistory v-if="history" :user-id="history.id" :warehouse-id="session.warehouseId" /></el-drawer>
  </WorkspaceLayout>
</template>
