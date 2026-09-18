<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router';
import CursorPager from '../components/CursorPager.vue';
import QueryFeedback from '../components/QueryFeedback.vue';
import PageHeading from '../components/PageHeading.vue';
import { useCursorPage } from '../useCursorPage';
import { request, errorText } from '../api';
import { useSessionStore } from '../session';
import { importSchema, importPageSchema, localTime, statusLabel } from './contracts';
import type { ImportSummary } from './contracts';
import ImportDetail from './ImportDetail.vue';
import WorkspaceLayout from '../WorkspaceLayout.vue';

const session = useSessionStore();
const route = useRoute(); const router = useRouter();
const detailId = computed(() => typeof route.query.import === 'string' ? route.query.import : '');
const detailBusy = ref(false); const detailError = ref(''); const publishing = ref(false);
let listTop = 0; let tableTop = 0; let source: HTMLElement | null = null; let detailGeneration = 0;
const selectedFile = ref<File | null>(null);
const uploadKey = ref('');
const uploading = ref(false);
const error = ref('');
const selected = ref<ImportSummary | null>(null);
const canImport = computed(() => session.identity?.permissions.includes('inventory:import'));
const paging = useCursorPage(async (cursor) => {
  if (!session.warehouseId) return { items: [], nextCursor: null };
  const params = new URLSearchParams({ warehouseId: session.warehouseId, limit: '10', cursor });
  return request(`/inventory/imports?${params}`, importPageSchema);
});
const { rows: history, busy: loading, error: queryError, page, nextCursor } = paging;
function openDetail(item: ImportSummary, event?: Event): void {
  source = event?.currentTarget instanceof HTMLElement ? event.currentTarget : null;
  listTop = document.querySelector('.page-scroll')?.scrollTop ?? 0;
  tableTop = document.querySelector('.auxiliary-stack .el-scrollbar__wrap')?.scrollTop ?? 0;
  if (selected.value?.id !== item.id) selected.value = item;
  void router.push({ query: { ...route.query, warehouse: session.warehouseId, import: item.id } });
}
async function loadDetail(): Promise<void> {
  const ticket = ++detailGeneration; detailError.value = ''; detailBusy.value = false;
  if (!detailId.value) {
    await nextTick(); await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
    if (ticket !== detailGeneration) return;
    source?.focus({ preventScroll: true });
    document.querySelector('.auxiliary-stack .el-scrollbar__wrap')?.scrollTo(0, tableTop);
    document.querySelector('.page-scroll')?.scrollTo(0, listTop); return;
  }
  if (selected.value?.id !== detailId.value) {
    selected.value = null; detailBusy.value = true;
    try {
      const item = await request(`/inventory/imports/${encodeURIComponent(detailId.value)}?warehouseId=${session.warehouseId}`, importSchema);
      if (ticket === detailGeneration) selected.value = item;
    } catch (cause) { if (ticket === detailGeneration) detailError.value = errorText(cause); }
    finally { if (ticket === detailGeneration) detailBusy.value = false; }
  }
  await nextTick(); if (ticket === detailGeneration) document.querySelector('.page-scroll')?.scrollTo(0, 0);
}
function back(): void { const query = { ...route.query }; delete query.import; void router.push({ query }); }
function chooseFile(event: Event): void {
  if (!(event.target instanceof HTMLInputElement)) return;
  selectedFile.value = event.target.files?.[0] ?? null;
  uploadKey.value = crypto.randomUUID(); error.value = '';
}
async function upload(): Promise<void> {
  const file = selectedFile.value;
  if (!file || uploading.value) return;
  if (file.size > 2 * 1024 * 1024) { error.value = '文件不得超过 2MiB。'; return; }
  uploading.value = true; error.value = '';
  const warehouse = session.warehouseId;
  const body = new FormData(); body.set('file', file); body.set('warehouseId', warehouse);
  try {
    const result = await request('/inventory/imports', importSchema, { method: 'POST', body, headers: { 'Idempotency-Key': uploadKey.value } });
    if (warehouse === session.warehouseId) { uploading.value = false; openDetail(result); await paging.reset(); }
  } catch (cause) { error.value = errorText(cause); }
  finally { uploading.value = false; }
}
function published(item: ImportSummary): void {
  if (item.warehouseId !== session.warehouseId) return;
  selected.value = item; void paging.reload();
}
watch(() => session.warehouseId, () => {
  selected.value = null; uploadKey.value = crypto.randomUUID(); listTop = 0; tableTop = 0; source = null;
  void paging.reset(); void loadDetail();
}, { immediate: true });
watch(detailId, loadDetail);
onBeforeRouteLeave(() => !uploading.value && !publishing.value);
onBeforeRouteUpdate(() => !uploading.value && !publishing.value);
</script>

