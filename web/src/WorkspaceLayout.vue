<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import { Fold, Expand, Menu, User } from '@element-plus/icons-vue';
import { useSessionStore } from './session';
import { errorText } from './api';
import WorkspaceNavigation from './components/WorkspaceNavigation.vue';
import ConnectionStatus from './components/ConnectionStatus.vue';
import FeedbackState from './components/FeedbackState.vue';
import { navigation, navigationCollapsed } from './navigation';
import { connection } from './connection';
import SessionRecovery from './components/SessionRecovery.vue';
import { orderRouteQuery, parseOrderQuery } from './orders/list-context';
const props = defineProps<{ title: string; busy?: boolean; list?: boolean; warehouseLocked?: boolean }>();
const session = useSessionStore(); const route = useRoute(); const router = useRouter();
const error = ref(''); const drawer = ref(false); const collapsed = navigationCollapsed;
const current = computed(() => navigation.flatMap(group => group.items).find(item => route.path.startsWith(item.path)));
const authorized = computed(() => !current.value || session.identity?.permissions.includes(current.value.permission));
const environment = import.meta.env.VITE_ENVIRONMENT_NAME?.trim() || (import.meta.env.DEV ? '开发环境' : '');
const parentLink = computed(() => ({ path: current.value?.path ?? '/orders', query: current.value?.path === '/orders'
  ? orderRouteQuery(parseOrderQuery(route.query), session.warehouseId) : {} }));
function resize(): void { if (window.innerWidth >= 1024) drawer.value = false; }
function beforeUnload(event: BeforeUnloadEvent): void { if (props.busy) { event.preventDefault(); event.returnValue = ''; } }
onMounted(() => { window.addEventListener('resize', resize); window.addEventListener('beforeunload', beforeUnload); });
onUnmounted(() => { window.removeEventListener('resize', resize); window.removeEventListener('beforeunload', beforeUnload); });
onBeforeRouteLeave(() => { if (!props.busy) return true; error.value = '请先完成当前操作或核对原请求结果。'; return false; });
function changeWarehouse(value: string): void {
  if (props.busy || props.warehouseLocked) return;
  session.warehouseId = value;
  if (route.path.startsWith('/orders')) void router.push({ path: '/orders', query: { ...route.query, warehouse: value, page: '1', tab: undefined } });
  else void router.replace({ query: { ...route.query, warehouse: value, import: undefined, cursor: undefined, ...(route.path === '/users' ? { page: '1' } : {}) } });
}
async function logout(): Promise<void> {
  if (props.busy) return;
  try { await session.logout(); await router.replace('/login'); }
  catch (cause) { error.value = errorText(cause); }
}
async function reconnect(): Promise<void> {
  try { await session.load(); await router.replace(route.fullPath); error.value = ''; }
  catch (cause) { error.value = errorText(cause); }
}
</script>
<template>
  <div class="workspace" :class="{ 'is-collapsed': collapsed, 'is-list': list }">
    <aside class="sidebar"><div class="sidebar-brand"><span class="brand-mark">纤</span><span class="brand-name">腈纶出库</span></div>
      <WorkspaceNavigation :collapsed="collapsed" :busy="busy" />
    </aside>
    <div class="workspace-body">
      <header class="topbar">
        <div class="topbar-location">
          <el-tooltip :content="collapsed ? '展开导航' : '折叠导航'"><el-button class="icon-button desktop-navigation" :icon="collapsed ? Expand : Fold" :aria-label="collapsed ? '展开导航' : '折叠导航'" @click="collapsed = !collapsed" /></el-tooltip>
          <el-button class="icon-button mobile-navigation" :icon="Menu" aria-label="打开导航" @click="drawer = true" />
          <nav class="breadcrumb" aria-label="当前位置"><router-link v-if="current && route.path !== current.path" :to="parentLink">{{ current.label }}</router-link><span v-if="current && route.path !== current.path"> / </span>{{ title }}</nav>
        </div>
        <div class="topbar-actions">
          <span class="warehouse-caption">仓库</span><el-select :model-value="session.warehouseId" aria-label="当前仓库" class="warehouse-select" :disabled="busy || warehouseLocked" @update:model-value="changeWarehouse">
            <el-option v-for="warehouse in session.identity?.warehouses" :key="warehouse.id" :value="warehouse.id" :label="warehouse.name" />
          </el-select>
          <el-tag v-if="environment" class="environment-tag" type="info">{{ environment }}</el-tag><ConnectionStatus />
          <el-dropdown trigger="click" :disabled="busy" @command="logout">
            <el-button text :icon="User" class="account-trigger" :disabled="busy" aria-label="账户菜单">{{ session.identity?.displayName }}</el-button>
            <template #dropdown><el-dropdown-menu><el-dropdown-item command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </header>
      <div class="page-scroll"><main class="page-content">
        <el-alert v-if="error" :title="error" type="error" :closable="false" />
        <el-alert v-if="connection.state === 'expired'" title="登录已失效，请重新登录后核对原操作结果。" type="warning" :closable="false" />
        <FeedbackState v-if="!session.identity" title="后台暂时无法连接" description="尚未取得账号和仓库信息，请重新连接。" error action="重新连接" @action="reconnect" />
        <FeedbackState v-else-if="!session.warehouseId" title="尚未分配仓库" description="请联系管理员分配可访问的仓库。" />
        <FeedbackState v-else-if="!authorized" title="没有此页面的访问权限" description="请从导航选择有权限的页面，或联系管理员。" />
        <slot v-else />
      </main></div>
    </div>
    <el-drawer v-model="drawer" title="腈纶出库" direction="ltr" size="min(280px, 88vw)" class="navigation-drawer">
      <WorkspaceNavigation :busy="busy" @navigate="drawer = false" />
    </el-drawer>
    <SessionRecovery />
  </div>
</template>
