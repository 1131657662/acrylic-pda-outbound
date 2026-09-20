<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import { useSessionStore } from '../session';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import PageHeading from '../components/PageHeading.vue';
import FeedbackState from '../components/FeedbackState.vue';
import { localTime } from '../orders/contracts';
import { listPosition, saveListPosition } from '../orders/list-context';
import { useReminders } from './useReminders';
import type { Reminder } from './contracts';
const session = useSessionStore(); const root = ref<HTMLElement>();
const { rows, next, busy, loaded, error, receivedAt, cursor, key, previous, go, load } = useReminders();
function destination(row: Reminder) {
  return { path: row.sourceType === 'TEMP' ? `/temporary-reviews/${row.submissionId}` : `/orders/${row.orderId}`, query: { warehouse: session.warehouseId, tab: 'records',
    from: 'review-reminders', reminderCursor: cursor.value || undefined, reminderWarehouse: session.warehouseId } };
}
function remember(id: string): void {
  saveListPosition(`reminder:${key.value}`, { focus: id, table: root.value?.querySelector('.el-scrollbar__wrap')?.scrollTop ?? 0,
    page: document.querySelector('.page-scroll')?.scrollTop ?? 0 });
}
watch(receivedAt, async () => {
  const current = key.value; await nextTick(); await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
  if (current !== key.value) return;
  const position = listPosition(`reminder:${current}`); if (!position) return;
  const table = root.value?.querySelector('.el-scrollbar__wrap'); const page = document.querySelector('.page-scroll');
  if (table) table.scrollTop = position.table;
  if (page) page.scrollTop = position.page;
  [...(root.value?.querySelectorAll<HTMLElement>('[data-order-id]') ?? [])]
    .find(item => item.dataset.orderId === position.focus && item.offsetParent)?.focus({ preventScroll: true });
});
</script>
<template>
  <WorkspaceLayout title="超时待审" list>
    <PageHeading title="超时待审" subtitle="待审超过30分钟后提醒，请核对当前批次再审批。">
      <el-button :loading="busy" @click="load">刷新待办</el-button>
    </PageHeading>
    <p class="muted">系统每分钟检查，已处理批次在刷新后移出。提醒不会自动批准、出库或释放占用。</p>
    <el-alert v-if="error && loaded" :title="`${error} 下方为上次读取结果，请刷新后核对。`" type="error" :closable="false"><el-button text @click="load">重试查询</el-button></el-alert>
    <FeedbackState v-if="!loaded" :title="busy ? '正在读取待办' : '待办读取失败'" :description="error" :loading="busy" :error="!busy" :action="busy ? undefined : '重试查询'" @action="load" />
    <template v-else>
      <div class="result-summary" role="status"><span>本页 {{ rows.length }} 条 · 按提醒时间从新到旧</span><span>数据读取于 {{ localTime(receivedAt) }}</span></div>
      <FeedbackState v-if="!rows.length" :title="cursor ? '本页待办已处理或已变化' : '暂无超时待审提醒'" description="可刷新查询最新待办；历史提醒仍保留在订单操作记录中。" />
      <div v-else ref="root" class="order-results" :aria-busy="busy">
        <div class="orders-grid"><el-table v-loading="busy" :data="rows" height="100%" row-key="id">
          <el-table-column label="订单号" min-width="300"><template #default="{ row }"><router-link :data-order-id="row.orderId" :to="destination(row)" class="order-link" @click="remember(row.orderId)">{{ row.orderNo }}</router-link></template></el-table-column>
          <el-table-column label="来源" width="100"><template #default="{ row }">{{ row.sourceType === 'TEMP' ? '临时单' : '正式单' }}</template></el-table-column>
          <el-table-column prop="vehicleNo" label="车号" min-width="130" />
          <el-table-column label="后台接收时间" min-width="175"><template #default="{ row }">{{ localTime(row.submittedAt) }}</template></el-table-column>
          <el-table-column label="提醒时间" min-width="175"><template #default="{ row }">{{ localTime(row.remindedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="130"><template #default="{ row }"><router-link :to="destination(row)" @click="remember(row.orderId)">{{ row.sourceType === 'TEMP' ? '审阅临时单' : '核对并审批' }}</router-link></template></el-table-column>
        </el-table></div>
        <div class="order-mobile-list"><article v-for="row in rows" :key="row.id" class="order-mobile-row">
          <router-link :data-order-id="row.orderId" :to="destination(row)" class="order-link" @click="remember(row.orderId)">{{ row.orderNo }}</router-link>
          <div><span>{{ row.vehicleNo }}</span><span>{{ row.sourceType === 'TEMP' ? '临时待确认' : '待主管处理' }}</span></div>
          <p class="muted">后台接收：{{ localTime(row.submittedAt) }}<br />提醒：{{ localTime(row.remindedAt) }}</p>
          <router-link :to="destination(row)" @click="remember(row.orderId)">{{ row.sourceType === 'TEMP' ? '审阅临时单' : '核对并审批' }}</router-link>
        </article></div>
      </div>
    </template>
    <nav class="order-pagination" aria-label="待办分页">
      <el-button :disabled="busy || !cursor" @click="go('')">返回首页</el-button>
      <div><el-button :disabled="busy || previous === undefined" @click="go(previous ?? '')">上一页</el-button><el-button :disabled="busy || !!error || !next" @click="go(next ?? '')">下一页</el-button></div>
    </nav>
  </WorkspaceLayout>
</template>
