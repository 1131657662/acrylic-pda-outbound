<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue';
import { z } from 'zod';
import { useRoute } from 'vue-router';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import { request, errorText } from '../api';
import { useSessionStore } from '../session';
import { documentSchema, materialSchema } from './contracts';
import type { RescueMaterial } from './contracts';
import RescueMaterialPanel from './RescueMaterialPanel.vue';
import RescueCommand from './RescueCommand.vue';

const session = useSessionStore(); const route = useRoute();
const requestId = ref(typeof route.query.request === 'string' ? route.query.request : '');
const material = ref<RescueMaterial | null>(null); const error = ref(''); const loading = ref(false); const commandBusy = ref(false);
const busy = computed(() => loading.value || commandBusy.value);
const allowed = computed(() => session.identity?.permissions.includes('rescue:authorize'));
const originalOperator = computed(() => material.value ? documentSchema.parse(JSON.parse(material.value.originalText)).operatorId : '');
let generation = 0;
async function load(): Promise<void> {
  if (loading.value || !allowed.value) return;
  const id = requestId.value.trim(); const actor = session.identity?.id; const warehouse = session.warehouseId; const token = ++generation;
  loading.value = true; error.value = ''; material.value = null;
  try {
    if (!z.uuid().safeParse(id).success) throw new Error('请输入完整的原申请UUID');
    const data = await request(`/rescue-requests/${encodeURIComponent(id)}?warehouseId=${warehouse}`, materialSchema);
    if (token !== generation || actor !== session.identity?.id || warehouse !== session.warehouseId) return;
    const doc = documentSchema.parse(JSON.parse(data.originalText));
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(data.originalText));
    const sha = Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, '0')).join('');
    if (token !== generation || actor !== session.identity?.id || warehouse !== session.warehouseId) return;
    if (data.request.requestId !== id || doc.warehouseId !== warehouse || sha !== data.request.requestSha256) throw new Error('原申请身份、仓库或摘要不一致');
    material.value = data;
  } catch (cause) { if (token === generation) error.value = cause instanceof Error ? cause.message : errorText(cause); }
  finally { if (token === generation) loading.value = false; }
}
watch(() => [session.identity?.id, session.warehouseId, allowed.value], () => { generation++; material.value = null; error.value = ''; loading.value = false; });
onUnmounted(() => { generation++; });
</script>
<template>
  <WorkspaceLayout title="主管只读救援" :busy="busy" :warehouse-locked="busy">
    <section class="panel">
      <h1>主管只读救援</h1>
      <p>按原操作员提供的申请编号核对指定人、原机及原单。此入口只管理原件读取授权，不办理出库或换机续作。</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-input v-model="requestId" aria-label="原救援申请编号" placeholder="原PDA显示的申请UUID" maxlength="36" :disabled="busy || !allowed" />
      <el-button type="primary" :disabled="busy || !allowed || !requestId.trim()" @click="load">读取原救援申请</el-button>
      <p v-if="!allowed">没有该仓库的主管救援授权权限，请联系已获授权人员。</p>
    </section>
    <template v-if="material">
      <RescueMaterialPanel :material="material" />
      <RescueCommand :material="material" :original-operator="originalOperator" @updated="commandBusy = false; load()" @busy="commandBusy = $event" />
    </template>
  </WorkspaceLayout>
</template>
