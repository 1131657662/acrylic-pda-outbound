<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import PageHeading from '../components/PageHeading.vue';
import FeedbackState from '../components/FeedbackState.vue';
import { request, errorText } from '../api';
import { connection } from '../connection';
import { useSessionStore } from '../session';
import { detailSchema, localTime, type Detail } from '../orders/contracts';
import { workLabels } from './contracts';
import { exceptionViewSchema, planLabels, type ExceptionView } from './exception-contracts';
import { usePcCommands } from './usePcCommands';
import { actionNames, type Action } from './pending';
import PcExceptionPlanner from './PcExceptionPlanner.vue';
import PcExceptionPlan from './PcExceptionPlan.vue';
import PcExceptionConfirm, { type ExceptionConfirmation } from './PcExceptionConfirm.vue';
const props = defineProps<{ user: string; warehouse: string; order: string }>();
const session = useSessionStore(); const route = useRoute(); const router = useRouter();
const view = ref<ExceptionView | null>(null); const detail = ref<Detail | null>(null); const loading = ref(false); const readError = ref('');
const caseId = ref(''); const planId = ref(''); const dirty = ref(false); const draftRevision = ref(0); const confirmation = ref<ExceptionConfirmation | null>(null); let generation = 0;
async function refresh(): Promise<void> { caseId.value = ''; planId.value = ''; dirty.value = false; draftRevision.value++; await load(); }
const { pending, busy, error, storageError, notice, send, recover, reload } = usePcCommands(props, refresh);
const has = (permission: string): boolean => !!session.identity?.permissions.includes(permission);
const blocked = computed(() => busy.value || !!pending.value || !!storageError.value || loading.value || !!readError.value || connection.state !== 'ok');
const selected = computed(() => view.value?.plans.find(plan => plan.id === planId.value) ?? view.value?.plans[0]);
const activeCase = computed(() => view.value?.work.status === 'STOPPED' && view.value.stopped?.status === 'STOPPED');
const latest = computed(() => selected.value?.id === view.value?.plans[0]?.id);
const canPlan = computed(() => activeCase.value && has('order:resume') && (!view.value?.plans[0] || view.value.plans[0].status === 'REJECTED'));
const canReview = computed(() => activeCase.value && latest.value && has('order:resume') && selected.value?.status === 'PENDING');
const canApprove = computed(() => selected.value && (selected.value.body.lines.every(line => line.action === 'RETAIN') || has('order:dispose')) && (selected.value.body.outcome !== 'CANCEL' || has('order:recall')));
const canWithdraw = computed(() => activeCase.value && latest.value && has('order:resume') && selected.value?.status === 'APPROVED');
const canApply = computed(() => activeCase.value && latest.value && selected.value?.status === 'APPROVED' && selected.value.body.targetUserId === props.user && has('pc:operate'));
const canStop = computed(() => view.value && has('order:recall') && ['ACTIVE', 'REVIEW_REQUIRED', 'REJECTED'].includes(view.value.work.status));
async function load(): Promise<void> {
  const current = ++generation; loading.value = true; readError.value = '';
  try {
    const query = `warehouseId=${props.warehouse}`;
    const [pc, order] = await Promise.all([request(`/orders/${props.order}/pc/exception?${query}${caseId.value ? `&caseId=${caseId.value}` : ''}`, exceptionViewSchema), request(`/orders/${props.order}?${query}`, detailSchema)]);
    if (current === generation) { view.value = pc; detail.value = order; }
  } catch (cause) { if (current === generation) readError.value = errorText(cause); }
  finally { if (current === generation) loading.value = false; }
}
async function perform(action: Action, body: object): Promise<void> { if (await send(action, body)) confirmation.value = null; }
async function recoverOriginal(): Promise<void> { if (await recover()) confirmation.value = null; }
function chooseCase(): void { planId.value = ''; void load(); }
function ask(kind: 'stop' | 'approve' | 'reject' | 'apply'): void {
  if (!view.value || blocked.value) return;
  const body = { expectedVersion: view.value.work.version, planId: selected.value?.id, payloadSha256: selected.value?.payloadSha256 };
  if (kind === 'stop') confirmation.value = { title: '确认停止PC作业', explanation: '停止后所有原窗口立即失去写入资格，待审核提交终止。包占用继续保留，完成逐包方案、审批及交接后才能继续。', action: 'exception/stop', body: { expectedVersion: view.value.work.version } };
  if (kind === 'approve') confirmation.value = { title: '批准处置方案', explanation: '请核对逐包去向、卸货或隔离位置与指定人员。批准本身不释放包，仍需本人确认交接。', action: 'exception/review', body: { ...body, decision: 'APPROVE' } };
  if (kind === 'reject') confirmation.value = { title: selected.value?.status === 'APPROVED' ? '撤回方案批准' : '退回处置方案', explanation: '原方案及审核记录保留，包占用不变。重新提交完整方案并批准后才能交接。', action: 'exception/review', body: { ...body, decision: 'REJECT' } };
  if (kind === 'apply') confirmation.value = { title: '确认实物交接', explanation: `请逐包核对本车保留及已卸下包。成功后将按方案释放或隔离相应包，并${selected.value?.body.outcome === 'CANCEL' ? '结束本单，未办理出库' : '由你接续本单录入'}。`, action: 'exception/apply', body };
}
function back(): void { void router.push({ path: `/orders/${props.order}/pc-outbound`, query: route.query }); }
function focus(): void { reload(); if (!busy.value && !pending.value && !dirty.value && !confirmation.value) void load(); }
onMounted(() => { void load(); window.addEventListener('focus', focus); });
onBeforeUnmount(() => { generation++; window.removeEventListener('focus', focus); });
</script>
<template>
  <WorkspaceLayout title="PC 异常处置与交接" class="pc-exception-workspace" warehouse-locked :busy="busy || !!pending || !!confirmation || dirty">
    <PageHeading title="PC 异常处置与交接" :subtitle="detail ? `${detail.plan.vehicleNo} · ${detail.order.orderNo}` : ''" back back-label="返回PC作业" @back="back">
      <template #status><el-tag v-if="view">{{ workLabels[view.work.status] }}</el-tag></template>
      <el-button :loading="loading" :disabled="busy || !!pending || dirty || !!confirmation" @click="load">核对最新记录</el-button>
      <el-button v-if="canStop" :disabled="blocked || !!confirmation" @click="ask('stop')">停止并核验实物</el-button>
    </PageHeading>
    <el-alert v-if="readError" :title="readError + (view ? ' 当前显示上次资料，已暂停操作。' : '')" type="error" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="storageError" :title="storageError" type="error" :closable="false"><el-button @click="reload">重试浏览器存储</el-button></el-alert>
    <el-alert v-if="pending" :title="`${actionNames[pending.action]}结果待核对，原请求已保留。`" type="warning" :closable="false"><el-button :loading="busy" @click="recoverOriginal">恢复原PC请求</el-button></el-alert>
    <p v-if="notice && !error && !pending" class="pc-success" role="status">{{ notice }}</p>
    <FeedbackState v-if="!view" :loading="loading" :error="!!readError" :title="loading ? '正在读取处置记录' : '暂时无法查看PC作业'" :action="loading ? undefined : '重新查询'" @action="load" />
    <template v-else>
      <p>当前作业人员：{{ view.work.operatorName }} · 第 {{ view.work.round }} 轮。停止、批准和交接生效分别记录。</p>
      <el-alert v-if="view.work.status === 'STOPPED'" title="PC录入已停止，原包占用保留。请完成逐包核验、主管审批和本人交接。" type="warning" :closable="false" />
      <div v-if="view.history.length" class="pc-exception-fields"><el-form-item label="停止核验记录"><el-select v-model="caseId" aria-label="停止核验记录" placeholder="最近一次核验" :disabled="blocked || dirty" @change="chooseCase"><el-option label="最近一次核验" value="" /><el-option v-for="history in view.history" :key="history.id" :value="history.id" :label="`第 ${history.round} 轮 · ${localTime(history.stoppedAt)} · ${history.status === 'APPLIED' ? '已交接' : '待处置'}`" /></el-select></el-form-item></div>
      <FeedbackState v-if="!view.stopped" title="本单暂无异常处置记录" :description="canStop ? '需要换人、卸货或隔离时，先停止作业并核对实物。' : '由具备停止作业权限的主管发起核验。'" />
      <template v-else>
        <p>停止原因：{{ view.stopped.reason }} · {{ localTime(view.stopped.stoppedAt) }}</p>
        <PcExceptionPlanner v-if="canPlan" :key="`${view.stopped.id}-${view.plans.length}-${draftRevision}`" :stopped="view.stopped" :version="view.work.version" :disabled="blocked" @submit="body => perform('exception/plan', body)" @dirty="dirty = $event" />
        <el-button v-if="dirty && !blocked" @click="dirty = false; refresh()">放弃未提交方案</el-button>
        <div v-if="view.plans.length > 1" class="pc-exception-fields"><el-form-item label="方案记录"><el-select v-model="planId" aria-label="方案记录" placeholder="最新方案" :disabled="blocked || dirty"><el-option label="最新方案" value="" /><el-option v-for="(plan, index) in view.plans" :key="plan.id" :value="plan.id" :label="`方案 ${view.plans.length - index} · ${planLabels[plan.status]} · ${localTime(plan.requestedAt)}`" /></el-select></el-form-item></div>
        <PcExceptionPlan v-if="selected" :key="selected.id" :plan="selected" :stopped="view.stopped" />
        <div class="page-actions pc-exception-actions">
          <el-button v-if="canReview && canApprove" type="primary" :disabled="blocked" @click="ask('approve')">批准处置方案</el-button>
          <el-button v-if="canReview" :disabled="blocked" @click="ask('reject')">退回处置方案</el-button>
          <el-button v-if="canWithdraw" :disabled="blocked" @click="ask('reject')">撤回方案批准</el-button>
          <el-button v-if="canApply" type="primary" :disabled="blocked" @click="ask('apply')">本人确认实物交接</el-button>
        </div>
        <p v-if="canReview && !canApprove" class="muted">当前账号缺少本方案所需的实物处置或结束权限，请由有权限的主管批准。</p>
        <section v-if="view.reviews.length" class="pc-exception-audit"><h2>审核记录</h2><p v-for="(review, index) in view.reviews" :key="index">{{ localTime(review.createdAt) }} · {{ review.actorName }} · {{ review.decision === 'APPROVE' ? '批准' : '退回或撤回批准' }}：{{ review.reason }}</p></section>
      </template>
    </template>
    <PcExceptionConfirm v-if="confirmation" :confirmation="confirmation" :disabled="busy || !!pending" :pending="!!pending" :error="error || storageError" @confirm="perform" @cancel="confirmation = null" @recover="recoverOriginal" />
  </WorkspaceLayout>
</template>
