<script setup lang="ts">
import { ref } from 'vue';
import type { QuarantineHistory } from './quarantine-history-contracts';
import { downloadHistoryReceipt } from './quarantine-history-download';
import { useSessionStore } from '../session';
const props = defineProps<{ history: QuarantineHistory[]; actor: string; warehouse: string; disabled: boolean; sourceQuery: string }>();
const session = useSessionStore(); const error = ref(''); const downloading = ref(false);
function assertCurrent(): void {
  if (session.identity?.id !== props.actor || session.warehouseId !== props.warehouse)
    throw new Error('人员或仓库已变化，请重新读取历史依据。');
}
async function download(item: QuarantineHistory, receipt: 'return' | 'restoration'): Promise<void> {
  downloading.value = true; error.value = '';
  try { await downloadHistoryReceipt({ item, receipt, sourceQuery: props.sourceQuery, assertCurrent }); }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '历史原回执读取未确认。'; }
  finally { downloading.value = false; }
}
</script>
<template>
  <section v-if="history.length" class="quarantine-history" aria-label="历史出库与返库依据">
    <h3>历史出库与返库依据</h3>
    <p>逐周期核对原退货或撤销、反向流水与库存恢复批准。本次解隔离保留这些历史事实。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <article v-for="item in history" :key="item.dispatch.dispatchId" :data-history-dispatch="item.dispatch.dispatchId">
      <el-descriptions :title="`原周期 ${item.dispatch.dispatchCycle}`" :column="1" border>
        <el-descriptions-item label="原订单/出库">{{ item.result.orderId }} / {{ item.dispatch.dispatchId }}</el-descriptions-item>
        <el-descriptions-item label="结果版本/净值">{{ item.result.currentResultVersion }} / {{ item.dispatch.count }} 包 / {{ item.dispatch.weightG }} 克</el-descriptions-item>
        <el-descriptions-item label="原退货或撤销">{{ item.reopening.type === 'PHYSICAL_RETURN' ? '实物退货' : '出库撤销' }} · {{ item.reopening.documentId }}</el-descriptions-item>
        <el-descriptions-item label="原周期开放">{{ item.reopening.cycleBefore }} → {{ item.reopening.cycleAfter }}</el-descriptions-item>
        <el-descriptions-item label="原退货批准人/时刻">{{ item.reopening.approvalReceipt.actor.principalId }} / {{ item.reopening.approvalReceipt.recordedAt }}</el-descriptions-item>
        <el-descriptions-item label="库存恢复批准人/时刻">{{ item.stockRestorationReceipt.actor.principalId }} / {{ item.stockRestorationReceipt.recordedAt }}</el-descriptions-item>
      </el-descriptions>
      <p v-for="entry in item.ledger" :key="entry.id">流水 {{ entry.id }} · {{ entry.kind }} · {{ entry.quantityDelta }} 包 / {{ entry.weightDeltaG }} 克</p>
      <el-button :disabled="disabled || downloading" @click="download(item, 'return')">下载原退货或撤销批准回执（周期{{ item.dispatch.dispatchCycle }}）</el-button>
      <el-button :disabled="disabled || downloading" @click="download(item, 'restoration')">下载原库存恢复批准回执（周期{{ item.dispatch.dispatchCycle }}）</el-button>
    </article>
  </section>
</template>
<style scoped>
.quarantine-history, .quarantine-history article { display: grid; gap: var(--space-3); }
.quarantine-history h3, .quarantine-history p { margin: 0; overflow-wrap: anywhere; line-height: 1.6; }
.quarantine-history .el-button { justify-self: start; margin-inline: 0; max-width: 100%; height: auto; white-space: normal; line-height: 1.6; }
</style>
