<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { z } from 'zod';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import PageHeading from '../components/PageHeading.vue';
import OrderHeaderCorrections from './OrderHeaderCorrections.vue';
import { useSessionStore } from '../session';
const session = useSessionStore(); const route = useRoute(); const router = useRouter();
const input = ref(''); const error = ref('');
const order = computed(() => { const parsed = z.uuid().safeParse(route.params.order); return parsed.success ? parsed.data : ''; });
function open(): void {
  const parsed = z.uuid().safeParse(input.value.trim());
  if (!parsed.success) { error.value = '请输入原订单UUID编号。'; return; }
  error.value = ''; void router.push({ path: `/header-corrections/${parsed.data}`, query: { warehouse: session.warehouseId } });
}
</script>
<template>
  <WorkspaceLayout title="抬头更正">
    <PageHeading title="抬头更正" description="按原订单编号核对本仓库抬头。审批人与申请人分离，撤销由原审批人发起、原申请人确认。" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form v-if="!order" @submit.prevent="open">
      <el-form-item label="原订单编号"><el-input v-model="input" aria-label="原订单编号" maxlength="36" /></el-form-item>
      <el-button native-type="submit" type="primary" :disabled="!input.trim()">读取抬头更正</el-button>
    </el-form>
    <template v-else>
      <p>原订单编号：{{ order }}</p>
      <OrderHeaderCorrections v-if="session.identity" :key="`${session.identity.id}-${session.warehouseId}-${order}`"
        :actor="session.identity.id" :warehouse="session.warehouseId" :order="order" />
    </template>
  </WorkspaceLayout>
</template>
