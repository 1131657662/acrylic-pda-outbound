<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import PageHeading from '../components/PageHeading.vue';
import FeedbackState from '../components/FeedbackState.vue';
import { request, errorText } from '../api';
import { useSessionStore } from '../session';
import { connection } from '../connection';
import { detailSchema, type Detail } from '../orders/contracts';
import { currentPolicySchema, type Policy } from '../policies/contracts';
import { orderRouteQuery, parseOrderQuery } from '../orders/list-context';
import { viewSchema, workLabels, type PcView, type Candidate, type Entry } from './contracts';
import { usePcCommands } from './usePcCommands';
import { actionNames, type Action } from './pending';
import PcCandidates from './PcCandidates.vue';
import PcRecords from './PcRecords.vue';
import PcSummary from './PcSummary.vue';
import PcActionDialog, { type DialogKind } from './PcActionDialog.vue';
const props = defineProps<{ order: string; warehouse: string; user: string }>();
const session = useSessionStore(); const route = useRoute(); const router = useRouter(); const writer = crypto.randomUUID();
const detail = ref<Detail | null>(null); const view = ref<PcView | null>(null); const policy = ref<Policy | null>(null);
const loading = ref(false); const readError = ref(''); const dialog = ref<DialogKind | null>(null); const removing = ref<Entry | null>(null);
const tab = ref('candidates'); let generation = 0;
const { pending, busy, error, notice, storageError, send, recover, reload } = usePcCommands(props, load);
const canOperate = computed(() => session.identity?.permissions.includes('pc:operate'));
const mine = computed(() => view.value?.work?.operator === props.user);
const writing = computed(() => canOperate.value && mine.value && view.value?.work?.status === 'ACTIVE' && view.value.work.writerId === writer);
const blocked = computed(() => busy.value || !!pending.value || !!storageError.value || loading.value || !!readError.value || connection.state !== 'ok');
const canReview = computed(() => session.identity?.permissions.includes('submission:review') && view.value?.work?.status === 'REVIEW_REQUIRED');
const canResume = computed(() => session.identity?.permissions.includes('order:resume') && view.value?.work?.status === 'REJECTED');
const canStart = computed(() => canOperate.value && !view.value?.work && detail.value && (detail.value.order.status === 'READY' || (detail.value.order.status === 'DRAFT' && session.identity?.permissions.includes('order:publish'))));
async function load(): Promise<void> {
  const current = ++generation; loading.value = true; readError.value = '';
  try {
    const params = `warehouseId=${props.warehouse}`;
    const [order, pc] = await Promise.all([request(`/orders/${props.order}?${params}`, detailSchema), request(`/orders/${props.order}/pc?${params}`, viewSchema)]);
    if (current !== generation) return;
    detail.value = order; view.value = pc;
    if (!pc.work && order.order.status === 'DRAFT' && session.identity?.permissions.includes('order:publish')) policy.value = (await request(`/policies/current?${params}`, currentPolicySchema)).policy;
    if ((pc.work && pc.work.status !== 'ACTIVE') || !canOperate.value) tab.value = 'records';
  } catch (cause) { if (current === generation) readError.value = errorText(cause); }
  finally { if (current === generation) loading.value = false; }
}
async function perform(action: Action, body: object): Promise<void> { if (await send(action, body)) dialog.value = null; }
async function recoverOriginal(): Promise<void> { if (await recover()) dialog.value = null; }
function add(candidate: Candidate): void {
  const work = view.value?.work; if (!work || blocked.value || !writing.value) return;
  void perform('entries', { writerId: writer, fence: work.fence, expectedVersion: work.version, barcode: candidate.barcode, entryId: null, reason: '', physicalConfirmed: true });
}
function remove(entry: Entry): void { if (!blocked.value && writing.value) { removing.value = entry; dialog.value = 'void'; } }
function back(): void { void router.push({ path: `/orders/${props.order}`, query: { ...orderRouteQuery(parseOrderQuery(route.query), props.warehouse), tab: 'records' } }); }
function focus(): void { reload(); if (!busy.value && !pending.value && !dialog.value) void load(); }
onMounted(() => { void load(); window.addEventListener('focus', focus); });
onBeforeUnmount(() => { generation++; window.removeEventListener('focus', focus); });
</script>
<template>
  <WorkspaceLayout title="PC 手工出库" class="pc-workspace" :busy="busy || !!pending || !!dialog" warehouse-locked list>
    <PageHeading title="PC 手工出库" :subtitle="detail ? `${detail.plan.vehicleNo} · ${detail.order.orderNo}` : ''" back back-label="返回订单详情" @back="back">
      <template #status><el-tag v-if="view?.work" :type="view.work.status === 'SUCCEEDED' ? 'success' : 'info'">{{ workLabels[view.work.status] }}</el-tag></template>
      <el-button :disabled="busy || !!pending || !!dialog" :loading="loading" @click="load">核对最新记录</el-button>
      <el-button v-if="view?.work" :disabled="blocked || !!dialog" @click="router.push({ path: `/orders/${order}/pc-outbound/exception`, query: route.query })">异常处置与交接</el-button>
    </PageHeading>
    <el-alert v-if="readError" :title="readError + (view ? ' 当前为上次读取的资料，已暂停写入。' : '')" type="error" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="storageError" :title="storageError" type="error" :closable="false"><el-button @click="reload">重试浏览器存储</el-button></el-alert>
    <el-alert v-if="pending" :title="`${actionNames[pending.action]}结果待核对，原请求已保留。`" type="warning" :closable="false"><el-button :loading="busy" @click="recoverOriginal">恢复原PC请求</el-button></el-alert>
    <p v-if="notice && !error && !pending" class="pc-success" role="status">{{ notice }}</p>
    <FeedbackState v-if="!view || !detail" :loading="loading" :error="!!readError" :title="loading ? '正在读取PC作业' : '暂时无法查看此订单'" :action="loading ? undefined : '重新查询'" @action="load" />
    <template v-else-if="!view.work">
      <section class="panel"><h2>开始前核对</h2><p>本单计划 {{ detail.plan.plannedTotal }} 包。确认开始后，作业记录保存在后台，返回或关闭浏览器不会释放订单。</p><el-table :data="detail.lines"><el-table-column prop="deliveryCode" label="发货代码" /><el-table-column prop="model" label="产品型号" /><el-table-column prop="plannedCount" label="计划包数" /></el-table>
        <el-button v-if="canStart" type="primary" :disabled="blocked" @click="dialog = 'start'">{{ detail.order.status === 'DRAFT' ? '发布并开始PC录入' : '确认开始PC录入' }}</el-button>
        <el-alert v-else title="本单当前不能开始PC录入，请核对订单归属、状态及作业权限。" type="info" :closable="false" /></section>
    </template>
    <template v-else>
      <div class="result-summary"><span>作业人员：{{ view.work.operatorName }} · 第 {{ view.work.round }} 轮</span><span>当前规则：{{ view.work.deduct ? '确认出库时自动扣库存' : '不自动扣库存，出库防重仍生效' }}</span></div>
      <div v-if="view.work.status === 'ACTIVE' && !writing" class="pc-writer-notice"><span>{{ mine ? '当前窗口只读，核对后可在此继续录入。' : `本单由 ${view.work.operatorName} 持有，请由原人员继续。` }}</span><el-button v-if="mine && canOperate" :disabled="blocked" @click="dialog = 'writer'">在本窗口继续</el-button></div>
      <el-alert v-if="view.work.status === 'STOPPED'" title="本单已停止录入，包占用仍保留。请进入异常处置与交接，核对原方案。" type="warning" :closable="false" />
      <el-alert v-if="view.work.status === 'CANCELLED'" title="本单已按实物处置方案结束，未办理出库。卸货和隔离记录可在异常处置与交接中查看。" type="info" :closable="false" />
      <div v-if="view.submission && ['REVIEW_REQUIRED', 'REJECTED', 'SUCCEEDED'].includes(view.work.status)" class="pc-result">
        <el-alert :title="workLabels[view.work.status]" :type="view.work.status === 'SUCCEEDED' ? 'success' : 'warning'" :closable="false" />
        <p>{{ view.submission.actualCount }} 包已提交 · {{ view.submission.shortReason ? '短装原因：' + view.submission.shortReason : '各产品达到计划' }}</p>
        <p v-if="view.work.status === 'REJECTED'">原记录和占用保留，主管核验实物后可恢复原人员作业。</p>
        <p v-for="(issue, index) in view.submission.issues" :key="index">{{ issue.barcode }} {{ issue.reason }}</p>
        <div class="page-actions"><el-button v-if="canReview" type="primary" :disabled="blocked" @click="dialog = 'approve'">核对并批准出库</el-button><el-button v-if="canReview" :disabled="blocked" @click="dialog = 'reject'">退回并保留占用</el-button><el-button v-if="canResume" :disabled="blocked" @click="dialog = 'resume'">主管核验恢复作业</el-button></div>
      </div>
      <div class="pc-work-grid">
        <el-tabs v-model="tab" class="pc-main"><el-tab-pane v-if="canOperate && view.work.status === 'ACTIVE'" label="候选条码" name="candidates"><PcCandidates :order="order" :warehouse="warehouse" :products="view.products" :version="view.work.version" :disabled="blocked || !writing" @confirm="add" /></el-tab-pane>
          <el-tab-pane label="本单录入记录" name="records"><PcRecords :order="order" :warehouse="warehouse" :version="view.work.version" :disabled="blocked || !writing" @remove="remove" /></el-tab-pane></el-tabs>
        <PcSummary :view="view" :disabled="blocked || !writing" @close="dialog = 'submit'" />
      </div>
    </template>
    <PcActionDialog v-if="dialog && detail && view" :key="dialog" :kind="dialog" :detail="detail" :view="view" :policy="policy" :entry="removing" :writer="writer" :busy="busy" :pending="!!pending" :error="error" @cancel="dialog = null" @confirm="perform" @recover="recoverOriginal" />
  </WorkspaceLayout>
</template>
