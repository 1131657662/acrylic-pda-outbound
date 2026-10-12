<script setup lang="ts">
import WorkspaceLayout from '../WorkspaceLayout.vue';
import PageHeading from '../components/PageHeading.vue';
import ReconciliationWorkspace from './ReconciliationWorkspace.vue';
import QuarantineWorkspace from './QuarantineWorkspace.vue';
import { ref } from 'vue';
import { useSessionStore } from '../session';
const session = useSessionStore();
const quarantine = ref(false);
</script>
<template>
  <WorkspaceLayout title="库存差异">
    <PageHeading title="库存差异" subtitle="逐笔核对缺账或返库库存账，由独立主管按原事实和实物凭据批准处理。上游、实物、占用及结果保持可追溯。" />
    <el-radio-group v-model="quarantine" aria-label="库存差异类型"><el-radio-button :value="false">库存账核对</el-radio-button><el-radio-button :value="true">实物解隔离</el-radio-button></el-radio-group>
    <template v-if="session.identity">
      <QuarantineWorkspace v-if="quarantine" :key="`quarantine-${session.identity.id}-${session.warehouseId}`" :actor="session.identity.id" :warehouse="session.warehouseId" />
      <ReconciliationWorkspace v-else :key="`${session.identity.id}-${session.warehouseId}`" :actor="session.identity.id" :warehouse="session.warehouseId" />
    </template>
  </WorkspaceLayout>
</template>
