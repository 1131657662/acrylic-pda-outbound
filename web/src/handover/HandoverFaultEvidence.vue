<script setup lang="ts">
import type { FaultReceipt } from './fault-contracts';
import { attachmentDownload } from './attachment-api';
defineProps<{ receipt: FaultReceipt }>();
</script>
<template>
  <section class="panel fault-evidence" aria-label="主管故障围栏凭据">
    <h2>主管故障围栏凭据</h2>
    <el-alert title="原机没有提交本次停止回执；以下记录由独立主管确认，原设备已禁用。" type="warning" :closable="false" />
    <dl>
      <dt>确认主管</dt><dd>{{ receipt.actor.principalId }}</dd>
      <dt>故障设备</dt><dd>{{ receipt.businessDecision.sourceDeviceId }}</dd>
      <dt>断电保管时间</dt><dd>{{ new Date(receipt.businessDecision.physicalControl.controlledAt).toLocaleString('zh-CN') }}</dd>
      <dt>设备保管位置</dt><dd>{{ receipt.businessDecision.physicalControl.deviceLocation }}</dd>
      <dt>货物位置</dt><dd>{{ receipt.businessDecision.physicalControl.goodsLocation }}</dd>
      <dt>未决输入</dt><dd>{{ receipt.businessDecision.physicalControl.unresolvedInputCount }}</dd>
      <dt>原文摘要</dt><dd>{{ receipt.businessDecision.payloadSha256 }}</dd>
      <dt>原请求编号</dt><dd>{{ receipt.commandId }}</dd>
    </dl>
    <details><summary>可信归档截点</summary>
      <p>归档集合 {{ receipt.businessDecision.archivedEvidence.sourceEvidenceSetId }}</p>
      <p>验证记录 {{ receipt.businessDecision.archivedEvidence.verificationId }}</p>
      <p>采集截至 {{ receipt.businessDecision.archivedEvidence.captureHighWatermark }} · 审计截至 {{ receipt.businessDecision.archivedEvidence.auditThroughSeq }} · 扫描截至 {{ receipt.businessDecision.archivedEvidence.orderScanHighWatermark }}</p>
    </details>
    <div v-if="receipt.businessDecision.attachments?.length" aria-label="已核验原附件">
      <h3>已核验原附件</h3>
      <ul><li v-for="(file, index) in receipt.businessDecision.attachments" :key="file.attachmentId">
        <a :href="attachmentDownload(receipt.businessDecision.caseId, receipt.actor.warehouseId, file.attachmentId)" download>下载原附件 {{ index + 1 }}</a>
        · {{ file.sizeBytes }} 字节<br />摘要 {{ file.evidenceSha256 }}
      </li></ul>
    </div>
    <p>此凭据保留原占用和资料；接收端须另行准备、本人确认及激活。</p>
  </section>
</template>
<style scoped>
.fault-evidence { overflow-wrap: anywhere; }
dt { color: var(--el-text-color-secondary); }
dd { margin-inline-start: 0; margin-block-end: var(--el-font-size-base); }
</style>