<template>
  <WorkspaceLayout :title="detailId ? '库存导入核对' : '库存导入'" :busy="uploading || publishing" :list="!detailId">
      <div v-show="!detailId" class="auxiliary-stack">
        <div class="page-title"><div><p class="eyebrow">INVENTORY / IMPORT</p><h1>库存导入</h1><p class="muted">上传上游最新 sku.txt，核对条码、产品及净重。</p></div><span class="step-label">上传 → 核对差异 → 确认发布</span></div>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
        <el-empty v-if="!session.warehouseId" description="暂无可访问的仓库，请联系管理员。" />
        <template v-else>
          <section v-if="canImport" class="panel upload-panel"><div><h2>上传库存文件</h2><p class="muted">每份文件按当前库存全量核对。相邻文件可以包含重叠条码。</p></div>
            <form class="upload-form" @submit.prevent="upload"><label class="file-picker">选择 sku.txt<input type="file" accept=".txt,text/plain" :disabled="uploading" @change="chooseFile" /></label>
              <el-button native-type="submit" type="primary" size="large" :loading="uploading" :disabled="!selectedFile">上传并预检</el-button></form>
          </section>
          <section class="panel auxiliary-list"><div class="section-heading"><h2>导入记录</h2><el-button text :disabled="loading" @click="paging.reload">刷新</el-button></div>
            <QueryFeedback :busy="loading" :error="queryError" :count="history.length" empty="还没有导入记录" @retry="paging.retry" />
            <div v-if="history.length" class="auxiliary-table"><el-table v-loading="loading" :data="history" row-key="id" highlight-current-row height="100%">
              <el-table-column prop="originalFilename" label="原文件" min-width="160" />
              <el-table-column label="上传时间" min-width="180"><template #default="{ row }">{{ localTime(row.createdAt) }}</template></el-table-column>
              <el-table-column prop="totalRows" label="数据行" width="100" align="right" />
              <el-table-column prop="errorRows" label="错误行" width="100" align="right" />
              <el-table-column label="状态" min-width="190"><template #default="{ row }"><el-tag :type="row.status === 'BLOCKED' ? 'danger' : 'warning'">{{ row.publishedSnapshotId ? '已发布' : statusLabel(row.status) }}</el-tag></template></el-table-column>
              <el-table-column width="90" label="操作"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row, $event)">查看</el-button></template></el-table-column>
            </el-table></div>
            <CursorPager :page="page" :size="10" :busy="loading" :has-next="!!nextCursor" label="导入记录分页" @first="paging.first" @previous="paging.previous" @next="paging.next" />
          </section>
        </template>
      </div>
      <div v-show="!!detailId">
        <PageHeading title="库存导入核对" back back-label="返回导入记录" @back="back" />
        <QueryFeedback :busy="detailBusy" :error="detailError" :count="selected ? 1 : 0" empty="未找到导入记录" @retry="loadDetail" />
        <ImportDetail v-if="selected" :key="selected.id" :item="selected" @published="published" @sending="publishing = $event" />
      </div>
  </WorkspaceLayout>
</template>
