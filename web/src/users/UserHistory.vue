<script setup lang="ts">
import { watch } from 'vue';
import { request } from '../api';
import { useCursorPage } from '../useCursorPage';
import { historySchema, actionLabel } from './contracts';
import { localTime } from '../orders/contracts';
import CursorPager from '../components/CursorPager.vue';
import QueryFeedback from '../components/QueryFeedback.vue';
const props = defineProps<{ userId: string; warehouseId: string }>();
const paging = useCursorPage(cursor => request(`/users/${props.userId}/history?${new URLSearchParams({ warehouseId: props.warehouseId, beforeId: cursor })}`, historySchema));
const { rows, busy, error, page, nextCursor } = paging;
watch(() => [props.userId, props.warehouseId], paging.reset, { immediate: true });
</script>
<template>
  <QueryFeedback :busy="busy" :error="error" :count="rows.length" empty="暂无人员管理记录" @retry="paging.retry" />
  <el-table v-if="rows.length" :data="rows" v-loading="busy">
    <el-table-column label="时间" min-width="180"><template #default="{ row }">{{ localTime(row.createdAt) }}</template></el-table-column>
    <el-table-column label="操作" min-width="130"><template #default="{ row }">{{ actionLabel(row.action) }}</template></el-table-column>
    <el-table-column prop="actorName" label="操作人员" min-width="120" /><el-table-column prop="reason" label="原因" min-width="180" />
  </el-table>
  <CursorPager :page="page" :size="20" :busy="busy" :has-next="!!nextCursor" label="人员操作记录分页" @first="paging.first" @previous="paging.previous" @next="paging.next" />
</template>
