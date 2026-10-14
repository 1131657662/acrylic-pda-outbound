<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { z } from 'zod';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import { useSessionStore } from '../session';
import { errorText, request } from '../api';
import { matchesOriginal, reviewSchema } from './contracts';
import type { IndependentReview, ReviewSection } from './contracts';
import IndependentEvidence from './IndependentEvidence.vue';
import PhysicalCheckForm from './PhysicalCheckForm.vue';
import AssociationForm from './AssociationForm.vue';
import ConflictForm from './ConflictForm.vue';
const session = useSessionStore(); const route = useRoute();
const relayId = ref(typeof route.query.relay === 'string' ? route.query.relay : ''); const section = ref<ReviewSection>('candidates');
const data = ref<IndependentReview | null>(null); const busy = ref(false); const error = ref(''); const page = ref(1);
const allowed = computed(() => ['rescue:relay', 'temporary_order:confirm'].every(permission => session.identity?.permissions.includes(permission)));
let generation = 0;
function clear(): void { generation++; data.value = null; error.value = ''; busy.value = false; page.value = 1; }
async function load(next = false): Promise<void> {
  if (busy.value || !allowed.value) return;
  const id = relayId.value.trim(); const warehouse = session.warehouseId; const actor = session.identity?.id;
  const selected = section.value; const cursor = next ? data.value?.nextCursor : null; const oldHash = data.value?.contextSha256;
  if (next && !cursor) return;
  const token = ++generation; busy.value = true; error.value = ''; data.value = null;
  try {
    if (!z.uuid().safeParse(id).success) throw new Error('请输入完整的原件中转UUID');
    const query = new URLSearchParams({ warehouseId: warehouse, section: selected, limit: '20', ...(cursor ? { cursor } : {}) });
    const result = await request(`/original-relays/${encodeURIComponent(id)}/independent-reconciliation?${query}`, reviewSchema);
    if (token !== generation || actor !== session.identity?.id || warehouse !== session.warehouseId) return;
    if (!matchesOriginal(result, { relayId: id, warehouseId: warehouse, section: selected })) throw new Error('原件、仓库或核对类别不一致，请重新读取');
    if (next && oldHash !== result.contextSha256) throw new Error('核对依据已变化，请从第一页重新核对');
    data.value = result; page.value = next ? page.value + 1 : 1;
  } catch (cause) { if (token === generation) error.value = cause instanceof Error ? cause.message : errorText(cause); }
  finally { if (token === generation) busy.value = false; }
}
function choose(value: ReviewSection): void { if (busy.value) return; section.value = value; clear(); void load(); }
watch(() => [session.identity?.id, session.warehouseId, allowed.value, relayId.value], clear);
onUnmounted(() => { generation++; });
</script>
<template>
  <WorkspaceLayout title="独立采集核对" :busy="busy" :warehouse-locked="busy">
    <section class="panel">
      <h1>独立采集核对</h1><el-tag type="warning">待对账 · 未记账</el-tag>
      <p>按已接收原件编号核对原独立采集、完整候选与库存，再由独立主管登记实物观察。</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-input v-model="relayId" aria-label="独立原件中转编号" placeholder="电脑中转接收回执中的UUID" maxlength="36" :disabled="busy || !allowed" />
      <el-button type="primary" :disabled="busy || !allowed" @click="load(false)">读取完整核对依据</el-button>
      <p v-if="!allowed">需要该仓库的原件中转及临时单确认双权限。</p>
      <div class="review-sections" aria-label="核对类别">
        <el-button :type="section === 'candidates' ? 'primary' : 'default'" :aria-pressed="section === 'candidates'" :disabled="busy || !allowed" @click="choose('candidates')">全部候选</el-button>
        <el-button :type="section === 'inventory' ? 'primary' : 'default'" :aria-pressed="section === 'inventory'" :disabled="busy || !allowed" @click="choose('inventory')">有效包库存</el-button>
        <el-button :type="section === 'peers' ? 'primary' : 'default'" :aria-pressed="section === 'peers'" :disabled="busy || !allowed" @click="choose('peers')">其他独立原件</el-button>
      </div>
    </section>
    <section v-if="data" class="panel review-result">
      <h2>{{ data.original.task.vehicleNo }} · 原任务待对账</h2>
      <p>有效 {{ data.original.closure.active }}包 / VOID {{ data.original.closure.void }}包 / 短装 {{ data.original.closure.shortfall }}包</p>
      <p v-if="data.original.closure.shortfallReason">结束原因：{{ data.original.closure.shortfallReason }}</p>
      <p>历史签发、原登记设备钥及完整结束已核验。下方实物与人工关联回执分别显示登记结果。</p>
      <p>候选 {{ data.summary.candidateTotal }}单 · 库存有阻断 {{ data.summary.inventoryBlocked }}包 · 跨任务重叠 {{ data.summary.crossTaskConflicts }}项</p>
      <p>固定候选日期 {{ data.summary.fromDate }} 至 {{ data.summary.throughDate }}。本次只观察已接收原件，其他设备未外置事实仍须核对。</p>
      <p v-if="data.review.currentSubjectState !== 'UNCHANGED'">原人员、设备或授权现状已变化；历史原件保留，不授予新增采集权。</p>
      <IndependentEvidence :data="data" />
      <p>第 {{ page }} 页 · 本类别共 {{ data.total }}项，完整集合参与摘要</p>
      <el-button :disabled="busy" @click="load(false)">刷新并从第一页核对</el-button>
      <el-button :disabled="busy || !data.hasMore" @click="load(true)">下一页</el-button>
      <details><summary>查看原件及核对摘要</summary><p>原任务 {{ data.original.taskId }}</p><p>原设备 {{ data.original.deviceId }}</p>
        <p>原采集人 {{ data.original.operatorId }}</p><p>原包 {{ data.original.archiveSha256 }}</p><p>核对 {{ data.contextSha256 }}</p></details>
      <PhysicalCheckForm :key="`${data.original.relayId}/${data.contextSha256}`" :data="data" />
      <ConflictForm :key="`conflict/${data.original.relayId}/${data.contextSha256}`" :data="data" />
      <AssociationForm :key="`association/${data.original.relayId}/${data.contextSha256}`" :data="data" />
    </section>
  </WorkspaceLayout>
</template>
<style scoped>
.review-sections { display: flex; flex-wrap: wrap; gap: var(--space-2); margin-top: var(--space-4); }
.review-sections :deep(.el-button + .el-button) { margin-left: 0; }
.review-result { overflow-wrap: anywhere; }
</style>
