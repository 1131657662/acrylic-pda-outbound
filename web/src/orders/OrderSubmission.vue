<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { request, errorText, ApiError } from '../api';
import { useSessionStore } from '../session';
import { submissionDetailSchema, submissionSchema, submissionLabel, kilograms } from './submission-contracts';
import type { SubmissionDetail } from './submission-contracts';
import { dispositionLabels } from './disposition-contracts';
const issueLabels: Record<string, string> = { DISPATCH_CYCLE_CONFLICT: '出库轮次已变化', INVENTORY_UNAVAILABLE: '库存不可用',
  BALE_ALREADY_COMMITTED: '已被其他订单占用', INVENTORY_BASELINE_CHANGED: '库存资料已变化',
  SHORT_SHIPMENT_APPROVAL_REQUIRED: '短装需要主管审核', SUPERVISOR_REJECTED: '主管已退回',
  OFFLINE_AUTH_REAUTHORIZATION_REQUIRED: '原作业身份需要重新核验', ORDER_RECOVERY_REQUIRED: '订单需要恢复核对',
  DOWNLOAD_ACK_REQUIRED: '原 PDA 尚未确认资料接收', ORDER_STATE_CONFLICT: '订单状态已变化' };
const props = defineProps<{ orderId: string; warehouseId: string; version?: string }>();
const emit = defineEmits<{ updated: []; busy: [value: boolean] }>();
const session = useSessionStore(); const detail = ref<SubmissionDetail | null>(null);
const busy = ref(false); const error = ref(''); const reason = ref(''); const pendingBody = ref(''); const decision = ref<'APPROVE' | 'REJECT' | null>(null);
let generation = 0;
const canReview = computed(() => session.identity?.permissions.includes('submission:review') && detail.value?.submission?.status === 'REVIEW_REQUIRED');
function scanLabel(id: string, status: string): string {
  const proof = detail.value?.dispositions.find(d => d.captureId === id);
  return proof ? dispositionLabels[proof.action] : status === 'ACTIVE' ? '有效明细' : '本地已撤销';
}
async function load(): Promise<void> {
  const current = ++generation; busy.value = true; error.value = '';
  try {
    const result = await request(`/orders/${props.orderId}/submission?warehouseId=${props.warehouseId}`, submissionDetailSchema);
    if (current === generation) detail.value = result;
  } catch (cause) { if (current === generation) error.value = errorText(cause); }
  finally { if (current === generation) busy.value = false; }
}
async function review(): Promise<void> {
  const submission = detail.value?.submission;
  if (!submission || !decision.value || busy.value) return;
  if (!pendingBody.value) pendingBody.value = JSON.stringify({ payloadSha256: submission.payloadSha256, decision: decision.value, reason: reason.value.trim() });
  busy.value = true; error.value = '';
  try {
    await request(`/submissions/${submission.submissionId}/review`, submissionSchema, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: pendingBody.value,
    });
    pendingBody.value = ''; decision.value = null; reason.value = ''; await load(); emit('updated');
  } catch (cause) {
    if (cause instanceof ApiError && cause.status < 500 && cause.status !== 401) pendingBody.value = '';
    error.value = `${errorText(cause)}${pendingBody.value ? ' 原审批内容已保留，请重试原请求。' : ''}`;
  }
  finally { busy.value = false; }
}
watch(() => [props.warehouseId, props.orderId], () => { detail.value = null; pendingBody.value = ''; decision.value = null; void load(); }, { immediate: true });
watch(() => props.version, () => { if (!pendingBody.value && !decision.value) void load(); });
watch(() => !!decision.value || !!pendingBody.value, value => emit('busy', value));
onBeforeUnmount(() => { generation++; });
</script>
<template>
  <section class="panel">
    <div class="section-heading"><div><h2>后台收到的装车记录</h2><p class="muted">后台确认出库后才能放行。退回和取消保留占用，不能直接重新扫码。</p></div><el-button :loading="busy" :disabled="!!pendingBody || !!decision" @click="load">查询结果</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="detail?.submission">
      <el-tag :type="detail.submission.status === 'SUCCEEDED' ? 'success' : 'warning'">{{ submissionLabel[detail.submission.status] }}</el-tag>
      <p>收到有效明细 {{ detail.expectedCount ?? '尚未收到' }} 包 · {{ detail.expectedWeightGrams != null ? kilograms(detail.expectedWeightGrams) : '尚未收到' }} kg</p>
      <p v-if="detail.submission.status === 'SUCCEEDED'">确认出库 {{ detail.submission.actualCount }} 包 · {{ kilograms(detail.submission.actualWeightGrams ?? '0') }} kg · {{ detail.submission.inventoryDeducted ? '已自动扣账' : '未自动扣账（已保留出库防重）' }}</p>
      <p v-if="detail.shortShipmentReason">短装原因：{{ detail.shortShipmentReason }}</p>
      <el-alert v-for="(issue, index) in detail.submission.issues" :key="index" :title="`${issue.barcode ?? ''} ${issueLabels[issue.code] ?? '记录需核对'}${issue.reason ? '：' + issue.reason : ''}`" type="warning" :closable="false" />
      <details v-if="detail.submission.issues?.length" class="file-proof"><summary>问题详情</summary><p v-for="(issue, index) in detail.submission.issues" :key="index">{{ issue.barcode }} · {{ issue.code }} {{ issue.reason }}</p></details>
      <el-table :data="detail.scans ?? []" max-height="360"><el-table-column prop="orderScanSeq" label="原扫码序号" width="115" /><el-table-column prop="barcode" label="条码" min-width="160" /><el-table-column prop="deliveryCode" label="产品" width="90" /><el-table-column label="重量 kg" width="130"><template #default="{ row }">{{ kilograms(row.weightGrams) }}</template></el-table-column><el-table-column label="状态" width="130"><template #default="{ row }">{{ scanLabel(row.captureId, row.status) }}</template></el-table-column></el-table>
      <div v-if="canReview" class="review-actions"><el-input v-model="reason" aria-label="主管审批原因" type="textarea" maxlength="500" :disabled="busy || !!pendingBody" placeholder="核对实物、车号及短装原因后填写审批说明" />
        <el-button type="primary" :disabled="busy || !reason.trim() || !!pendingBody" @click="decision = 'APPROVE'">批准当前短装出库</el-button>
        <el-button :disabled="busy || !reason.trim() || !!pendingBody" @click="decision = 'REJECT'">退回处理并保留占用</el-button>
      </div>
      <p v-if="pendingBody" class="muted">原审批结果待核对。批次与审批正文保持不变，请重试原请求。</p>
    </template>
    <p v-else-if="!busy && !error" class="muted">后台尚未收到本单的结单资料。</p>
    <el-dialog :model-value="decision !== null" :title="decision === 'APPROVE' ? '批准当前短装出库' : '退回当前批次'" width="min(480px, 95vw)" :show-close="false" :close-on-click-modal="false" :close-on-press-escape="false">
      <p>{{ decision === 'APPROVE' ? '请确认实物、车辆和短装原因均已核实。确认后服务器将重新校验并尝试整单出库。' : '退回后仍保留原批次和实物占用，不能自动解冻或释放。' }}</p><p>{{ reason }}</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <template #footer><el-button :disabled="busy || !!pendingBody" @click="decision = null">返回核对</el-button><el-button type="primary" :loading="busy" @click="review">{{ pendingBody ? '重试原审批' : '确认此决定' }}</el-button></template>
    </el-dialog>
  </section>
</template>
<style scoped>
.review-actions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 16px; }
</style>
