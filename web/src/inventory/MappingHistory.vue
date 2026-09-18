<script setup lang="ts">
import { watch } from 'vue';
import { request } from '../api';
import { mappingSchema, pageSchema } from './master-contracts';
import { localTime } from './contracts';
import { useCursorPage } from '../useCursorPage';
import CursorPager from '../components/CursorPager.vue';
import QueryFeedback from '../components/QueryFeedback.vue';
const props = defineProps<{ warehouseId: string; code: string }>();
const paging = useCursorPage(async (cursor) => {
  const params = new URLSearchParams({ warehouseId: props.warehouseId, limit: '20' });
  if (cursor) params.set('beforeVersion', cursor);
  return request(`/delivery-mappings/${props.code}/history?${params}`, pageSchema(mappingSchema));
});
const { rows, busy, error, page, nextCursor } = paging;
watch(() => [props.warehouseId, props.code], paging.reset, { immediate: true });
</script>
<template>
  <section class="panel">
    <QueryFeedback :busy="busy" :error="error" :count="rows.length" empty="暂无此代码的历史记录" @retry="paging.retry" />
    <el-table v-if="rows.length" v-loading="busy" :data="rows">
      <el-table-column prop="version" label="版本" width="80" /><el-table-column prop="model" label="型号" min-width="180" />
      <el-table-column prop="name" label="名称" min-width="140" /><el-table-column label="状态" width="110"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}{{ row.current ? ' · 当前' : '' }}</template></el-table-column>
      <el-table-column prop="reason" label="变更原因" min-width="180" /><el-table-column label="时间" min-width="180"><template #default="{ row }">{{ localTime(row.createdAt) }}</template></el-table-column>
    </el-table><CursorPager :page="page" :size="20" :busy="busy" :has-next="!!nextCursor" label="产品对应历史分页" @first="paging.first" @previous="paging.previous" @next="paging.next" />
  </section>
</template>
