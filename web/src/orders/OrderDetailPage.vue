<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { CopyDocument, Refresh } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import PageHeading from '../components/PageHeading.vue';
import FeedbackState from '../components/FeedbackState.vue';
import { useSessionStore } from '../session';
import { request, errorText, ApiError } from '../api';
import { detailSchema, preflightSchema, receiptSchema, localTime, countLabel } from './contracts';
import type { Detail, Preflight } from './contracts';
import { orderRouteQuery, parseOrderQuery } from './list-context';
import OrderEditor from './OrderEditor.vue';
import OrderHistory from './OrderHistory.vue';
import OrderPublishDialog from './OrderPublishDialog.vue';
import OrderPublications from './OrderPublications.vue';
import OrderSubmission from './OrderSubmission.vue';
import OrderRecall from './OrderRecall.vue';
import OrderStatus from './OrderStatus.vue';
import OrderPlan from './OrderPlan.vue';
import PcOrderLink from '../pc/PcOrderLink.vue';
import OrderExports from '../exports/OrderExports.vue';
const route = useRoute(); const router = useRouter(); const session = useSessionStore();
const detail = ref<Detail | null>(null); const preflight = ref<Preflight | null>(null); const busy = ref(false);
const error = ref(''); const checkError = ref(''); const receivedAt = ref('');
const editor = ref(false); const cancelReason = ref(''); const cancelDialog = ref(false); const uncertain = ref(false);
const publishDialog = ref(false); const recallBusy = ref(false); const submissionBusy = ref(false);
const pcChannel = ref(false);
const locked = computed(() => editor.value || cancelDialog.value || publishDialog.value || recallBusy.value || submissionBusy.value);
const tab = computed({ get: () => ['plan', 'records', 'exceptions', 'history', 'exports'].includes(String(route.query.tab)) ? String(route.query.tab) : 'plan',
  set: (value: string) => { void router.replace({ query: { ...route.query, ...orderRouteQuery(parseOrderQuery(route.query), session.warehouseId), tab: value } }); } });
