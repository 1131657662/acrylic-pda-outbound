<script setup lang="ts">
import { ref } from 'vue';
import type { Action } from './pending';
export interface ExceptionConfirmation { title: string; explanation: string; action: Action; body: object }
const props = defineProps<{ confirmation: ExceptionConfirmation; disabled: boolean; pending: boolean; error: string }>();
const emit = defineEmits<{ confirm: [action: Action, body: object]; cancel: []; recover: [] }>();
const evidence = ref(''); const checked = ref(false);
function confirm(): void {
  if (props.disabled || !checked.value || !evidence.value.trim()) return;
  emit('confirm', props.confirmation.action, { ...props.confirmation.body, reason: evidence.value.trim(), physicalEvidence: evidence.value.trim(), physicalConfirmed: true });
}
</script>
<template>
  <el-dialog :model-value="true" :title="confirmation.title" class="pc-exception-dialog" :close-on-click-modal="false" :close-on-press-escape="!disabled" :show-close="!disabled" @close="emit('cancel')">
    <p>{{ confirmation.explanation }}</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form-item label="现场核验记录与原因"><el-input v-model="evidence" aria-label="现场核验记录与原因" type="textarea" :rows="4" maxlength="1000" :disabled="disabled" /></el-form-item>
    <el-checkbox v-model="checked" :disabled="disabled">已核对本单、全部实物及人员交接影响</el-checkbox>
    <template #footer><el-button :disabled="disabled" @click="emit('cancel')">返回核对</el-button><el-button v-if="pending" @click="emit('recover')">恢复原PC请求</el-button><el-button v-else type="primary" :disabled="disabled || !checked || !evidence.trim()" @click="confirm">{{ confirmation.title }}</el-button></template>
  </el-dialog>
</template>
