<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { request, errorText, ApiError } from '../api';
import { currentPolicySchema } from '../policies/contracts';
import type { Policy } from '../policies/contracts';
import { receiptSchema } from './contracts';
import type { Detail } from './contracts';
const props = defineProps<{ warehouseId: string; detail: Detail }>();
const emit = defineEmits<{ saved: []; cancel: [] }>();
const policy = ref<Policy | null>(null); const busy = ref(false); const loading = ref(true);
const reason = ref(''); const error = ref(''); const uncertain = ref(false);
let body = ''; let key = '';
onMounted(async () => {
  try { policy.value = (await request(`/policies/current?warehouseId=${props.warehouseId}`, currentPolicySchema)).policy; }
  catch (cause) { error.value = errorText(cause); } finally { loading.value = false; }
});
async function publish(): Promise<void> {
  if (busy.value || !policy.value || !reason.value.trim()) return;
  if (!uncertain.value) {
    body = JSON.stringify({ expectedVersion: props.detail.order.rowVersion, expectedPolicyVersion: policy.value.version, reason: reason.value });
    key = crypto.randomUUID();
  }
  busy.value = true; error.value = '';
  try {
    await request(`/orders/${props.detail.order.id}/publish?warehouseId=${props.warehouseId}`, receiptSchema,
      { method: 'POST', body, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key } });
    uncertain.value = false; emit('saved');
  } catch (cause) { uncertain.value = !(cause instanceof ApiError) || cause.status >= 500 || cause.status === 401; error.value = errorText(cause); }
  finally { busy.value = false; }
}
</script>
<template>
  <el-dialog :model-value="true" title="发布装车订单" width="580" :close-on-click-modal="false" :show-close="!busy && !uncertain"
    :close-on-press-escape="!busy && !uncertain" @close="emit('cancel')">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="uncertain" title="发布结果未确认，请重试原发布请求。" type="warning" :closable="false" />
    <div v-loading="loading"><h3>{{ detail.plan.vehicleNo }} · V{{ detail.plan.revision }} · {{ detail.plan.plannedTotal }} 包</h3>
      <p v-if="policy">固定规则 V{{ policy.version }}：自动扣库存<strong>{{ policy.autoDeductInventory ? '开启' : '关闭' }}</strong></p>
      <el-alert v-else-if="!loading" title="请先在“出库规则”中完成设置。" type="warning" :closable="false" />
      <p class="muted">发布后进入待领取。计划单生成任务同时建立；库存候选不足仍需现场核对，发布不会预留具体包。</p>
      <el-input v-model="reason" aria-label="发布原因" placeholder="填写发布原因" maxlength="500" :disabled="busy || uncertain" />
    </div>
    <template #footer><el-button :disabled="busy || uncertain" @click="emit('cancel')">取消</el-button>
      <el-button type="primary" :loading="busy" :disabled="loading || !policy || !reason.trim()" @click="publish">{{ uncertain ? '重试原发布请求' : '确认发布' }}</el-button></template>
  </el-dialog>
</template>
