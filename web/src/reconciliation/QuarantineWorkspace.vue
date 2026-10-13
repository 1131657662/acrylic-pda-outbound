<script setup lang="ts">
import { computed, ref } from 'vue';
import { useSessionStore } from '../session';
import { useQuarantine } from './useQuarantine';
import { labels } from './contracts';
import type { QuarantineKind } from './quarantine-contracts';
import QuarantineHistory from './QuarantineHistory.vue';
const props = defineProps<{ actor: string; warehouse: string }>(); const session = useSessionStore();
const { list, selected, pending, busy, blocked, error, load, select, upload, download, source, act, recover } = useQuarantine(props);
const kind = ref<QuarantineKind | null>(null); const reason = ref(''); const place = ref(''); const reference = ref('');
const observed = ref(''); const attachments = ref<Awaited<ReturnType<typeof upload>>[]>([]); const uploading = ref(false); const uploadError = ref('');
const unresolved = computed(() => pending.value && !pending.value.receipt && !pending.value.rejection);
const maker = computed(() => session.identity?.permissions.includes('inventory:reconcile') && session.identity?.permissions.includes('outbound:correct'));
const reviewer = computed(() => session.identity?.permissions.includes('correction:approve'));
const statuses: Record<string, string> = { OPEN: '待提交核验凭据', REVIEW_READY: '待主管核对', RESOLVED: '原隔离已解除' };
const submitting = computed(() => kind.value?.endsWith('_SUBMIT'));
async function open(action: QuarantineKind, id: string): Promise<void> {
  await select(action, id); if (!selected.value || error.value) return;
  kind.value = action; reason.value = ''; place.value = ''; reference.value = ''; attachments.value = []; uploadError.value = '';
  observed.value = new Date().toISOString().slice(0,19) + 'Z';
}
async function file(event: Event): Promise<void> {
  if (!(event.target instanceof HTMLInputElement) || !event.target.files?.[0]) return; uploading.value = true;
  try { attachments.value.push(await upload(event.target.files[0])); uploadError.value = ''; }
  catch { uploadError.value = '凭据上传未确认，请保留文件后重新核对。'; }
  finally { uploading.value = false; }
}
async function confirm(): Promise<void> {
  if (!kind.value) return;
  await act(kind.value, reason.value.trim(), submitting.value ? { observedAt: observed.value, place: place.value.trim(),
    reference: reference.value.trim(), observation: 'QUARANTINE_CLEARED_IN_WAREHOUSE', attachments: attachments.value } : undefined);
  if (pending.value?.receipt) kind.value = null;
}
</script>
<template>
  <section class="quarantine-workspace" aria-label="实物解隔离核验">
    <el-alert v-if="error && !kind" :title="error" type="error" :closable="false" />
    <el-alert v-if="unresolved" title="原差异操作结果未确认，原请求已保留。" type="warning" :closable="false"><el-button :loading="busy" @click="recover">核对或重试原差异请求</el-button></el-alert>
    <el-alert v-if="pending?.receipt?.commandType.startsWith('RECONCILE_QUARANTINE_')" :title="`${labels[pending.receipt.commandType]}已记录：${statuses[pending.receipt.businessDecision.status] ?? pending.receipt.businessDecision.status}`" type="success" :closable="false" />
    <el-alert v-if="pending?.rejection" :title="`原操作已拒绝：${pending.rejection.message}`" type="warning" :closable="false" />
    <el-button :disabled="busy || !!unresolved" @click="load()">刷新隔离与原事实</el-button>
    <h2>有原实物处置依据的隔离包</h2>
    <p>核对原隔离依据、实物在库及解除条件。登记与提交保留隔离，独立主管批准后重新计算可用性。</p>
    <el-empty v-if="list && !list.candidates.length" description="没有适用的隔离候选" />
    <article v-for="item in list?.candidates" :key="item.baleId" :data-quarantine-bale="item.baleId">
      <p>条码 {{ item.barcode }} · 原隔离 {{ item.dispositionId }}</p>
      <el-button v-if="maker" :disabled="busy || blocked || !!unresolved" @click="open('RECONCILE_QUARANTINE_DISCOVER', item.baleId)">登记解隔离核验</el-button>
    </article>
    <el-button v-if="list?.nextCursor" :disabled="busy" @click="load('candidates')">更多隔离候选</el-button>
    <h2>解隔离案件</h2><el-empty v-if="list && !list.cases.length" description="没有解隔离案件" />
    <article v-for="item in list?.cases" :key="item.caseId" :data-quarantine-case="item.caseId">
      <p>案件 {{ item.caseId }} · {{ statuses[item.status] ?? item.status }}</p>
      <el-button v-if="maker && item.status === 'OPEN'" :disabled="busy || blocked || !!unresolved" @click="open('RECONCILE_QUARANTINE_SUBMIT', item.caseId)">提交解隔离凭据</el-button>
      <el-button v-if="reviewer && item.status === 'REVIEW_READY'" :disabled="busy || blocked || !!unresolved" @click="open('RECONCILE_QUARANTINE_APPROVE', item.caseId)">主管批准解隔离</el-button>
    </article>
    <el-button v-if="list?.nextCaseCursor" :disabled="busy" @click="load('cases')">更多解隔离案件</el-button>
    <el-dialog class="quarantine-dialog" :model-value="!!kind" :title="kind ? labels[kind] : ''" width="680" :close-on-click-modal="false" :close-on-press-escape="!busy && !uploading" :show-close="!busy && !uploading" @close="kind = null">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <template v-if="selected">
        <el-descriptions title="库存与隔离事实" :column="1" border>
          <el-descriptions-item label="条码">{{ selected.context.facts.stock.barcode }}</el-descriptions-item>
          <el-descriptions-item label="周期/账务">{{ selected.context.facts.stock.dispatchCycle }} / {{ selected.context.facts.stock.bookStatus }}</el-descriptions-item>
          <el-descriptions-item label="上游在库">{{ selected.context.facts.stock.upstreamPresent ? '声明在库' : '未声明在库' }}</el-descriptions-item>
          <el-descriptions-item label="占用/出库/流水">{{ selected.context.facts.owners.length }} / {{ selected.context.facts.dispatches.length }} / {{ selected.context.facts.ledger.length }}</el-descriptions-item>
          <el-descriptions-item label="其他阻断">{{ selected.context.facts.stock.blockedReason || '无' }}</el-descriptions-item>
          <el-descriptions-item label="固定快照">{{ selected.context.facts.stock.snapshotId }}</el-descriptions-item>
        </el-descriptions>
        <QuarantineHistory v-if="kind" :history="selected.context.facts.history ?? []" :actor="props.actor" :warehouse="props.warehouse" :disabled="busy || uploading"
          :source-query="`warehouseId=${props.warehouse}&commandType=${kind}&${kind.endsWith('_DISCOVER') ? `baleId=${selected.context.baleId}` : `caseId=${selected.context.case?.caseId}`}`" />
        <el-descriptions title="原隔离与实物处置依据" :column="1" border>
          <el-descriptions-item label="原隔离">{{ selected.context.dispositionId }}</el-descriptions-item>
          <template v-if="selected.context.facts.origin.sourceKind === 'TEMP'">
            <el-descriptions-item label="原处置方式">{{ selected.context.facts.origin.originalReceipt.commandType === 'CLOSE_DISPOSITION' ? '关闭来源后逐包隔离' : '逐包隔离，保留其余作业' }}</el-descriptions-item>
            <el-descriptions-item label="原来源/提交">{{ selected.context.facts.origin.scopeId }} / {{ selected.context.facts.origin.submissionId }}</el-descriptions-item>
            <el-descriptions-item label="原证据集/核验">{{ selected.context.facts.origin.evidenceSetId }} / {{ selected.context.facts.origin.verificationId }}</el-descriptions-item>
          </template>
          <template v-else-if="selected.context.facts.origin.sourceKind === 'PC'">
            <el-descriptions-item label="原PC作业/方案">{{ selected.context.facts.origin.workId }} / {{ selected.context.facts.origin.planId }}</el-descriptions-item>
            <el-descriptions-item label="依据类型">原数据库审批、停止快照和应用记录</el-descriptions-item>
          </template>
          <template v-else>
            <el-descriptions-item label="原恢复包/应用提交">{{ selected.context.facts.origin.recoveryPackageId }} / {{ selected.context.facts.origin.submissionId }}</el-descriptions-item>
            <el-descriptions-item label="依据类型">原实物批准、原机停止签名和应用提交</el-descriptions-item>
            <el-descriptions-item label="原实物应用状态">{{ selected.context.facts.origin.originalReceipt.application.receipt.status === 'DISPOSITIONS_APPLIED' ? '原处置已应用，其他包按原批次继续处理' : '原批次已形成终态回执' }}</el-descriptions-item>
          </template>
          <el-descriptions-item :label="selected.context.facts.origin.sourceKind === 'TEMP' ? '原签名回执' : selected.context.facts.origin.sourceKind === 'PC' ? '原应用回执' : '原实物批准'">{{ selected.context.facts.origin.originalReceipt.receiptId }}</el-descriptions-item>
          <el-descriptions-item label="原处置人/时刻">{{ selected.context.facts.origin.originalReceipt.actor.principalId }} / {{ selected.context.facts.origin.originalReceipt.recordedAt }}</el-descriptions-item>
          <el-descriptions-item label="原处置原因">{{ selected.context.facts.origin.closeReason }}</el-descriptions-item>
          <el-descriptions-item label="原回执摘要">{{ selected.context.facts.origin.originalReceiptSha256 }}</el-descriptions-item>
        </el-descriptions>
        <el-button :disabled="busy || uploading || !kind" @click="kind && source(kind)">{{ selected.context.facts.origin.sourceKind === 'TEMP' ? '下载原隔离签名回执' : selected.context.facts.origin.sourceKind === 'PC' ? '下载原PC审批依据' : '下载原FORMAL处置依据' }}</el-button>
        <p v-if="selected.context.submittedCommand">原实物核验：{{ selected.context.submittedCommand.evidence.place }} · {{ selected.context.submittedCommand.evidence.observedAt }} · {{ selected.context.submittedCommand.evidence.reference }}</p>
        <el-button v-for="(attachment, index) in selected.context.submittedCommand?.evidence.attachments" :key="attachment.attachmentId" :disabled="busy || uploading" @click="download(attachment)">读取原始凭据 {{ index + 1 }}</el-button>
        <p>须核验实物在库且隔离条件已解除。批准保留原处置、结果、库存账和周期，上游缺码、其他占用及账务冲突继续阻止出库。</p>
      </template>
      <el-form label-position="top">
        <template v-if="submitting">
          <el-form-item label="核验时刻（带时区）"><el-input v-model="observed" aria-label="实物核验时刻" :disabled="busy || !!unresolved" /></el-form-item>
          <el-form-item label="实物核验地点"><el-input v-model="place" aria-label="实物核验地点" maxlength="200" :disabled="busy || !!unresolved" /></el-form-item>
          <el-form-item label="实物在库且隔离条件已解除的依据"><el-input v-model="reference" aria-label="实物核验依据" maxlength="500" :disabled="busy || !!unresolved" /></el-form-item>
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
.quarantine-workspace { display: grid; gap: var(--space-4); }
.quarantine-workspace h2 { margin: 0; font-size: 18px; }
.quarantine-workspace p { margin: 0; overflow-wrap: anywhere; line-height: 1.6; }
.quarantine-workspace article { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap;
  gap: var(--space-3); padding: var(--space-4); border: 1px solid var(--separator); border-radius: var(--radius-control); }
.quarantine-workspace > .el-button { justify-self: start; }
:global(.quarantine-dialog.el-dialog) { margin-block: var(--space-4); max-height: calc(100dvh - 2 * var(--space-4)); }
:global(.quarantine-dialog .el-descriptions__content) { overflow-wrap: anywhere; }
</style>
