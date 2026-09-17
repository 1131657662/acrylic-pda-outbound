<script setup lang="ts">
import { computed, ref } from 'vue';
import { request, errorText } from '../api';
import { useSessionStore } from '../session';
import { importSchema } from './contracts';
import type { ImportSummary } from './contracts';
import { diffSchema, receiptSchema } from './master-contracts';
import type { Diff } from './master-contracts';
import PublicationDiff from './PublicationDiff.vue';
const props = defineProps<{ item: ImportSummary }>();
const emit = defineEmits<{ published: [item: ImportSummary] }>();
const session = useSessionStore();
const preview = ref<Diff | null>(null);
const loading = ref(false); const publishing = ref(false); const error = ref('');
const acceptMappings = ref(false); const acceptDuplicates = ref(false); const acceptMissing = ref(false); const reason = ref('');
let requestBody = ''; let requestKey = ''; let receiptId: string | null = null;
const canPublish = computed(() => session.identity?.permissions.includes('inventory:publish'));
const ready = computed(() => preview.value && !props.item.publishedSnapshotId && props.item.status !== 'BLOCKED'
  && (!preview.value.mappingChangeCount || acceptMappings.value) && (!props.item.duplicateRows || acceptDuplicates.value)
  && (!preview.value.removed || acceptMissing.value));
async function load(): Promise<void> {
  loading.value = true; error.value = ''; preview.value = null;
  acceptMappings.value = false; acceptDuplicates.value = false; acceptMissing.value = false; requestKey = ''; requestBody = '';
  try { preview.value = await request(`/inventory/imports/${props.item.id}/diff?warehouseId=${props.item.warehouseId}`, diffSchema); }
  catch (cause) { error.value = errorText(cause); }
  finally { loading.value = false; }
}
async function publish(): Promise<void> {
  if (!preview.value || publishing.value) return;
  publishing.value = true; error.value = '';
  const body = JSON.stringify({ expectedActiveSnapshot: preview.value.baseline.activeSnapshotId,
    expectedMappingRevision: preview.value.baseline.mappingRevision, acceptIdenticalDuplicates: acceptDuplicates.value,
    acceptMappingChanges: acceptMappings.value, confirmedRemovedCount: acceptMissing.value ? preview.value.removed : 0, reason: reason.value });
  if (body !== requestBody || !requestKey) { requestBody = body; requestKey = crypto.randomUUID(); }
  try {
    const receipt = await request(`/inventory/imports/${props.item.id}/publish?warehouseId=${props.item.warehouseId}`, receiptSchema,
      { method: 'POST', body: requestBody, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': requestKey } });
    receiptId = receipt.id;
    emit('published', await request(`/inventory/imports/${props.item.id}?warehouseId=${props.item.warehouseId}`, importSchema));
    preview.value = null;
  } catch (cause) { error.value = `${receiptId ? '发布已成功，刷新记录失败。再次点击可取回原回执。' : ''}${errorText(cause)}`; }
  finally { publishing.value = false; }
}
</script>
<template>
  <div v-if="!item.publishedSnapshotId && item.status !== 'BLOCKED'" class="publication-panel">
    <el-button :loading="loading" :disabled="publishing" @click="load">{{ preview ? '重新核对差异' : '核对差异并发布' }}</el-button>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="preview">
      <div class="metrics"><div><span>新增包数</span><strong>{{ preview.added }}</strong></div><div><span>上游未列出</span><strong>{{ preview.removed }}</strong></div>
        <div><span>属性变化</span><strong>{{ preview.changed }}</strong></div><div><span>无变化</span><strong>{{ preview.unchanged }}</strong></div></div>
      <PublicationDiff :preview="preview" />
      <p class="muted">本次将建立新库存版本。未列出的包标记为上游缺失；扣账、冻结及出库周期由各自凭据维护。</p>
      <div class="confirmation-fields">
        <el-checkbox v-if="preview.mappingChangeCount" v-model="acceptMappings" :disabled="publishing">已核对 {{ preview.mappingChangeCount }} 项候选映射，确认建立新版本</el-checkbox>
        <el-checkbox v-if="item.duplicateRows" v-model="acceptDuplicates" :disabled="publishing">确认相同重复条码去重（{{ item.duplicateRows }} 行）</el-checkbox>
        <el-checkbox v-if="preview.removed" v-model="acceptMissing" :disabled="publishing">确认本次全量文件缺少 {{ preview.removed }} 包</el-checkbox>
        <el-input v-model="reason" aria-label="发布备注" placeholder="发布备注（可选）" maxlength="500" :disabled="publishing" />
        <el-button v-if="canPublish" type="primary" :disabled="!ready || loading" :loading="publishing" @click="publish">确认发布库存版本</el-button>
      </div>
    </template>
  </div>
</template>
