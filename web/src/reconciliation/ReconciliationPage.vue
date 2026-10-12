<script setup lang="ts">
import WorkspaceLayout from '../WorkspaceLayout.vue';
import PageHeading from '../components/PageHeading.vue';
import ReconciliationWorkspace from './ReconciliationWorkspace.vue';
import { useSessionStore } from '../session';
const session = useSessionStore();
</script>
<template>
  <WorkspaceLayout title="库存差异">
    <PageHeading title="库存差异" subtitle="逐笔核对缺账或返库库存账，由独立主管按原事实和实物凭据批准处理。上游、实物、占用及结果保持可追溯。" />
    <ReconciliationWorkspace v-if="session.identity" :key="`${session.identity.id}-${session.warehouseId}`" :actor="session.identity.id" :warehouse="session.warehouseId" />
  </WorkspaceLayout>
</template>
