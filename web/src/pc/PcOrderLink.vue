<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useSessionStore } from '../session';
import { request } from '../api';
import { viewSchema, type Work, workLabels } from './contracts';
const props = defineProps<{ order: string; warehouse: string; version: string; status: string; disabled: boolean }>();
const emit = defineEmits<{ channel: [pc: boolean] }>();
const route = useRoute(); const session = useSessionStore(); const work = ref<Work | null>(null); const loaded = ref(false); let generation = 0;
const canStart = computed(() => session.identity?.permissions.includes('pc:operate') && (props.status === 'READY' || (props.status === 'DRAFT' && session.identity.permissions.includes('order:publish'))));
watch(() => [props.order, props.warehouse, props.version], async () => {
  const current = ++generation; loaded.value = false; work.value = null; emit('channel', false);
  try { const result = await request(`/orders/${props.order}/pc?warehouseId=${props.warehouse}`, viewSchema); if (current === generation) { work.value = result.work; loaded.value = true; emit('channel', !!result.work); } }
  catch { if (current === generation) loaded.value = false; }
}, { immediate: true });
onBeforeUnmount(() => { generation++; });
</script>
<template>
  <el-button v-if="loaded && (work || canStart)" :disabled="disabled" @click="$router.push({ path: `/orders/${order}/pc-outbound`, query: route.query })">{{ work ? work.status === 'ACTIVE' && work.operator === session.identity?.id ? '继续PC录入' : '查看PC记录与结果' : 'PC 手工出库' }}</el-button>
  <span v-if="work" class="muted">{{ workLabels[work.status] }} · {{ work.operatorName }}</span>
</template>
