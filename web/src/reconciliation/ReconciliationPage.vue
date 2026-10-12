<script setup lang="ts">
import WorkspaceLayout from '../WorkspaceLayout.vue';
import PageHeading from '../components/PageHeading.vue';
import ReconciliationWorkspace from './ReconciliationWorkspace.vue';
import { useSessionStore } from '../session';
const session = useSessionStore();
</script>
<template>
  <WorkspaceLayout title="库存差异">
    <PageHeading title="库存差异" subtitle="以原出库和实际凭据逐笔核对缺账，由另一位主管批准补扣。上游、实物、占用及结果保持可追溯。" />
    <ReconciliationWorkspace v-if="session.identity" :key="`${session.identity.id}-${session.warehouseId}`" :actor="session.identity.id" :warehouse="session.warehouseId" />
  </WorkspaceLayout>
</template>
