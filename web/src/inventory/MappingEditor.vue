<script setup lang="ts">
import { reactive, ref } from 'vue';
import { z } from 'zod';
import { request, errorText } from '../api';
import type { Mapping } from './master-contracts';
const props = defineProps<{ warehouseId: string; item: Mapping | null }>();
const emit = defineEmits<{ saved: []; cancel: [] }>();
const form = reactive({ deliveryCode: props.item?.deliveryCode ?? '', expectedVersion: props.item?.version ?? '0',
  model: props.item?.model ?? '', name: props.item?.name ?? '', enabled: props.item?.enabled ?? true, reason: '' });
const busy = ref(false); const error = ref(''); let savedBody = ''; let key = '';
async function save(): Promise<void> {
  if (!/^[A-Z0-9]{3}$/.test(form.deliveryCode) || !form.model.trim() || !form.name.trim() || !form.reason.trim()) {
    error.value = '请填写三位发货代码、型号、名称及变更原因。'; return;
  }
  busy.value = true; error.value = '';
  const body = JSON.stringify(form);
  if (body !== savedBody || !key) { savedBody = body; key = crypto.randomUUID(); }
  try {
    await request(`/delivery-mappings/revisions?warehouseId=${props.warehouseId}`, z.object({ id: z.string() }),
      { method: 'POST', body, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key } });
    emit('saved');
  } catch (cause) { error.value = errorText(cause); }
  finally { busy.value = false; }
}
</script>
<template>
  <el-dialog :model-value="true" :title="item ? '修订代码映射' : '新增代码映射'" width="560" :close-on-click-modal="false" :show-close="!busy" :close-on-press-escape="!busy" @close="emit('cancel')">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form label-position="top" :disabled="busy" @submit.prevent="save">
      <el-form-item label="发货代码"><el-input v-model="form.deliveryCode" aria-label="发货代码" maxlength="3" :disabled="!!item" /></el-form-item>
      <el-form-item label="产品型号"><el-input v-model="form.model" aria-label="产品型号" maxlength="100" /></el-form-item>
      <el-form-item label="产品名称"><el-input v-model="form.name" aria-label="产品名称" maxlength="100" /></el-form-item>
      <el-form-item label="启用状态"><el-switch v-model="form.enabled" aria-label="启用状态" active-text="启用" inactive-text="停用" /></el-form-item>
      <el-form-item label="变更原因"><el-input v-model="form.reason" aria-label="变更原因" type="textarea" maxlength="500" /></el-form-item>
      <p class="muted">新版本保留原映射历史。历史库存快照及原始单据仍使用当时的属性。</p>
      <div class="pagination"><el-button :disabled="busy" @click="emit('cancel')">取消</el-button><el-button type="primary" native-type="submit" :loading="busy">保存映射版本</el-button></div>
    </el-form>
  </el-dialog>
</template>
