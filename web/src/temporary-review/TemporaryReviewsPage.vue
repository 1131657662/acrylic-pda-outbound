<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import PageHeading from '../components/PageHeading.vue';
import FeedbackState from '../components/FeedbackState.vue';
import { useSessionStore } from '../session';
import { localTime } from '../orders/contracts';
import { kilograms } from '../orders/submission-contracts';
import { useTemporaryReviews } from './useTemporaryReviews';
import { listPosition, saveListPosition } from '../orders/list-context';
const session = useSessionStore(); const root = ref<HTMLElement>();
const { rows, next, busy, loaded, error, cursor, key, receivedAt, previous, go, load } = useTemporaryReviews();
function destination(id: string) {
  return { path: `/temporary-reviews/${id}`, query: { warehouse: session.warehouseId, listCursor: cursor.value || undefined } };
}
function remember(id: string): void {
  saveListPosition(`temporary:${key.value}`, { focus: id, table: root.value?.querySelector('.el-scrollbar__wrap')?.scrollTop ?? 0,
    page: document.querySelector('.page-scroll')?.scrollTop ?? 0 });
}
watch(receivedAt, async () => {
  const current = key.value; await nextTick(); await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
  if (current !== key.value) return;
  const position = listPosition(`temporary:${current}`); if (!position) return;
  const table = root.value?.querySelector('.el-scrollbar__wrap'); const page = document.querySelector('.page-scroll');
  if (table) table.scrollTop = position.table;
  if (page) page.scrollTop = position.page;
  [...(root.value?.querySelectorAll<HTMLElement>('[data-submission-id]') ?? [])]
    .find(item => item.dataset.submissionId === position.focus && item.offsetParent)?.focus({ preventScroll: true });
});
</script>
<template>
  <WorkspaceLayout title="临时待确认" list>
    <PageHeading title="临时待确认" subtitle="已完整接收的当前临时批次，仍不可放行。"><el-button :loading="busy" @click="load">刷新待办</el-button></PageHeading>
    <el-alert title="当前仅支持审阅。独立确认、短装审批及实物处置尚未开放。" type="warning" :closable="false" />
    <el-alert v-if="error && loaded" :title="`${error} 下方为上次读取结果。`" type="error" :closable="false" />
    <FeedbackState v-if="!loaded" :title="busy ? '正在读取待办' : '待办读取失败'" :description="error" :loading="busy" :error="!busy" :action="busy ? undefined : '重试查询'" @action="load" />
    <template v-else>
      <div class="result-summary" role="status"><span>本页 {{ rows.length }} 条 · 按完整接收时间从新到旧</span><span>读取于 {{ localTime(receivedAt) }}</span></div>
      <FeedbackState v-if="!rows.length" title="暂无当前待确认临时批次" description="可刷新查询最新结果，历史批次与原实物占用仍保留。" />
      <div v-else ref="root" class="order-results" :aria-busy="busy">
        <div class="orders-grid"><el-table :data="rows" height="100%" row-key="submissionId">
          <el-table-column label="订单号" min-width="250"><template #default="{ row }"><router-link :to="destination(row.submissionId)" :data-submission-id="row.submissionId" @click="remember(row.submissionId)">{{ row.orderNo }}</router-link></template></el-table-column>
          <el-table-column prop="vehicleNo" label="车号" min-width="130" />
          <el-table-column prop="expectedCount" label="收到有效包数" width="120" align="right" />
          <el-table-column label="收到净重 kg" width="140" align="right"><template #default="{ row }">{{ kilograms(row.expectedWeightGrams) }}</template></el-table-column>
          <el-table-column label="完整接收时间" min-width="180"><template #default="{ row }">{{ localTime(row.receivedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="110"><template #default="{ row }"><router-link :to="destination(row.submissionId)" :data-submission-id="row.submissionId" @click="remember(row.submissionId)">审阅资料</router-link></template></el-table-column>
        </el-table></div>
        <div class="order-mobile-list"><article v-for="row in rows" :key="row.submissionId" class="order-mobile-row">
          <router-link :to="destination(row.submissionId)" :data-submission-id="row.submissionId" @click="remember(row.submissionId)" class="order-link">{{ row.orderNo }}</router-link>
          <div><span>{{ row.vehicleNo }}</span><span>待确认 · 不可放行</span></div>
          <p>收到 {{ row.expectedCount }} 包 · {{ kilograms(row.expectedWeightGrams) }} kg</p>
          <p class="muted">{{ localTime(row.receivedAt) }}</p><router-link :to="destination(row.submissionId)" :data-submission-id="row.submissionId" @click="remember(row.submissionId)">审阅资料</router-link>
        </article></div>
      </div>
    </template>
    <nav class="order-pagination" aria-label="临时待办分页">
      <el-button :disabled="busy || !cursor" @click="go('')">返回首页</el-button>
      <div><el-button :disabled="busy || previous === undefined" @click="go(previous ?? '')">上一页</el-button><el-button :disabled="busy || !!error || !next" @click="go(next ?? '')">下一页</el-button></div>
    </nav>
  </WorkspaceLayout>
</template>
