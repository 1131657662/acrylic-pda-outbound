<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { request, errorText } from '../api';
import { useSessionStore } from '../session';
import { importSchema, importPageSchema, localTime, statusLabel } from './contracts';
import type { ImportSummary } from './contracts';
import ImportDetail from './ImportDetail.vue';
import WorkspaceLayout from '../WorkspaceLayout.vue';

const session = useSessionStore();
const selectedFile = ref<File | null>(null);
const uploadKey = ref('');
const uploading = ref(false);
const loading = ref(false);
const error = ref('');
const history = ref<ImportSummary[]>([]);
const selected = ref<ImportSummary | null>(null);
const nextCursor = ref<string | null>(null);
const canImport = computed(() => session.identity?.permissions.includes('inventory:import'));
let generation = 0;

async function loadHistory(cursor = ''): Promise<void> {
  const current = ++generation;
  if (!session.warehouseId) return;
  loading.value = true; error.value = '';
  try {
    const params = new URLSearchParams({ warehouseId: session.warehouseId, limit: '10', cursor });
    const page = await request(`/inventory/imports?${params}`, importPageSchema);
    if (current !== generation) return;
    history.value = page.items; nextCursor.value = page.nextCursor;
  } catch (cause) { if (current === generation) error.value = errorText(cause); }
  finally { if (current === generation) loading.value = false; }
}
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
    if (warehouse === session.warehouseId) { selected.value = result; await loadHistory(); }
  } catch (cause) { error.value = errorText(cause); }
  finally { uploading.value = false; }
}
function published(item: ImportSummary): void {
  if (item.warehouseId !== session.warehouseId) return;
  selected.value = item; void loadHistory();
}
watch(() => session.warehouseId, () => {
  selected.value = null; history.value = []; nextCursor.value = null; uploadKey.value = crypto.randomUUID();
  void loadHistory();
}, { immediate: true });
</script>

<template>
  <WorkspaceLayout title="库存导入" :busy="uploading">
        <div class="page-title"><div><p class="eyebrow">INVENTORY / IMPORT</p><h1>库存导入</h1><p class="muted">上传上游最新 sku.txt，核对条码、产品及净重。</p></div><span class="step-label">上传 → 核对差异 → 确认发布</span></div>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
        <el-empty v-if="!session.warehouseId" description="暂无可访问的仓库，请联系管理员。" />
        <template v-else>
          <section v-if="canImport" class="panel upload-panel"><div><h2>上传库存文件</h2><p class="muted">每份文件按当前库存全量核对。相邻文件可以包含重叠条码。</p></div>
            <form class="upload-form" @submit.prevent="upload"><label class="file-picker">选择 sku.txt<input type="file" accept=".txt,text/plain" :disabled="uploading" @change="chooseFile" /></label>
              <el-button native-type="submit" type="primary" size="large" :loading="uploading" :disabled="!selectedFile">上传并预检</el-button></form>
          </section>
          <section class="panel"><div class="section-heading"><h2>导入记录</h2><el-button text :disabled="loading" @click="loadHistory()">刷新</el-button></div>
            <el-table v-loading="loading" :data="history" empty-text="还没有导入记录" row-key="id" highlight-current-row @row-click="(row: ImportSummary) => selected = row">
              <el-table-column prop="originalFilename" label="原文件" min-width="160" />
              <el-table-column label="上传时间" min-width="180"><template #default="{ row }">{{ localTime(row.createdAt) }}</template></el-table-column>
              <el-table-column prop="totalRows" label="数据行" width="100" align="right" />
              <el-table-column prop="errorRows" label="错误行" width="100" align="right" />
              <el-table-column label="状态" min-width="190"><template #default="{ row }"><el-tag :type="row.status === 'BLOCKED' ? 'danger' : 'warning'">{{ row.publishedSnapshotId ? '已发布' : statusLabel(row.status) }}</el-tag></template></el-table-column>
              <el-table-column width="90"><template #default="{ row }"><el-button link type="primary" @click="selected = row">查看</el-button></template></el-table-column>
            </el-table>
            <div class="pagination"><span class="muted">每页 10 条</span><el-button :disabled="loading" @click="loadHistory()">第一页</el-button><el-button :disabled="loading || !nextCursor" @click="nextCursor && loadHistory(nextCursor)">下一页</el-button></div>
          </section>
          <ImportDetail v-if="selected" :key="selected.id" :item="selected" @published="published" />
        </template>
  </WorkspaceLayout>
</template>
