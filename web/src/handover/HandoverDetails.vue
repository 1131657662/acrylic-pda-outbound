<script setup lang="ts">
import type { CaseContext } from './contracts';
import { stateLabel, factLabel } from './contracts';
import HandoverFaultAction from './HandoverFaultAction.vue';
defineProps<{ detail: CaseContext }>();
const emit = defineEmits<{ refresh: [] }>();
</script>
<template>
  <section class="panel handover-details" aria-label="交接详情">
    <h2>交接处理进度</h2>
    <p><el-tag type="info">{{ stateLabel(detail.context.status) }}</el-tag> 作业轮次 {{ detail.context.fromWorkRound }} → {{ detail.context.toWorkRound }}</p>
    <el-alert title="以下是服务器记录的处理状态，接收端能否继续装车仍需核对本机资料与资格。" type="info" :closable="false" />
    <dl>
      <dt>原机停止</dt><dd>{{ detail.context.stopState === 'REQUESTED' ? '已申请停止，尚未收到停止证明' : stateLabel(detail.context.stopState) }}</dd>
      <dt>原提交结果</dt><dd>{{ stateLabel(detail.context.originalResultState) }}</dd>
      <dt>资料完整</dt><dd>{{ stateLabel(detail.context.integrityState) }}</dd>
      <dt>实物核验</dt><dd>{{ stateLabel(detail.context.physicalState) }}</dd>
      <dt>原申请原因</dt><dd>{{ detail.originalCommand.reason || '未填写' }}</dd>
    </dl>
    <details><summary>来源与人员凭据</summary>
      <p>案件：{{ detail.context.caseId }} · 记录版本 {{ detail.context.caseVersion }}</p>
      <p>原来源：{{ detail.context.scopeId }} · {{ detail.context.sourceKind === 'FORMAL' ? '正式来源' : '临时来源' }}</p>
      <p>原申请人：{{ detail.context.requestedBy }}</p>
      <p>批准人：{{ factLabel(detail.context.approvedBy) }}</p>
      <p>接收人员：{{ factLabel(detail.context.targetUserId) }}</p>
      <p>接收设备：{{ factLabel(detail.context.targetDeviceId) }}</p>
      <p>原申请凭据：{{ detail.originalCommand.commandId }}</p>
    </details>
  </section>
  <HandoverFaultAction :detail="detail" @refresh="emit('refresh')" />
</template>
<style scoped>
.handover-details { overflow-wrap: anywhere; }
dt { color: var(--el-text-color-secondary); }
dd { margin-inline-start: 0; margin-block-end: var(--el-font-size-base); }
</style>
