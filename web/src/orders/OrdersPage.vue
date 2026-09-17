<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { Search, Refresh, Plus } from '@element-plus/icons-vue';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import PageHeading from '../components/PageHeading.vue';
import FeedbackState from '../components/FeedbackState.vue';
import { useSessionStore } from '../session';
import { orderStatusLabels, localTime, countLabel } from './contracts';
import OrderStatus from './OrderStatus.vue';
import OrderEditor from './OrderEditor.vue';
import { useOrderList } from './useOrderList';
import { listPosition, orderRouteQuery, saveListPosition } from './list-context';
const session = useSessionStore(); const router = useRouter();
const { rows, total, loaded, busy, error, notice, receivedAt, query, applied, key, load, change } = useOrderList();
const search = ref(query.value.search); const status = ref(query.value.status); const jump = ref(query.value.page);
const editor = ref(false); const root = ref<HTMLElement>();
const canWrite = computed(() => session.identity?.permissions.includes('order:write'));
const pages = computed(() => Math.max(1, Math.ceil(total.value / query.value.pageSize)));
const filtered = computed(() => !!query.value.search || !!query.value.status);
watch(query, value => { search.value = value.search; status.value = value.status; jump.value = value.page; });
function submit(): void { change({ search: search.value.trim(), status: status.value, page: 1 }); }
function reset(): void { change({ search: '', status: '', page: 1 }); }
function remember(id: string): void {
  saveListPosition(key.value, { table: root.value?.querySelector('.el-scrollbar__wrap')?.scrollTop ?? 0,
    page: document.querySelector('.page-scroll')?.scrollTop ?? 0, focus: id });
}
async function restore(): Promise<void> {
  const restoring = key.value;
  await nextTick(); await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
  if (key.value !== restoring) return;
  const position = listPosition(restoring); if (!position) return;
  const table = root.value?.querySelector('.el-scrollbar__wrap');
  const page = document.querySelector('.page-scroll');
  if (table) table.scrollTop = position.table;
  if (page) page.scrollTop = position.page;
  const links = root.value?.querySelectorAll<HTMLElement>('[data-order-id]');
  const link = [...(links ?? [])].find(item => item.dataset.orderId === position.focus && item.offsetParent);
  link?.focus({ preventScroll: true });
}
watch(receivedAt, () => { void restore(); });
function saved(id: string): void {
  editor.value = false;
  void router.push({ path: `/orders/${id}`, query: orderRouteQuery(query.value, session.warehouseId) });
}
</script>
<template>
  <WorkspaceLayout title="装车订单" :busy="editor" list>
    <PageHeading title="装车订单"><el-button v-if="canWrite" type="primary" :icon="Plus" @click="editor = true">新建订单</el-button></PageHeading>
    <form class="order-filters" @submit.prevent="submit">
      <label>订单号或车号<el-input v-model="search" aria-label="订单或车号" placeholder="输入完整订单号或车号" maxlength="40" clearable /></label>
      <label>订单状态<el-select v-model="status" aria-label="订单状态" placeholder="全部状态"><el-option label="全部状态" value="" /><el-option v-for="(label, value) in orderStatusLabels" :key="value" :label="label" :value="value" /></el-select></label>
      <div class="filter-actions"><el-button native-type="submit" type="primary" :icon="Search" :loading="busy">查询</el-button><el-button :disabled="busy" @click="reset">重置</el-button><el-tooltip content="刷新当前页"><el-button :icon="Refresh" aria-label="刷新当前页" :disabled="busy" @click="load" /></el-tooltip></div>
    </form>
    <el-alert v-if="notice" :title="notice" type="info" :closable="false" />
    <el-alert v-if="error && loaded" :title="`查询失败：${error} 下方保留上次成功查询的结果。`" type="error" :closable="false"><el-button text @click="load">重试查询</el-button></el-alert>
    <FeedbackState v-if="!loaded" :title="busy ? '正在查询订单' : '订单查询失败'" :description="error" :loading="busy" :error="!busy" :action="busy ? undefined : '重试查询'" @action="load" />
    <template v-else>
      <div class="result-summary" role="status"><span>共 {{ total }} 单<span v-if="applied.search || applied.status || error"> · {{ error ? '上次查询' : '已查询' }}：{{ applied.search || '全部订单' }} / {{ orderStatusLabels[applied.status] || '全部状态' }} / 第 {{ applied.page }} 页</span></span><span>数据读取于 {{ localTime(receivedAt) }}</span></div>
      <FeedbackState v-if="!rows.length" :title="filtered ? '没有符合条件的订单' : '还没有订单'" :action="filtered ? '清除筛选' : undefined" @action="reset" />
      <div v-else ref="root" class="order-results" :aria-busy="busy">
        <div class="orders-grid"><el-table v-loading="busy" :data="rows" height="100%" row-key="id">
          <el-table-column label="订单号" min-width="215"><template #default="{ row }"><router-link :data-order-id="row.id" :to="{ path: `/orders/${row.id}`, query: orderRouteQuery(query, session.warehouseId) }" class="order-link" @click="remember(row.id)">{{ row.orderNo }}</router-link></template></el-table-column>
          <el-table-column label="车号 / 柜号" min-width="150"><template #default="{ row }">{{ row.vehicleNo }}<p v-if="row.containerNo" class="muted">{{ row.containerNo }}</p></template></el-table-column>
          <el-table-column label="状态" min-width="190"><template #default="{ row }"><OrderStatus :status="row.status" /></template></el-table-column>
          <el-table-column label="计划包数" width="105" align="right"><template #default="{ row }"><span class="numeric">{{ countLabel(row.plannedTotal) }}</span></template></el-table-column>
          <el-table-column label="版本" width="75"><template #default="{ row }">V{{ row.revision }}</template></el-table-column>
          <el-table-column label="创建信息" min-width="175"><template #default="{ row }">{{ row.createdBy }}<p class="muted">{{ localTime(row.createdAt) }}</p></template></el-table-column>
        </el-table></div>
        <div class="order-mobile-list"><article v-for="row in rows" :key="row.id" class="order-mobile-row">
          <router-link :data-order-id="row.id" :to="{ path: `/orders/${row.id}`, query: orderRouteQuery(query, session.warehouseId) }" class="order-link" @click="remember(row.id)">{{ row.orderNo }}</router-link>
          <div><span>{{ row.vehicleNo }}</span><OrderStatus :status="row.status" /></div><div><span>计划 {{ row.plannedTotal }} 包 · V{{ row.revision }}</span><span>{{ row.containerNo || '未填写柜号' }}</span></div><span class="muted">{{ localTime(row.createdAt) }}</span>
        </article></div>
      </div>
      <div class="order-pagination">
        <label class="page-size">每页<el-select :model-value="query.pageSize" aria-label="每页条数" :disabled="busy" @update:model-value="change({ pageSize: $event, page: 1 })"><el-option v-for="size in [20, 50, 100]" :key="size" :label="`${size} 条`" :value="size" /></el-select></label>
        <el-pagination :current-page="query.page" :page-size="query.pageSize" :total="total" :pager-count="5" :disabled="busy" layout="prev, pager, next" @current-change="change({ page: $event })" />
        <form class="page-jump" @submit.prevent="change({ page: jump })"><span>第 {{ query.page }} / {{ pages }} 页</span><el-input-number v-model="jump" aria-label="跳转页码" :min="1" :max="pages" :controls="false" :precision="0" :disabled="busy" /><el-button native-type="submit" :disabled="busy">跳转</el-button></form>
      </div>
    </template>
    <OrderEditor v-if="editor" :warehouse-id="session.warehouseId" :detail="null" @saved="saved" @cancel="editor = false" />
  </WorkspaceLayout>
</template>
