<script setup lang="ts">
import { computed, ref } from 'vue';
import { useSessionStore } from '../session';
import { useReconciliation } from './useReconciliation';
import { labels } from './contracts';
import type { ReconciliationKind } from './contracts';
const props = defineProps<{ actor: string; warehouse: string }>(); const session = useSessionStore();
const { list, selected, pending, busy, blocked, error, load, select, upload, download, act, recover } = useReconciliation(props);
const kind = ref<ReconciliationKind | null>(null); const reason = ref(''); const place = ref(''); const reference = ref('');
const observed = ref(''); const attachments = ref<Awaited<ReturnType<typeof upload>>[]>([]); const uploading = ref(false); const uploadError = ref('');
const unresolved = computed(() => pending.value && !pending.value.receipt && !pending.value.rejection);
const maker = computed(() => session.identity?.permissions.includes('inventory:reconcile') && session.identity?.permissions.includes('outbound:correct'));
const reviewer = computed(() => session.identity?.permissions.includes('correction:approve'));
const statuses: Record<string, string> = { OPEN: '待提交核验凭据', REVIEW_READY: '待主管核对', RESOLVED: '已处理' };
const submitting = computed(() => kind.value?.endsWith('_SUBMIT'));
const returning = computed(() => kind.value?.startsWith('RECONCILE_RETURN_'));
function statusText(status: string, returned: boolean): string {
  return status === 'RESOLVED' ? returned ? '库存账已恢复' : '已补扣' : statuses[status] ?? status;
}
async function open(action: ReconciliationKind, id: string): Promise<void> {
  await select(action, id); if (!selected.value || error.value) return;
  kind.value = action; reason.value = ''; place.value = ''; reference.value = ''; attachments.value = []; uploadError.value = '';
  observed.value = new Date().toISOString().slice(0,19) + 'Z';
}
async function file(event: Event): Promise<void> {
  if (!(event.target instanceof HTMLInputElement) || !event.target.files?.[0]) return;
  uploading.value = true;
  try { const metadata = await upload(event.target.files[0]); attachments.value.push(metadata); uploadError.value = ''; }
  catch { uploadError.value = '凭据上传未确认，请保留文件后重新核对；未提交差异处理。'; }
  finally { uploading.value = false; }
}
async function confirm(): Promise<void> {
  if (!kind.value) return;
  await act(kind.value, reason.value.trim(), submitting.value ? { observedAt: observed.value,
    place: place.value.trim(), reference: reference.value.trim(), observation: returning.value ? 'RETURNED_IN_WAREHOUSE' : 'DISPATCHED_CONFIRMED', attachments: attachments.value } : undefined);
  if (pending.value?.receipt) kind.value = null;
}
</script>
<template>
  <section class="reconciliation-workspace" aria-label="库存账务差异处理">
    <el-alert v-if="error && !kind" :title="error" type="error" :closable="false" />
    <el-alert v-if="unresolved" title="原差异操作结果未确认，原请求已保留。" type="warning" :closable="false"><el-button :loading="busy" @click="recover">核对或重试原差异请求</el-button></el-alert>
    <el-alert v-if="pending?.receipt" :title="`${labels[pending.receipt.commandType]}已记录：${statusText(pending.receipt.businessDecision.status, pending.receipt.commandType.startsWith('RECONCILE_RETURN_'))}`" type="success" :closable="false" />
    <el-alert v-if="pending?.rejection" :title="`原操作已拒绝：${pending.rejection.message}`" type="warning" :closable="false" />
    <el-button :disabled="busy || !!unresolved" @click="load()">刷新差异与原事实</el-button>
    <h2>成功出库的缺账候选</h2><p>逐笔核对原出库、上游声明、实物凭据、占用及实际流水。登记与提交不改账，独立主管批准才补扣；案件结案保持周期与隔离。</p>
    <el-empty v-if="list && !list.candidates.length" description="没有缺账候选" />
    <article v-for="item in list?.candidates" :key="item.dispatchId" :data-dispatch="item.dispatchId">
      <p>条码 {{ item.barcode }} · 原订单 {{ item.orderId }}</p>
      <el-button v-if="maker" :disabled="busy || blocked || !!unresolved" @click="open('RECONCILE_DISCOVER', item.dispatchId)">登记缺账差异</el-button>
    </article>
    <el-button v-if="list?.nextCursor" :disabled="busy" @click="load('candidates')">更多缺账候选</el-button>
    <h2>已批准退货/错误出库的返库恢复候选</h2>
    <p>逐笔核对原更正审批、实物回库、上游资料、占用与实际反向流水。独立主管批准后恢复库存账；其他隔离或冲突仍须分别处理。</p>
    <el-empty v-if="list && !list.returnedCandidates.length" description="没有返库恢复候选" />
    <article v-for="item in list?.returnedCandidates" :key="item.dispatchId" :data-returned-dispatch="item.dispatchId">
      <p>条码 {{ item.barcode }} · 原订单 {{ item.orderId }}</p>
      <el-button v-if="maker" :disabled="busy || blocked || !!unresolved" @click="open('RECONCILE_RETURN_DISCOVER', item.dispatchId)">登记返库恢复</el-button>
    </article>
    <el-button v-if="list?.nextReturnedCursor" :disabled="busy" @click="load('returned')">更多返库候选</el-button>
    <h2>差异案件</h2><el-empty v-if="list && !list.cases.length" description="没有差异案件" />
    <article v-for="item in list?.cases" :key="item.caseId" :data-case="item.caseId">
      <p>案件 {{ item.caseId }} · {{ statusText(item.status, item.kind === 'RETURNED_STOCK') }}</p>
      <el-button v-if="maker && item.status === 'OPEN'" :disabled="busy || blocked || !!unresolved" @click="open(item.kind === 'RETURNED_STOCK' ? 'RECONCILE_RETURN_SUBMIT' : 'RECONCILE_SUBMIT', item.caseId)">{{ item.kind === 'RETURNED_STOCK' ? '提交返库核验凭据' : '提交核验凭据' }}</el-button>
      <el-button v-if="reviewer && item.status === 'REVIEW_READY'" :disabled="busy || blocked || !!unresolved" @click="open(item.kind === 'RETURNED_STOCK' ? 'RECONCILE_RETURN_APPROVE' : 'RECONCILE_APPROVE', item.caseId)">{{ item.kind === 'RETURNED_STOCK' ? '主管批准返库恢复' : '主管批准补扣' }}</el-button>
    </article>
    <el-button v-if="list?.nextCaseCursor" :disabled="busy" @click="load('cases')">更多差异案件</el-button>
    <el-dialog class="reconciliation-dialog" :model-value="!!kind" :title="kind ? labels[kind] : ''" width="680" :close-on-click-modal="false" :close-on-press-escape="!busy && !uploading" :show-close="!busy && !uploading" @close="kind = null">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <template v-if="selected">
        <el-descriptions title="固定核对事实" :column="1" border>
          <el-descriptions-item label="条码">{{ selected.context.facts.stock.barcode }}</el-descriptions-item>
          <el-descriptions-item label="原结果版本">{{ selected.context.facts.result.currentResultVersion }}</el-descriptions-item>
          <el-descriptions-item label="有效包数/克">{{ selected.context.facts.dispatch.count }}/{{ selected.context.facts.dispatch.weightG }}</el-descriptions-item>
          <el-descriptions-item label="周期">{{ selected.context.facts.dispatch.dispatchCycle }}</el-descriptions-item>
          <el-descriptions-item label="上游在库">{{ selected.context.facts.stock.upstreamPresent ? '声明在库' : '未声明在库' }}</el-descriptions-item>
          <el-descriptions-item label="账务">{{ selected.context.facts.stock.bookStatus }} · 流水 {{ selected.context.facts.ledger.length }}</el-descriptions-item>
          <el-descriptions-item label="隔离原因">{{ selected.context.facts.stock.blockedReason || '无' }}</el-descriptions-item>
          <el-descriptions-item label="固定快照">{{ selected.context.facts.stock.snapshotId }}</el-descriptions-item>
        </el-descriptions>
        <el-descriptions v-if="returning && selected.context.facts.reopening" title="原退货与周期依据" :column="1" border>
          <el-descriptions-item label="原更正">{{ selected.context.facts.reopening.type === 'PHYSICAL_RETURN' ? '实物退货' : '错误出库撤销' }} · {{ selected.context.facts.reopening.documentId }}</el-descriptions-item>
          <el-descriptions-item label="原审批回执">{{ selected.context.facts.reopening.approvalReceipt.receiptId }}</el-descriptions-item>
          <el-descriptions-item label="原审批人/时刻">{{ selected.context.facts.reopening.approvalReceipt.actor.principalId }} · {{ selected.context.facts.reopening.approvalReceipt.recordedAt }}</el-descriptions-item>
          <el-descriptions-item label="原重开周期">{{ selected.context.facts.reopening.cycleBefore }} → {{ selected.context.facts.reopening.cycleAfter }} · 结果版本 {{ selected.context.facts.reopening.stateVersion }}</el-descriptions-item>
          <el-descriptions-item label="原依据摘要">{{ selected.context.facts.reopening.evidenceSha256 }}</el-descriptions-item>
        </el-descriptions>
        <p v-if="selected.context.submittedCommand">原核验：{{ selected.context.submittedCommand.evidence.place }} · {{ selected.context.submittedCommand.evidence.observedAt }} · {{ selected.context.submittedCommand.evidence.reference }}</p>
        <el-button v-for="(attachment, index) in selected.context.submittedCommand?.evidence.attachments" :key="attachment.attachmentId"
          :disabled="busy || uploading" @click="download(attachment)">读取原始凭据 {{ index + 1 }}</el-button>
        <p v-if="returning">须已核验实物回库及原审批/反向流水；恢复库存账保留原结果与已重开周期，其他隔离、缺码和占用冲突继续阻止出库。</p>
        <p v-else>补扣不释放占用、不解除隔离、不重开周期，不更改原出库数量和重量。</p>
      </template>
      <el-form label-position="top">
        <template v-if="submitting">
          <el-form-item label="核验时刻（带时区）"><el-input v-model="observed" aria-label="实物核验时刻" :disabled="busy || !!unresolved" /></el-form-item>
          <el-form-item label="实物核验地点"><el-input v-model="place" aria-label="实物核验地点" maxlength="200" :disabled="busy || !!unresolved" /></el-form-item>
          <el-form-item :label="returning ? '实物已回库的核验依据' : '已实际发出的核验依据'"><el-input v-model="reference" aria-label="实物核验依据" maxlength="500" :disabled="busy || !!unresolved" /></el-form-item>
          <el-form-item label="原始凭据（最多5个，每个10MB）"><input type="file" aria-label="差异原始凭据" :disabled="busy || uploading || attachments.length >= 5 || !!unresolved" @change="file" /></el-form-item>
          <p>已取得 {{ attachments.length }} 份凭据</p><el-alert v-if="uploadError" :title="uploadError" type="error" :closable="false" />
        </template>
        <el-form-item label="本次处理原因"><el-input v-model="reason" aria-label="差异处理原因" type="textarea" maxlength="500" :disabled="busy || !!unresolved" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="busy || uploading" @click="kind = null">返回核对</el-button>
        <el-button type="primary" :loading="busy" :disabled="uploading || !!unresolved || !reason.trim() || submitting && (!place.trim() || !reference.trim() || !attachments.length)" @click="confirm">确认{{ kind ? labels[kind] : '' }}</el-button></template>
    </el-dialog>
  </section>
</template>
<style scoped>
.reconciliation-workspace { display: grid; gap: var(--space-4); }
.reconciliation-workspace h2 { margin: 0; font-size: 18px; }
.reconciliation-workspace p { margin: 0; overflow-wrap: anywhere; line-height: 1.6; }
.reconciliation-workspace article { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap;
  gap: var(--space-3); padding: var(--space-4); border: 1px solid var(--separator); border-radius: var(--radius-control); }
.reconciliation-workspace > .el-button { justify-self: start; }
:global(.reconciliation-dialog.el-dialog) { margin-block: var(--space-4); max-height: calc(100dvh - 2 * var(--space-4)); }
</style>
