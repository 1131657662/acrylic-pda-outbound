<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { request, errorText, ApiError } from '../api';
import { useSessionStore } from '../session';
import { submissionDetailSchema, submissionSchema, submissionLabel, kilograms } from './submission-contracts';
import type { SubmissionDetail } from './submission-contracts';
const props = defineProps<{ orderId: string; warehouseId: string }>();
const emit = defineEmits<{ updated: [] }>();
const session = useSessionStore(); const detail = ref<SubmissionDetail | null>(null);
const busy = ref(false); const error = ref(''); const reason = ref(''); const pendingBody = ref(''); const decision = ref<'APPROVE' | 'REJECT' | null>(null);
let generation = 0;
const canReview = computed(() => session.identity?.permissions.includes('submission:review') && detail.value?.submission?.status === 'REVIEW_REQUIRED');
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
    if (cause instanceof ApiError && cause.status < 500) pendingBody.value = '';
    error.value = `${errorText(cause)}${pendingBody.value ? ' 原审批内容已保留，请重试原请求。' : ''}`;
  }
  finally { busy.value = false; }
}
watch(() => [props.warehouseId, props.orderId], () => { detail.value = null; pendingBody.value = ''; decision.value = null; void load(); }, { immediate: true });
</script>
<template>
  <section class="panel">
    <div class="section-heading"><div><h2>装车提交与云端回执</h2><p class="muted">仅云端确认出库后可放行。短装批准会重新校验全部明细；退回和取消保留占用，不能直接重新扫码。</p></div><el-button :loading="busy" @click="load">查询结果</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="detail?.submission">
      <el-tag :type="detail.submission.status === 'SUCCEEDED' ? 'success' : 'warning'">{{ submissionLabel[detail.submission.status] }}</el-tag>
      <p>收到有效明细 {{ detail.expectedCount ?? '—' }} 包 · {{ detail.expectedWeightGrams ? kilograms(detail.expectedWeightGrams) : '—' }} kg</p>
      <p v-if="detail.submission.status === 'SUCCEEDED'">确认出库 {{ detail.submission.actualCount }} 包 · {{ kilograms(detail.submission.actualWeightGrams ?? '0') }} kg · {{ detail.submission.inventoryDeducted ? '已自动扣账' : '未自动扣账（已保留出库防重）' }}</p>
      <p v-if="detail.shortShipmentReason">短装原因：{{ detail.shortShipmentReason }}</p>
      <el-alert v-for="(issue, index) in detail.submission.issues" :key="index" :title="`${issue.barcode ?? ''} ${issue.code}${issue.reason ? '：' + issue.reason : ''}`" type="warning" :closable="false" />
      <el-table :data="detail.scans ?? []" max-height="360"><el-table-column prop="orderScanSeq" label="原扫码序号" width="115" /><el-table-column prop="barcode" label="条码" min-width="160" /><el-table-column prop="deliveryCode" label="产品" width="90" /><el-table-column label="重量 kg" width="130"><template #default="{ row }">{{ kilograms(row.weightGrams) }}</template></el-table-column><el-table-column label="状态" width="130"><template #default="{ row }">{{ row.status === 'ACTIVE' ? '有效明细' : '本地已撤销' }}</template></el-table-column></el-table>
      <div v-if="canReview" class="review-actions"><el-input v-model="reason" aria-label="主管审批原因" type="textarea" maxlength="500" :disabled="busy || !!pendingBody" placeholder="核对实物、车号及短装原因后填写审批说明" />
        <el-button type="primary" :disabled="busy || !reason.trim() || !!pendingBody" @click="decision = 'APPROVE'">批准当前短装出库</el-button>
        <el-button :disabled="busy || !reason.trim() || !!pendingBody" @click="decision = 'REJECT'">退回处理并保留占用</el-button>
      </div>
      <p v-if="pendingBody" class="muted">原审批结果待核对。批次与审批正文保持不变，请重试原请求。</p>
    </template>
    <p v-else-if="!busy && !error" class="muted">云端尚未收到冻结提交。</p>
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
