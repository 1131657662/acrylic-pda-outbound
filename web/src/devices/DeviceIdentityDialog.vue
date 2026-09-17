<script setup lang="ts">
import { ref } from 'vue';
import { localTime } from '../orders/contracts';
import type { Device } from './contracts';
defineProps<{ device: Device }>();
const emit = defineEmits<{ close: [] }>();
const message = ref('');
async function copy(value: string): Promise<void> {
  try { await navigator.clipboard.writeText(value); message.value = '已复制设备ID，请填入对应PDA。'; }
  catch { message.value = '浏览器未允许复制，请在下方选择并复制设备ID。'; }
}
</script>
<template>
  <el-dialog :model-value="true" title="PDA 设备身份" width="min(660px, 94vw)" @close="emit('close')">
    <p><strong>{{ device.name }}</strong> · {{ device.deviceCode }}</p>
    <p class="muted">{{ device.status === 'ACTIVE' ? '已启用' : '已停用' }} · 登记时间 {{ localTime(device.createdAt) }}</p>
    <el-form label-position="top">
      <el-form-item label="设备ID（填入对应PDA）"><el-input :model-value="device.id" readonly aria-label="设备ID" /></el-form-item>
      <el-form-item label="登记公钥"><el-input :model-value="device.publicKey" readonly aria-label="登记公钥" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="公钥核对摘要"><code class="device-key">{{ device.keySha256 }}</code></el-form-item>
    </el-form>
    <p v-if="message" role="status">{{ message }}</p>
    <template #footer><el-button @click="emit('close')">关闭</el-button><el-button type="primary" @click="copy(device.id)">复制设备ID</el-button></template>
  </el-dialog>
</template>
<style scoped>
p { margin-bottom: 16px; }
.device-key { overflow-wrap: anywhere; }
</style>
