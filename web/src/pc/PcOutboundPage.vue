<script setup lang="ts">
import { useRoute } from 'vue-router';
import { useSessionStore } from '../session';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import PcWorkScreen from './PcWorkScreen.vue';
const route = useRoute(); const session = useSessionStore();
</script>
<template>
  <PcWorkScreen v-if="session.identity && session.warehouseId && typeof route.params.id === 'string'" :key="`${session.identity.id}-${session.warehouseId}-${route.params.id}`" :user="session.identity.id" :warehouse="session.warehouseId" :order="route.params.id" />
  <WorkspaceLayout v-else title="PC 手工出库" />
</template>
