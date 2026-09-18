<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { request, errorText } from '../api';
import FeedbackState from '../components/FeedbackState.vue';
import { kilograms } from '../orders/submission-contracts';
import { localTime } from '../orders/contracts';
import { entriesSchema, entryLabels, type Entry } from './contracts';
const props = defineProps<{ order: string; warehouse: string; version: string; disabled: boolean }>();
const emit = defineEmits<{ remove: [entry: Entry] }>();
const rows = ref<Entry[]>([]); const total = ref(0); const page = ref(1); const search = ref(''); const code = ref('');
const busy = ref(false); const error = ref(''); let generation = 0;
async function load(next = page.value): Promise<void> {
  const current = ++generation; busy.value = true; error.value = '';
  try {
    const params = new URLSearchParams({ warehouseId: props.warehouse, search: search.value.trim(), code: code.value.trim(), page: String(next), pageSize: '20' });
    const found = await request(`/orders/${props.order}/pc/entries?${params}`, entriesSchema);
    if (current === generation) { rows.value = found.items; total.value = found.total; page.value = found.page; }
  } catch (cause) { if (current === generation) error.value = errorText(cause); }
  finally { if (current === generation) busy.value = false; }
}
watch(() => props.version, () => { void load(); }, { immediate: true });
onBeforeUnmount(() => { generation++; });
</script>
<template>
  <section class="pc-records">
    <form class="pc-search" @submit.prevent="load(1)"><el-input v-model="search" aria-label="查询录入条码" maxlength="11" placeholder="完整条码或片段" clearable /><el-input v-model="code" aria-label="查询录入代码" maxlength="3" placeholder="发货代码" clearable /><el-button native-type="submit" :loading="busy">查询记录</el-button></form>
    <el-alert v-if="error" :title="error + ' 当前记录可能不是最新结果。'" type="error" :closable="false" />
    <p class="muted">后台已保存共 {{ total }} 条记录，包含撤销记录；最近录入在前。撤销需核对实物及填写原因。</p>
    <div class="pc-record-scroll"><el-table :data="rows" v-loading="busy" row-key="id">
      <el-table-column prop="sequence" label="录入序号" width="90" /><el-table-column prop="barcode" label="完整条码" min-width="160" />
      <el-table-column prop="deliveryCode" label="代码" width="75" /><el-table-column label="净重 kg" width="110"><template #default="{ row }">{{ kilograms(row.weightGrams) }}</template></el-table-column>
      <el-table-column label="人员 / 时间" min-width="160"><template #default="{ row }">{{ row.operatorName }}<div class="muted">{{ localTime(row.createdAt) }}</div></template></el-table-column>
      <el-table-column label="状态" min-width="140"><template #default="{ row }">{{ entryLabels[row.status as Entry['status']] }}<p v-if="row.voidReason" class="muted">{{ row.voidReason }}</p></template></el-table-column>
      <el-table-column label="操作" width="120"><template #default="{ row }"><el-button v-if="row.status === 'ACTIVE'" link type="danger" :disabled="disabled || busy || !!error" @click="emit('remove', row)">撤销本次录入</el-button></template></el-table-column>
      <template #empty><FeedbackState :loading="busy" :error="!!error" :title="busy ? '正在读取记录' : error ? '记录读取失败' : '暂无匹配录入记录'" /></template>
    </el-table></div>
    <el-pagination layout="total, prev, pager, next" :total="total" :page-size="20" :current-page="page" :disabled="busy" @current-change="load" />
  </section>
</template>