let cancelBody = ''; let cancelKey = ''; let generation = 0;
const canWrite = computed(() => session.identity?.permissions.includes('order:write'));
const canCancel = computed(() => session.identity?.permissions.includes('order:cancel'));
const canPublish = computed(() => session.identity?.permissions.includes('order:publish'));
const unassigned = computed(() => detail.value && ['DRAFT', 'READY'].includes(detail.value.order.status));
async function check(id: string, params: URLSearchParams, current: number): Promise<void> {
  try { const result = await request(`/orders/${id}/preflight?${params}`, preflightSchema); if (current === generation) preflight.value = result; }
  catch (cause) { if (current === generation) checkError.value = errorText(cause); }
}
async function load(): Promise<void> {
  const current = ++generation;
  if (!session.warehouseId || typeof route.params.id !== 'string' || !session.identity?.permissions.includes('order:read')) return;
  busy.value = true; error.value = ''; checkError.value = ''; preflight.value = null;
  try {
    const params = new URLSearchParams({ warehouseId: session.warehouseId }); const id = route.params.id;
    const loaded = await request(`/orders/${id}?${params}`, detailSchema);
    if (current !== generation) return;
    detail.value = loaded; receivedAt.value = new Date().toISOString();
    if (loaded.order.status === 'DRAFT') await check(id, params, current);
  } catch (cause) {
    if (current === generation) {
      error.value = cause instanceof ApiError && cause.status === 404 ? '当前仓库中没有此订单，请返回列表重新查询。' : errorText(cause);
      if (cause instanceof ApiError && [403, 404].includes(cause.status)) detail.value = null;
    }
  } finally { if (current === generation) busy.value = false; }
}
async function cancel(): Promise<void> {
  if (busy.value || !detail.value || !cancelReason.value.trim()) return;
  if (!uncertain.value) {
    cancelBody = JSON.stringify({ expectedVersion: detail.value.order.rowVersion, reason: cancelReason.value });
    cancelKey = crypto.randomUUID();
  }
  busy.value = true; error.value = '';
  try {
    await request(`/orders/${detail.value.order.id}/cancel?warehouseId=${session.warehouseId}`, receiptSchema, { method: 'POST',
      body: cancelBody, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': cancelKey } });
    uncertain.value = false; cancelDialog.value = false; cancelReason.value = ''; ElMessage.success('订单已作废'); await load();
  } catch (cause) { uncertain.value = !(cause instanceof ApiError) || cause.status >= 500 || cause.status === 401; error.value = errorText(cause); }
  finally { busy.value = false; }
}
function saved(): void { editor.value = false; ElMessage.success('计划已保存'); void load(); }
function published(): void { publishDialog.value = false; ElMessage.success('订单已发布，等待领取'); void load(); }
async function copyOrder(): Promise<void> {
  if (!detail.value) return;
  try { await navigator.clipboard.writeText(detail.value.order.orderNo); ElMessage.success('订单号已复制'); }
  catch { ElMessage.error('复制失败，请选择订单号后复制。'); }
}
function back(): void {
  if (route.query.from === 'review-reminders') {
    void router.push({ path: '/review-reminders', query: { warehouse: session.warehouseId,
      cursor: route.query.reminderWarehouse === session.warehouseId && typeof route.query.reminderCursor === 'string' ? route.query.reminderCursor : undefined } });
  } else void router.push({ path: '/orders', query: orderRouteQuery(parseOrderQuery(route.query), session.warehouseId) });
}
watch(() => [session.warehouseId, route.params.id], () => { detail.value = null; preflight.value = null; void load(); }, { immediate: true });
onBeforeUnmount(() => { generation++; });
</script>
<template>
  <WorkspaceLayout title="订单详情" :busy="locked">
    <PageHeading :title="detail?.order.orderNo ?? '订单详情'" :subtitle="detail ? `车号：${detail.plan.vehicleNo}${detail.plan.containerNo ? ' · 柜号：' + detail.plan.containerNo : ''}` : undefined" back :back-label="route.query.from === 'review-reminders' ? '返回超时待审' : '返回订单列表'" @back="back">
      <template #status><template v-if="detail"><OrderStatus :status="detail.order.status" /><el-tooltip content="复制订单号"><el-button text :icon="CopyDocument" aria-label="复制订单号" @click="copyOrder" /></el-tooltip></template></template>
      <template v-if="detail">
        <PcOrderLink :key="detail.order.id" :order="detail.order.id" :warehouse="session.warehouseId" :version="detail.order.rowVersion" :status="detail.order.status" :disabled="busy || locked || !!error" @channel="pcChannel = $event" />
        <el-button v-if="canPublish && detail.order.status === 'DRAFT'" type="primary" :disabled="busy || locked || !!error || !preflight?.checksPassed" @click="publishDialog = true">发布订单</el-button>
        <el-button v-if="canWrite && unassigned && (detail.order.status === 'DRAFT' || canPublish)" :disabled="busy || locked || !!error" @click="editor = true">{{ detail.order.status === 'READY' ? '修订并重新发布' : '修订草稿' }}</el-button>
        <el-button v-if="canCancel && unassigned" type="danger" plain :disabled="busy || locked || !!error" @click="cancelDialog = true">作废订单</el-button>
        <el-tooltip content="刷新订单"><el-button :icon="Refresh" aria-label="刷新订单" :disabled="busy || locked" @click="load" /></el-tooltip>
      </template>
    </PageHeading>
    <el-alert v-if="error && detail" :title="`${error} 当前显示上次读取的订单资料。`" type="error" :closable="false" />
    <FeedbackState v-if="!detail" :loading="busy" :error="!!error" :title="busy ? '正在读取订单' : '暂时无法查看订单'" :description="error" :action="busy ? undefined : '重新查询'" @action="load" />
    <template v-else>
      <div class="detail-meta"><div><span>计划合计</span><strong class="numeric">{{ countLabel(detail.plan.plannedTotal) }} 包</strong></div><div><span>当前计划</span><strong>V{{ detail.plan.revision }}</strong></div><div><span>创建来源</span><strong>{{ detail.order.source === 'PDA_TEMP' ? 'PDA 临时单' : '管理工作台' }}</strong></div></div>
      <div class="result-summary"><span>{{ detail.order.createdBy }} · 创建于 {{ localTime(detail.order.createdAt) }}</span><span>数据读取于 {{ localTime(receivedAt) }}</span></div>
      <el-tabs v-model="tab" class="detail-tabs">
        <el-tab-pane v-if="detail.order.status === 'COMPLETED' && detail.order.source === 'WEB'" label="原确认文件" name="exports">
          <OrderExports v-if="tab === 'exports' && session.identity" :key="`${session.identity.id}-${session.warehouseId}-${detail.order.id}`" :actor="session.identity.id" :warehouse="session.warehouseId" :order="detail.order.id" />
        </el-tab-pane>
        <el-tab-pane label="产品计划" name="plan"><OrderPlan :detail="detail" :preflight="preflight" :check-error="checkError" @retry="load" /></el-tab-pane>
        <el-tab-pane label="装车记录" name="records"><p v-if="pcChannel">本单采用PC手工录入，请通过上方“查看PC记录与结果”或“继续PC录入”核对全部记录与结果。</p><OrderSubmission v-else :key="`submission-${detail.order.id}`" :version="detail.order.rowVersion" :warehouse-id="session.warehouseId" :order-id="detail.order.id" @updated="load" @busy="submissionBusy = $event" /></el-tab-pane>
        <el-tab-pane label="异常处理" name="exceptions"><template v-if="pcChannel"><p>需要换人、卸货释放或实物隔离时，请核对逐包处置方案、主管审批和本人交接记录。</p><el-button :disabled="busy || locked || !!error" @click="router.push({ path: `/orders/${detail.order.id}/pc-outbound/exception`, query: route.query })">查看PC异常处置与交接</el-button></template><OrderRecall v-else :key="`${session.warehouseId}-${detail.order.id}`" :warehouse-id="session.warehouseId" :order-id="detail.order.id" :row-version="detail.order.rowVersion" :status="detail.order.status" @updated="load" @busy="recallBusy = $event" /></el-tab-pane>
        <el-tab-pane label="操作记录" name="history">
          <OrderHistory :key="`${detail.order.id}-${detail.order.rowVersion}`" :warehouse-id="session.warehouseId" :order-id="detail.order.id" />
          <OrderPublications :key="`pub-${detail.order.id}-${detail.order.rowVersion}`" :warehouse-id="session.warehouseId" :order-id="detail.order.id" />
        </el-tab-pane>
      </el-tabs>
    </template>
    <OrderEditor v-if="editor && detail" :warehouse-id="session.warehouseId" :detail="detail" @saved="saved" @cancel="editor = false" />
    <OrderPublishDialog v-if="publishDialog && detail" :warehouse-id="session.warehouseId" :detail="detail" @saved="published" @cancel="publishDialog = false" />
    <el-dialog v-model="cancelDialog" title="作废未领取订单" width="480" :close-on-click-modal="false" :show-close="!busy && !uncertain" :close-on-press-escape="!busy && !uncertain">
      <p>订单 {{ detail?.order.orderNo }} · {{ detail?.plan.vehicleNo }}。作废后不能领取，原计划和操作记录保留。</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" /><el-alert v-if="uncertain" title="结果未确认，请重试原作废请求。" type="warning" :closable="false" />
      <el-input v-model="cancelReason" aria-label="作废原因" type="textarea" maxlength="500" placeholder="请填写作废原因" :disabled="busy || uncertain" />
      <template #footer><el-button :disabled="busy || uncertain" @click="cancelDialog = false">返回核对</el-button><el-button type="danger" :loading="busy" :disabled="!cancelReason.trim()" @click="cancel">{{ uncertain ? '重试原作废请求' : '确认作废' }}</el-button></template>
    </el-dialog>
  </WorkspaceLayout>
</template>
