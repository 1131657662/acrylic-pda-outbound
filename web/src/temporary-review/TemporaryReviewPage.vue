<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { request, errorText } from '../api';
import { useSessionStore } from '../session';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import PageHeading from '../components/PageHeading.vue';
import FeedbackState from '../components/FeedbackState.vue';
import { localTime } from '../orders/contracts';
import { kilograms, submissionLabel } from '../orders/submission-contracts';
import { issueLabel, temporaryReviewSchema, type TemporaryReview } from './contracts';
import TemporaryReviewScans from './TemporaryReviewScans.vue';
const session = useSessionStore(); const route = useRoute(); const router = useRouter();
const detail = ref<TemporaryReview | null>(null); const busy = ref(false); const error = ref('');
const id = computed(() => typeof route.params.id === 'string' ? route.params.id : '');
const status = computed(() => detail.value?.status === 'REVIEW_REQUIRED' ? '待独立确认' :
  Object.entries(submissionLabel).find(([key]) => key === detail.value?.status)?.[1] ?? '状态待核对');
let generation = 0;
async function load(): Promise<void> {
  const ticket = ++generation; detail.value = null; error.value = '';
  if (!session.warehouseId || !session.identity?.permissions.includes('temporary_order:confirm')) { busy.value = false; return; }
  busy.value = true;
  try {
    const value = await request(`/temporary-reviews/${encodeURIComponent(id.value)}?${new URLSearchParams({ warehouseId: session.warehouseId })}`, temporaryReviewSchema);
    if (ticket === generation) detail.value = value;
  } catch (cause) { if (ticket === generation) error.value = errorText(cause); }
  finally { if (ticket === generation) busy.value = false; }
}
function back(): void {
  const fromReminder = route.query.from === 'review-reminders' && route.query.reminderWarehouse === session.warehouseId;
  const cursor = fromReminder ? route.query.reminderCursor : route.query.listCursor;
  void router.push({ path: fromReminder ? '/review-reminders' : '/temporary-reviews', query: {
    warehouse: session.warehouseId, cursor: typeof cursor === 'string' ? cursor : undefined,
  } });
}
watch(() => [id.value, session.warehouseId, session.identity?.id, session.identity?.permissions.includes('temporary_order:confirm')], () => { void load(); }, { immediate: true });
onBeforeUnmount(() => { generation++; });
</script>
<template>
  <WorkspaceLayout title="临时单审阅">
    <PageHeading :title="detail ? `临时单审阅 ${detail.orderNo}` : '临时单审阅'" :subtitle="detail?.vehicleNo" back back-label="返回待办列表" @back="back">
      <el-button :loading="busy" @click="load">重新核对</el-button>
    </PageHeading>
    <FeedbackState v-if="!detail" :title="busy ? '正在核对原批次' : '审阅资料无法读取'" :description="error" :loading="busy" :error="!busy" :action="busy ? undefined : '重试查询'" @action="load" />
    <template v-else>
      <el-alert title="当前不可放行。此页仅供审阅，出库确认与实物处置尚未开放。" type="warning" :closable="false" />
      <p><el-tag type="warning">{{ status }}</el-tag> 临时号 {{ detail.temporaryNo }} · 收到有效记录 {{ detail.expectedCount }} 包 · {{ kilograms(detail.expectedWeightGrams) }} kg</p>
      <p class="muted">完整接收：{{ localTime(detail.receivedAt) }} · 本次核对：{{ localTime(detail.checkedAt) }}</p>
      <section class="panel">
        <h2>原作业与计划</h2>
        <p>车号：{{ detail.vehicleNo }} · 柜号：{{ detail.containerNo || '未填写' }}</p>
        <p>原人员：{{ detail.operatorName }} · 原设备：{{ detail.deviceName }}</p>
        <p>临时建单原因：{{ detail.reason }}</p><p v-if="detail.shortShipmentReason">原短装原因：{{ detail.shortShipmentReason }}</p>
        <el-table :data="detail.products" row-key="deliveryCode">
          <el-table-column prop="deliveryCode" label="发货代码" width="100" /><el-table-column prop="model" label="原型号" min-width="120" />
          <el-table-column prop="name" label="原品名" min-width="150" /><el-table-column prop="plannedCount" label="原计划包数" width="110" align="right" />
          <el-table-column prop="actualCount" label="收到有效包数" width="120" align="right" /><el-table-column prop="shortCount" label="短装包数" width="100" align="right" />
          <el-table-column label="有效净重 kg" width="130" align="right"><template #default="{ row }">{{ kilograms(row.weightGrams) }}</template></el-table-column>
        </el-table>
      </section>
      <section class="panel">
        <h2>当前待核对事项</h2><p class="muted">下列内容是本次读取时的复核结果，未作审批或处置决定。短装、冲突和临时出库确认分别处理。</p>
        <ul><li v-for="issue in detail.currentIssues" :key="issue.code">{{ issueLabel(issue.code) }}<span v-if="issue.count !== '1'">（{{ issue.count }} 项）</span></li></ul>
        <p>原资格有效至 {{ localTime(detail.authorization.expiresAt) }}；到期不删除原记录、不释放实物占用。</p>
        <details><summary>上传时登记的待核对事项</summary><ul><li v-for="issue in detail.receivedIssues" :key="issue.code">{{ issueLabel(issue.code) }}（{{ issue.count }} 项）</li></ul></details>
      </section>
      <TemporaryReviewScans :submission-id="detail.submissionId" :warehouse-id="session.warehouseId" />
      <section class="panel"><details class="file-proof"><summary>原来源与批次凭据</summary>
        <p>原临时单：{{ detail.source.clientOrderId }} · 原设备：{{ detail.source.creatorDeviceId }}</p>
        <p>原资格：{{ detail.authorization.authorizationId }} · 原人员版本 {{ detail.authorization.userVersion }} · 原设备版本 {{ detail.authorization.deviceVersion }}</p>
        <p>提交批次：{{ detail.submissionId }} · 轮次 {{ detail.workRound }} · {{ detail.current ? '当前批次' : '历史批次' }}</p>
        <p>当前批次：{{ detail.currentSubmissionId || '暂无当前批次' }}</p>
        <p>原计划版本 {{ detail.orderRevision }} · 库存 {{ detail.inventorySnapshotId }} · 政策 {{ detail.policyVersion }}</p>
        <p>原恢复代次 {{ detail.sourceRecoveryEpoch }} · 后台代次 {{ detail.serverRecoveryEpoch }} · {{ detail.serviceMode === 'NORMAL' ? '正常服务' : '恢复对账中' }}</p>
        <p>原计划摘要：{{ detail.planSha256 }}</p><p>原正文摘要：{{ detail.payloadSha256 }}</p>
        <p>本次审阅人员权限版本 {{ detail.reviewerPermissionVersion }} · 原政策{{ detail.autoDeductInventory ? '开启' : '关闭' }}自动扣账；当前尚未出库。</p>
      </details></section>
    </template>
  </WorkspaceLayout>
</template>
