<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import PageHeading from '../components/PageHeading.vue';
import FeedbackState from '../components/FeedbackState.vue';
import { useSessionStore } from '../session';
import { errorText } from '../api';
import { casePage, caseContext } from './api';
import { stateLabel, requestedTime } from './contracts';
import type { CasePage, CaseContext } from './contracts';
import HandoverDetails from './HandoverDetails.vue';

const route = useRoute(); const router = useRouter(); const session = useSessionStore();
const page = ref<CasePage | null>(null); const detail = ref<CaseContext | null>(null);
const busy = ref(false); const error = ref(''); const detailError = ref('');
const permitted = computed(() => session.identity?.permissions.some(p => p === 'order:handover' || p === 'order:resume'));
const cursor = computed(() => typeof route.query.handoverCursor === 'string' ? route.query.handoverCursor : '');
const order = computed(() => typeof route.params.id === 'string' ? route.params.id : '');
const selected = computed(() => typeof route.params.caseId === 'string' ? route.params.caseId : '');
let active: AbortController | undefined;

async function load(): Promise<void> {
  active?.abort(); const current = new AbortController(); active = current;
  page.value = null; detail.value = null; error.value = ''; detailError.value = ''; busy.value = false;
  if (!permitted.value || !session.warehouseId || !order.value) return;
  busy.value = true;
  try {
    const loaded = await casePage(session.warehouseId, order.value, cursor.value, current.signal);
    if (current !== active || current.signal.aborted) return;
    page.value = loaded;
    if (selected.value) await loadDetail(current);
  } catch (cause) { if (current === active && !current.signal.aborted) error.value = errorText(cause); }
  finally { if (current === active) busy.value = false; }
}
async function loadDetail(current: AbortController): Promise<void> {
  try {
    const loaded = await caseContext(session.warehouseId, order.value, selected.value, current.signal);
    if (current === active && !current.signal.aborted) detail.value = loaded;
  } catch (cause) { if (current === active && !current.signal.aborted) detailError.value = errorText(cause); }
}
function navigate(caseId = '', position = cursor.value): void {
  void router.push({ path: `/orders/${order.value}/handovers${caseId ? '/' + caseId : ''}`,
    query: { ...route.query, warehouse: session.warehouseId, handoverCursor: position || undefined } });
}
function back(): void {
  if (selected.value) navigate();
  else void router.push({ path: `/orders/${order.value}`, query: { ...route.query, handoverCursor: undefined } });
}
watch(() => [session.identity?.id, session.warehouseId, permitted.value, order.value, cursor.value, selected.value],
  () => { void load(); }, { immediate: true });
onBeforeUnmount(() => { active?.abort(); active = undefined; });
</script>
<template>
  <WorkspaceLayout title="换机交接">
    <PageHeading :title="page ? `${page.orderNo} · 换机交接` : '换机交接'" back
      :back-label="selected ? '返回交接列表' : '返回订单'" @back="back">
      <el-button :disabled="busy || !permitted" @click="load">刷新交接记录</el-button>
    </PageHeading>
    <FeedbackState v-if="!permitted" title="没有交接查询权限" description="请返回订单或联系管理员核对权限。" />
    <FeedbackState v-else-if="!page" :loading="busy" :error="!!error" :title="busy ? '正在读取交接记录' : '暂时无法查看交接记录'"
      :description="error" :action="busy ? undefined : '重新查询'" @action="load" />
    <template v-else-if="selected">
      <HandoverDetails v-if="detail" :detail="detail" />
      <FeedbackState v-else :loading="busy" :error="!!detailError" title="交接详情待核对" :description="detailError"
        :action="busy ? undefined : '重新查询'" @action="load" />
    </template>
    <template v-else>
      <p class="muted">按申请时间由近到远显示本订单的交接记录。</p>
      <FeedbackState v-if="!page.items.length" title="本页没有交接记录" description="可返回首页或订单核对。" />
      <el-table v-else :data="page.items" row-key="caseId" aria-label="交接案件列表">
        <el-table-column label="申请时间" min-width="175"><template #default="{ row }">{{ requestedTime(row.requestedAt) }}</template></el-table-column>
        <el-table-column label="作业轮次" min-width="110"><template #default="{ row }">{{ row.fromWorkRound }} → {{ row.toWorkRound }}</template></el-table-column>
        <el-table-column label="处理状态" min-width="165"><template #default="{ row }">{{ stateLabel(row.status) }}</template></el-table-column>
        <el-table-column label="操作" width="110"><template #default="{ row }"><el-button link type="primary" @click="navigate(row.caseId)">查看交接</el-button></template></el-table-column>
      </el-table>
      <div class="result-summary"><span>本页 {{ page.items.length }} 条{{ page.hasMore ? '，还有后续记录' : '，已到末页' }}</span>
        <div><el-button :disabled="!cursor || busy" @click="navigate('', '')">返回首页</el-button>
          <el-button :disabled="!page.hasMore || busy" @click="navigate('', page.nextCursor ?? '')">下一页</el-button></div>
      </div>
    </template>
  </WorkspaceLayout>
</template>
