<script setup lang="ts">
import type { IndependentReview } from './contracts';
defineProps<{ data: IndependentReview }>();
const names: Record<string, string> = {
  ORDER_STATE: '订单状态阻断', hasAssignment: '存在领取记录', hasPcWork: '存在电脑作业', hasSubmission: '存在提交', hasResult: '存在出库结果',
  BALE_UNKNOWN: '当前仓库没有对应库存包', POSITION_MISSING: '缺少库存状态', UPSTREAM_ABSENT: '上游未声明在库', BOOK_UNAVAILABLE: '账面不可用',
  INVENTORY_BLOCKED: '库存被隔离或阻断', COMMITMENT_EXISTS: '存在有效占用', SNAPSHOT_CHANGED: '库存快照不一致',
  SKU_DELIVERYCODE_MISMATCH: '交货代码不一致', SKU_MODEL_MISMATCH: '型号不一致', SKU_NAME_MISMATCH: '品名不一致', SKU_WEIGHTGRAMS_MISMATCH: '重量不一致',
};
function label(value: string): string { return names[value] ?? value; }
</script>
<template>
  <div v-if="data.section === 'candidates'" class="evidence-list">
    <article v-for="item in data.items" :key="item.id" class="evidence-card">
      <h3>{{ item.orderNo }} · {{ item.vehicleNo }}</h3>
      <p>{{ item.source === 'WEB' ? '正式订单' : 'TEMP订单' }} · 状态 {{ item.status }} · 修订 {{ item.revision }} · 计划 {{ item.plannedTotal }}包</p>
      <p v-for="product in item.products" :key="product.deliveryCode">{{ product.deliveryCode }} / {{ product.model }} / {{ product.name }}：{{ product.plannedCount }}包</p>
      <p v-if="item.blockers.length" role="status">{{ item.blockers.map(label).join('；') }}</p>
      <p v-else>未发现订单作业阻断；仍需实物核验及显式人工关联。</p>
      <details><summary>查看原订单标识</summary><p>{{ item.id }}</p><p>版本 {{ item.rowVersion }} · {{ item.createdAt }}</p></details>
      <details v-if="item.workFacts.length"><summary>查看作业及结果依据</summary>
        <p v-for="(fact, index) in item.workFacts" :key="index">{{ fact.kind }} · {{ fact.status ?? '结果' }} · {{ fact.id ?? `版本 ${fact.version}` }}
          <span v-if="fact.rowVersion"> · 状态版本 {{ fact.rowVersion }}</span><span v-if="fact.workRound"> · 轮次 {{ fact.workRound }}</span></p>
        <p v-for="fact in item.workFacts.filter(value => value.kind === 'ASSIGNMENT')" :key="fact.id">领取设备 {{ fact.deviceId }} · 人员 {{ fact.operatorId }}
          · 代次 {{ fact.epoch }} · 修订 {{ fact.revision }} · 到期 {{ fact.expiresAt }} · 下载确认 {{ fact.downloadAckAt ?? '无' }}</p>
        <p v-for="fact in item.workFacts.filter(value => value.kind === 'RESULT')" :key="fact.version">结果版本 {{ fact.version }} · 原提交 {{ fact.submissionId }}
          · {{ fact.actualCount }}包 / {{ fact.weightGrams }}克 · 扣账标记 {{ fact.inventoryDeducted }}</p>
      </details>
    </article>
  </div>
  <div v-else-if="data.section === 'inventory'" class="evidence-list">
    <article v-for="item in data.items" :key="item.captureId" class="evidence-card">
      <h3>原序 {{ item.sequence }} · {{ item.barcode }}</h3>
      <p>{{ item.original.deliveryCode }} / {{ item.original.model }} / {{ item.original.name }} · {{ item.original.weightGrams }}克</p>
      <p v-if="item.blockers.length" role="status">{{ item.blockers.map(label).join('；') }}</p>
      <p v-else>当前服务端字段一致；实物尚未核对，不代表库存可用或已关联。</p>
      <details v-if="item.server"><summary>查看库存原依据</summary><p>包 {{ item.server.baleId }}</p>
        <p>服务端：{{ item.server.deliveryCode ?? '缺失' }} / {{ item.server.model ?? '缺失' }} / {{ item.server.name ?? '缺失' }} · {{ item.server.weightGrams ?? '缺失' }}克</p>
        <p>上游 {{ item.server.upstreamPresent ? '在库' : '未声明在库' }} · 阻断 {{ item.server.blockedReason ?? '未发现' }} · 状态版本 {{ item.server.positionVersion ?? '缺失' }}</p>
        <p>周期 {{ item.server.dispatchCycle }} · 账面 {{ item.server.bookStatus }}</p>
        <p>占用 {{ item.server.commitmentState ?? '未发现' }} · {{ item.server.commitmentOrderId ?? '' }} · 周期 {{ item.server.commitmentCycle ?? '无' }}</p>
        <p>仓库快照 {{ item.server.snapshotId ?? '缺失' }} · 包快照 {{ item.server.positionSnapshotId ?? '缺失' }}</p></details>
    </article>
  </div>
  <div v-else class="evidence-list">
    <article v-for="item in data.items" :key="`${item.deviceId}/${item.taskId}`" class="evidence-card">
      <h3>{{ item.selectedTask ? '本原任务' : '其他原任务' }} · {{ item.originals.length }}份导出</h3>
      <p v-if="item.closureDivergence" role="alert">同一原任务存在不同结束事实，必须保全核对。</p>
      <p v-if="item.crossTaskConflict" role="alert">{{ item.crossDeviceConflict ? '跨设备' : '同设备不同任务' }}有效条码重叠：{{ item.overlappingBarcodes.join('、') }}</p>
      <p v-else>{{ item.selectedTask ? '同任务多份导出已归并，不计为多设备冲突。' : '未发现与本任务有效条码重叠。' }}</p>
      <details><summary>查看原机、原任务及各原件</summary><p>设备 {{ item.deviceId }}</p><p>任务 {{ item.taskId }}</p>
        <p v-for="original in item.originals" :key="original.relayId">{{ original.relayId }} · {{ original.sourceTrust }} · {{ original.completeBoundaryVerified ? '完整结束已核验' : '完整结束尚未核验' }}
          · 原包 {{ original.archiveSha256 }} · 结束摘要 {{ original.closureSha256 ?? '无' }}</p></details>
    </article>
  </div>
  <p v-if="!data.items.length">此类别没有匹配的已知记录。仍须完成实物核验，未接收的其他设备事实不在本次观察范围。</p>
</template>
<style scoped>
.evidence-list { display: grid; gap: var(--space-4); }
.evidence-card { border: 1px solid var(--separator); border-radius: var(--radius-control); padding: var(--space-4); overflow-wrap: anywhere; }
.evidence-card h3 { margin-top: 0; }
</style>
