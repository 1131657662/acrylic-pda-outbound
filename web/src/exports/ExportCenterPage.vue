<script setup lang="ts">
import { useSessionStore } from '../session';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import PageHeading from '../components/PageHeading.vue';
import ExportCenterContent from './ExportCenterContent.vue';
const session = useSessionStore();
</script>
<template>
  <WorkspaceLayout title="导出中心">
    <PageHeading title="导出中心" subtitle="按原出库日查询最新净值，生成和下载固定成员与版本的文件。" />
    <ExportCenterContent v-if="session.identity && session.warehouseId && session.identity.permissions.includes('order:read')"
      :key="`${session.identity.id}:${session.warehouseId}`" :actor="session.identity.id" :warehouse="session.warehouseId" />
  </WorkspaceLayout>
</template>
