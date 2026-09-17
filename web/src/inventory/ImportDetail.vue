<script setup lang="ts">
import PublicationPanel from './PublicationPanel.vue';
import ImportRows from './ImportRows.vue';
import { weightKg, statusLabel, localTime } from './contracts';
import type { ImportSummary } from './contracts';
defineProps<{ item: ImportSummary }>();
defineEmits<{ published: [item: ImportSummary] }>();
</script>

<template>
  <section class="panel detail-panel">
    <div class="section-heading"><div><p class="eyebrow">预检结果</p><h2>{{ item.originalFilename }}</h2></div>
      <el-tag :type="item.status === 'BLOCKED' ? 'danger' : 'warning'">{{ item.publishedSnapshotId ? '已发布' : statusLabel(item.status) }}</el-tag>
    </div>
    <p class="muted">{{ localTime(item.createdAt) }} · {{ item.publishedSnapshotId ? '此文件已发布，历史发布记录不会自动切回当前版本。' : '此文件尚未发布，不影响当前库存。' }}</p>
    <div class="metrics">
      <div><span>文件数据行</span><strong>{{ item.totalRows.toLocaleString() }}</strong></div>
      <div><span>有效包数（去重）</span><strong>{{ item.validRows.toLocaleString() }}</strong></div>
      <div><span>有效产品代码</span><strong>{{ item.productCount }}</strong></div>
      <div><span>有效净重 kg</span><strong>{{ weightKg(item.totalWeightGrams) }}</strong></div>
    </div>
    <el-alert v-if="item.errorRows || item.duplicateRows" :type="item.errorRows ? 'error' : 'warning'" :closable="false" show-icon
      :title="`错误 ${item.errorRows} 行，重复 ${item.duplicateRows} 行。请核对原文件，不会自动发布有效子集。`" />
    <p v-if="item.ignoredRows" class="muted">已忽略 {{ item.ignoredRows }} 个空行，明细保留原始行号。</p>
    <PublicationPanel :item="item" @published="$emit('published', $event)" />
    <ImportRows :import-id="item.id" :warehouse-id="item.warehouseId" />
    <details class="file-proof"><summary>原文件校验信息</summary><p>SHA-256：{{ item.fileSha256 }}</p><p>解析配置：{{ item.parserVersion }}</p></details>
  </section>
</template>
