<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import type { FaultOwner } from './pending-fault';
import type { AttachmentRef } from './attachment-contracts';
import { useFaultAttachments } from './useFaultAttachments';
const props = defineProps<{ owner: FaultOwner; locked: boolean; available: boolean }>();
const emit = defineEmits<{ change: [references: AttachmentRef[], ready: boolean, busy: boolean] }>();
const { rows, busy, error, restore, select } = useFaultAttachments(() => props.owner);
const references = computed(() => rows.value.flatMap(row => row.reference ? [row.reference] : []));
const ready = computed(() => !error.value && references.value.length === rows.value.length && !busy.value && (props.available || rows.value.length === 0));
function changed(event: Event): void {
  if (props.locked || busy.value || !(event.target instanceof HTMLInputElement)) return;
  const selected = Array.from(event.target.files ?? []); event.target.value = ''; void select(selected);
}
watch([rows, busy, error, ready], () => emit('change', references.value, ready.value, busy.value), { deep: true });
onMounted(() => { restore(); emit('change', references.value, ready.value, false); });
</script>
<template>
  <section class="fault-attachments" aria-label="故障附件上传区">
    <h3>故障凭据附件（可选）</h3>
    <p>单个最多 10MiB，单次总量最多 50MiB，单条凭据最多 5 个附件。选择后按原键上传；刷新后请重选尚未完成的原文件。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="!available">附件上传暂未开放；已有原上传记录继续保留。</p>
    <label v-if="available">选择或重选原附件 <input type="file" multiple aria-label="故障凭据附件" :disabled="locked || busy" @change="changed" /></label>
    <p v-if="busy" role="status">正在核对并上传原附件，请稍候。</p>
    <ul v-if="rows.length">
      <li v-for="row in rows" :key="row.key">{{ row.name }} · {{ row.size }} 字节 · {{ row.reference ? '原件已保存' : '请重选此原文件继续上传' }}<br />摘要 {{ row.sha }}</li>
    </ul>
  </section>
</template>
<style scoped>
.fault-attachments { overflow-wrap: anywhere; }
input { display: block; max-width: 100%; margin-block: var(--el-font-size-base); }
li { margin-block-end: var(--el-font-size-base); }
</style>
