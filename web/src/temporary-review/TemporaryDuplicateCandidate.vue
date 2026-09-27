<script setup lang="ts">
import { localTime } from '../orders/contracts';
import { duplicateReason, type DuplicateCandidate } from './duplicate-contracts';
import TemporaryCommandAction from './TemporaryCommandAction.vue';
const props = defineProps<{ candidate: DuplicateCandidate; submissionId: string; warehouseId: string; contextSha256: string }>();
function unavailableReason(): string {
  if (props.candidate.blockers.length) {
    return `当前候选存在不可关联条件（${props.candidate.blockers.map(duplicateReason).join('、')}），不能发起人工关联。`;
  }
  return '服务端当前标记该候选不可关联：人工关联尚未开放，须先由业主授权并同步契约。';
}
</script>
<template>
  <article class="panel">
    <h3>{{ candidate.order.orderNo }} · {{ candidate.order.vehicleNo }}</h3>
    <p>柜号：{{ candidate.order.containerNo || '未填写' }} · 状态 {{ candidate.order.status }} · 计划 V{{ candidate.order.revision }} · 记录版本 {{ candidate.order.rowVersion }}</p>
    <p>服务器建单：{{ localTime(candidate.order.createdAt) }} · 最近变更：{{ localTime(candidate.order.updatedAt) }}</p>
    <p>候选信号：{{ candidate.signals.map(duplicateReason).join('、') }}。疑似重复，待人工核对。</p>
    <ul v-if="candidate.blockers.length"><li v-for="reason in candidate.blockers" :key="reason">{{ duplicateReason(reason) }}</li></ul>
    <p v-else>当前对照未发现上述不可关联条件；仍须核对完整版本、占用与唯一提交。本页不能关联或确认出库。</p>
    <el-table :data="candidate.products" row-key="deliveryCode">
      <el-table-column prop="deliveryCode" label="发货代码" width="100" /><el-table-column prop="model" label="型号" min-width="120" />
      <el-table-column prop="name" label="品名" min-width="150" /><el-table-column prop="plannedCount" label="正式计划包数" width="140" align="right" />
    </el-table>
    <details class="file-proof"><summary>候选版本凭据</summary>
      <p>正式订单 {{ candidate.order.id }} · 计划记录 {{ candidate.order.revisionId }}</p>
      <p>库存 {{ candidate.order.inventorySnapshotId }} · 映射版本 {{ candidate.order.mappingRevision }}</p>
    </details>
    <TemporaryCommandAction kind="LINK" :submission-id="submissionId" :warehouse-id="warehouseId"
      :candidate-context-sha256="contextSha256" :available="candidate.linkAvailable" :unavailable-reason="unavailableReason()"
      label="人工关联到该正式单" reason-required
      hint="关联会在同一事务内把本临时批次的规范单改指该正式单、写入映射版本+1，并迁移仍处于预留状态的占用行。请先核对车号、产品与计划包数完全一致。" />
  </article>
</template>
