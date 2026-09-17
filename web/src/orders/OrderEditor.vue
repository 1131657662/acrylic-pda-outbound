<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { request, errorText, ApiError } from '../api';
import { optionsSchema, receiptSchema } from './contracts';
import type { Detail, Product, PlanLine } from './contracts';
import OrderProductPicker from './OrderProductPicker.vue';
import { currentPolicySchema } from '../policies/contracts';
import type { Policy } from '../policies/contracts';
const props = defineProps<{ warehouseId: string; detail: Detail | null }>();
const emit = defineEmits<{ saved: [id: string]; cancel: [] }>();
const form = reactive({ vehicleNo: props.detail?.plan.vehicleNo ?? '', containerNo: props.detail?.plan.containerNo ?? '',
  notes: props.detail?.plan.notes ?? '', reason: '' });
const lines = ref<PlanLine[]>(props.detail?.lines.map((line) => ({ deliveryCode: line.deliveryCode, plannedCount: line.plannedCount })) ?? []);
const labels = reactive(new Map(props.detail?.lines.map((line) => [line.deliveryCode, `${line.model} · ${line.name}`])));
const baseline = ref<Baseline | null>(null);
const policy = ref<Policy | null>(null);
const republishing = props.detail?.order.status === 'READY';
type Baseline = { activeSnapshotId: string | null; mappingRevision: string };
const busy = ref(false); const loading = ref(true); const error = ref(''); const uncertain = ref(false);
const total = computed(() => lines.value.reduce((sum, line) => sum + (line.plannedCount ?? 0), 0));
let key = ''; let savedBody = ''; let savedPath = '';
onMounted(async () => {
  try {
    baseline.value = (await request(`/orders/products?warehouseId=${props.warehouseId}&limit=1`, optionsSchema)).baseline;
    if (republishing) policy.value = (await request(`/policies/current?warehouseId=${props.warehouseId}`, currentPolicySchema)).policy;
  }
  catch (cause) { error.value = errorText(cause); }
  finally { loading.value = false; }
});
function add(product: Product): void {
  if (lines.value.some((line) => line.deliveryCode === product.deliveryCode)) { error.value = '此产品已添加，请修改对应计划包数。'; return; }
  error.value = ''; labels.set(product.deliveryCode, `${product.model} · ${product.name}`);
  lines.value.push({ deliveryCode: product.deliveryCode, plannedCount: 1 });
}
function prepare(): boolean {
  if (!form.vehicleNo.trim() || !lines.value.length || lines.value.some((line) => !Number.isInteger(line.plannedCount) || (line.plannedCount ?? 0) <= 0)) {
    error.value = '请填写车号，添加产品，并为每个产品填写正整数计划包数。'; return false;
  }
  if (props.detail && !form.reason.trim()) { error.value = '请填写修订原因。'; return false; }
  if (republishing && !policy.value) { error.value = '重新发布需要已配置的出库规则。'; return false; }
  if (!baseline.value?.activeSnapshotId) { error.value = '当前仓库还没有已发布库存，请先发布库存版本。'; return false; }
  const plan = { vehicleNo: form.vehicleNo, containerNo: form.containerNo, notes: form.notes, lines: lines.value,
    expectedActiveSnapshot: baseline.value.activeSnapshotId, expectedMappingRevision: baseline.value.mappingRevision };
  const body = JSON.stringify(props.detail ? { expectedVersion: props.detail.order.rowVersion, reason: form.reason, plan,
    ...(republishing ? { expectedPolicyVersion: policy.value?.version } : {}) } : plan);
  if (body !== savedBody || !key) { savedBody = body; key = crypto.randomUUID(); }
  savedPath = `/orders${props.detail ? `/${props.detail.order.id}/revisions` : ''}?warehouseId=${props.warehouseId}`;
  return true;
}
async function save(): Promise<void> {
  if (busy.value || loading.value) return;
  if (!uncertain.value && !prepare()) return;
  busy.value = true; error.value = '';
  try {
    const result = await request(savedPath, receiptSchema, { method: 'POST', body: savedBody,
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key } });
    uncertain.value = false; emit('saved', result.orderId);
  } catch (cause) {
    uncertain.value = !(cause instanceof ApiError) || cause.status >= 500 || cause.status === 401;
    error.value = errorText(cause);
  } finally { busy.value = false; }
}
</script>
<template>
  <el-dialog :model-value="true" :title="republishing ? '修订并重新发布订单' : detail ? '修订订单草稿' : '新建装车订单'" width="760" :close-on-click-modal="false"
    :close-on-press-escape="!busy && !uncertain" :show-close="!busy && !uncertain" @close="emit('cancel')">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="uncertain" title="保存结果暂未确认。请重试原请求，成功后再修改计划。" type="warning" :closable="false" />
    <el-form v-loading="loading" label-position="top" :disabled="busy || uncertain || loading" @submit.prevent="save">
      <div class="order-form-pair"><el-form-item label="车号（必填）"><el-input v-model="form.vehicleNo" aria-label="车号" maxlength="40" /></el-form-item>
        <el-form-item label="柜号（选填）"><el-input v-model="form.containerNo" aria-label="柜号" maxlength="40" /></el-form-item></div>
      <el-form-item label="产品计划"><OrderProductPicker :warehouse-id="warehouseId" style="width: 100%" @select="add" /></el-form-item>
      <el-table :data="lines" empty-text="请先添加产品">
        <el-table-column prop="deliveryCode" label="代码" width="75" />
        <el-table-column label="型号 / 名称" min-width="180"><template #default="{ row }">{{ labels.get(row.deliveryCode) }}</template></el-table-column>
        <el-table-column label="计划包数" width="195"><template #default="{ row }"><el-input-number v-model="row.plannedCount" :aria-label="`${row.deliveryCode}计划包数`" :min="1" :max="2147483647" :precision="0" :disabled="busy || uncertain" /></template></el-table-column>
        <el-table-column width="70"><template #default="{ $index }"><el-button link type="danger" @click="lines.splice($index, 1)">移除</el-button></template></el-table-column>
      </el-table>
      <p class="order-total">计划合计 <strong>{{ total }}</strong> 包</p>
      <el-form-item label="装车备注"><el-input v-model="form.notes" aria-label="装车备注" type="textarea" maxlength="1000" show-word-limit /></el-form-item>
      <el-form-item v-if="detail" label="修订原因"><el-input v-model="form.reason" aria-label="修订原因" maxlength="500" /></el-form-item>
      <p v-if="republishing && policy" class="muted">将重新发布并固定规则 V{{ policy.version }}，自动扣库存{{ policy.autoDeductInventory ? '开启' : '关闭' }}；保留原发布记录。</p>
      <p class="muted">{{ republishing ? '保存后仍为待领取。' : '保存为草稿。' }}各产品分别校验计划包数；修订按当前库存和映射固定新版本，保留历史。</p>
    </el-form>
    <template #footer><el-button :disabled="busy || uncertain" @click="emit('cancel')">取消</el-button>
      <el-button type="primary" :loading="busy" :disabled="loading" @click="save">{{ uncertain ? '重试原保存请求' : republishing ? '保存并重新发布' : '保存草稿' }}</el-button></template>
  </el-dialog>
</template>
<style scoped>
.order-form-pair { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.order-total { text-align: right; padding: 16px 0; }
.order-total strong { font-size: 24px; }
.el-alert { margin-bottom: 16px; }
</style>
