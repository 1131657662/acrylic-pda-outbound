<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { z } from 'zod';
import { request, errorText } from '../api';
import { pageSchema } from '../inventory/master-contracts';
import { localTime } from './contracts';
const props = defineProps<{ warehouseId: string; orderId: string }>();
const schema = z.object({ id: z.string(), revision: z.number().int(), policyVersion: z.string(), autoDeductInventory: z.boolean(),
  reason: z.string(), publishedBy: z.string(), publishedAt: z.string(), documentStatus: z.string() });
const rows = ref<z.infer<typeof schema>[]>([]); const next = ref<string | null>(null); const busy = ref(false); const error = ref('');
async function load(before = ''): Promise<void> {
  busy.value = true; error.value = '';
  try {
    const params = new URLSearchParams({ warehouseId: props.warehouseId, limit: '20' });
    if (before) params.set('beforeRevision', before);
    const page = await request(`/orders/${props.orderId}/publications?${params}`, pageSchema(schema));
    rows.value = before ? [...rows.value, ...page.items] : page.items; next.value = page.nextCursor;
  } catch (cause) { error.value = errorText(cause); } finally { busy.value = false; }
}
function taskLabel(status: string): string {
  const labels: Record<string, string> = { PENDING: '待生成', RUNNING: '生成中', SUCCEEDED: '已生成', FAILED: '失败', SUPERSEDED: '已被替代' };
  return labels[status] ?? status;
}
onMounted(() => { void load(); });
</script>
<template>
  <section class="panel"><h2>发布记录</h2><el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="busy" :data="rows" empty-text="尚未发布"><el-table-column prop="revision" label="计划版本" width="100" />
      <el-table-column prop="policyVersion" label="规则版本" width="100" /><el-table-column label="自动扣库存" width="120"><template #default="{ row }">{{ row.autoDeductInventory ? '开启' : '关闭' }}</template></el-table-column>
      <el-table-column label="计划单任务" width="130"><template #default="{ row }">{{ taskLabel(row.documentStatus) }}</template></el-table-column>
      <el-table-column prop="reason" label="发布原因" min-width="150" /><el-table-column prop="publishedBy" label="操作人" width="120" />
      <el-table-column label="发布时间" width="190"><template #default="{ row }">{{ localTime(row.publishedAt) }}</template></el-table-column>
    </el-table><el-button v-if="next" :loading="busy" @click="load(next ?? '')">更多发布记录</el-button>
    <p class="muted">计划单任务已保存，PDF 文件生成服务尚待接入。</p>
  </section>
</template>
