<script setup lang="ts">
import type { IndependentReview } from './contracts';
import type { AssociationShortfall } from './association-shortfall';
defineProps<{ facts: AssociationShortfall; data: IndependentReview; disabled: boolean }>();
const confirmed = defineModel<boolean>('confirmed', { required: true });
const reason = defineModel<string>('reason', { required: true });
</script>
<template>
  <section v-if="facts.needed" class="shortfall-approval" aria-label="关联短装批准">
    <h3>短装须主管明确批准</h3>
    <p>原任务短装 {{ facts.original }}包；目标计划短装 {{ facts.target }}包。</p>
    <p>原短装原因：{{ data.original.closure.shortfallReason || '原任务无短装' }}</p>
    <el-checkbox v-model="confirmed" :disabled="disabled">确认批准原任务与目标两种短装差额</el-checkbox>
    <el-input v-model="reason" aria-label="关联短装批准说明" type="textarea" maxlength="500" :disabled="disabled" />
    <p>批准说明随本次关联保存；库存、占用与实物仍须分别核对。</p>
  </section>
</template>
<style scoped>
.shortfall-approval { margin-block: var(--space-4); padding: var(--space-4); border: 1px solid var(--separator); border-radius: var(--radius-control); }
.shortfall-approval :deep(.el-checkbox) { height: auto; white-space: normal; margin-bottom: var(--space-2); }
.shortfall-approval :deep(.el-checkbox__label) { white-space: normal; overflow-wrap: anywhere; }
</style>
