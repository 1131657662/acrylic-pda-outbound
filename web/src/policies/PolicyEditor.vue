<script setup lang="ts">
import { ref } from 'vue';
import { request, errorText, ApiError } from '../api';
import { policySchema } from './contracts';
import type { Policy } from './contracts';
const props = defineProps<{ warehouseId: string; current: Policy | null }>();
const emit = defineEmits<{ saved: []; cancel: [] }>();
const choice = ref<string>(props.current ? (props.current.autoDeductInventory ? 'on' : 'off') : '');
const reason = ref(''); const busy = ref(false); const uncertain = ref(false); const error = ref('');
let body = ''; let key = '';
async function save(): Promise<void> {
  if (busy.value || !choice.value || !reason.value.trim()) return;
  if (!uncertain.value) {
    body = JSON.stringify({ expectedVersion: props.current?.version ?? '0', autoDeductInventory: choice.value === 'on', reason: reason.value });
    key = crypto.randomUUID();
  }
  busy.value = true; error.value = '';
  try {
    await request(`/policies?warehouseId=${props.warehouseId}`, policySchema, { method: 'POST', body,
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key } });
    uncertain.value = false; emit('saved');
  } catch (cause) { uncertain.value = !(cause instanceof ApiError) || cause.status >= 500 || cause.status === 401; error.value = errorText(cause); }
  finally { busy.value = false; }
}
</script>
<template>
  <el-dialog :model-value="true" title="设置出库规则" width="620" :close-on-click-modal="false" :show-close="!busy && !uncertain"
    :close-on-press-escape="!busy && !uncertain" @close="emit('cancel')">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="uncertain" title="结果未确认，请重试原规则请求。" type="warning" :closable="false" />
    <el-form label-position="top" :disabled="busy || uncertain">
      <el-form-item label="确认出库后，是否由本系统自动扣库存？">
        <el-radio-group v-model="choice" aria-label="自动扣库存选择">
          <el-radio value="on">开启自动扣库存</el-radio><el-radio value="off">关闭自动扣库存</el-radio>
        </el-radio-group>
      </el-form-item>
      <p class="muted">关闭后仍记录出库、阻止同包重复出库，但不自动写入扣账流水。本系统不会修改上游的 sku 文件。</p>
      <el-form-item label="设置原因"><el-input v-model="reason" aria-label="规则设置原因" maxlength="500" type="textarea" /></el-form-item>
      <p class="muted">本次设置适用于此后发布或重新发布的订单。已经发布的版本保持原规则。</p>
    </el-form>
    <template #footer><el-button :disabled="busy || uncertain" @click="emit('cancel')">取消</el-button>
      <el-button type="primary" :loading="busy" :disabled="!choice || !reason.trim()" @click="save">{{ uncertain ? '重试原规则请求' : '保存规则' }}</el-button></template>
  </el-dialog>
</template>
