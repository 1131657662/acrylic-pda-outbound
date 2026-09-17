<script setup lang="ts">
import { computed } from 'vue';
import { statusLabel } from './contracts';
const props = defineProps<{ status: string }>();
const tones: Record<string, string> = { DRAFT: 'neutral', READY: 'information', ASSIGNED: 'information', REVIEW_REQUIRED: 'warning',
  RECALL_PENDING: 'warning', CONTINUING: 'warning', COMPLETED: 'success', CANCELLED: 'neutral', LINKED: 'neutral' };
const tone = computed(() => tones[props.status] ?? 'warning');
</script>
<template><span class="order-status" :class="tone"><span aria-hidden="true" class="status-dot" />{{ statusLabel(status) }}</span></template>
