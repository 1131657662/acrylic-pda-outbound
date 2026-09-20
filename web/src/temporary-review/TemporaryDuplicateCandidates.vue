<script setup lang="ts">
import { localTime } from '../orders/contracts';
import TemporaryDuplicateCandidate from './TemporaryDuplicateCandidate.vue';
import { useTemporaryDuplicates } from './useTemporaryDuplicates';
const props = defineProps<{ submissionId: string; warehouseId: string }>();
const { data, from, through, busy, error, index, changed, search, next, previous } = useTemporaryDuplicates(
  () => ({ submissionId: props.submissionId, warehouseId: props.warehouseId }));
</script>
<template>
  <section class="panel" aria-label="疑似重复正式单核对">
    <h2>疑似重复正式单</h2>
    <p>仅查询同仓正式单，以服务器建单日期（北京时间）筛选，同车号或有相同产品时提示。不会自动合并，查询不产生排除、关联或放行效力。</p>
    <p class="muted">初始范围为云端收到临时批次日期前后各 7 天，可自行调整；一次最多查 366 天。这是检索范围，范围外仍可能有重复。</p>
    <form class="order-pagination" @submit.prevent="search">
      <label>建单开始日期 <input v-model="from" type="date" min="2000-01-01" max="9998-12-31" :disabled="busy" /></label>
      <label>建单结束日期 <input v-model="through" type="date" min="2000-01-01" max="9998-12-31" :disabled="busy" /></label>
      <el-button native-type="submit" :loading="busy">查询／刷新全部候选</el-button>
    </form>
    <p v-if="changed" role="status">日期已修改，请先查询；当前筛选尚未应用。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="busy" role="status">正在重新核对候选及原批次……</p>
    <template v-if="data && !error">
      <p role="status">完整查询范围：{{ data.range.fromDate }} 至 {{ data.range.throughDate }}，同仓正式单共 {{ data.total }} 个候选；当前第 {{ index + 1 }} 页，本页 {{ data.items.length }} 个，每页最多 20 个。</p>
      <p>读取于 {{ localTime(data.checkedAt) }}。翻页时若候选或原批次变化，须重新查询；未看完全部页不等于完成重复核对。</p>
      <p v-if="!data.items.length">本次范围未找到符合信号的候选，不代表不存在重复，也不授予确认或放行权限。</p>
      <TemporaryDuplicateCandidate v-for="candidate in data.items" :key="candidate.order.id" :candidate="candidate" />
      <nav class="order-pagination" aria-label="重复候选分页">
        <el-button :disabled="busy || changed || index === 0" @click="previous">上一页</el-button>
        <el-button :disabled="busy || changed || !data.hasMore" @click="next">下一页</el-button>
      </nav>
      <details class="file-proof"><summary>本次对照所用原批次</summary>
        <p>原临时号身份 {{ data.context.source.clientOrderId }} · 原设备 {{ data.context.source.creatorDeviceId }}</p>
        <p>原批次 {{ data.context.submissionId }} · 正式记录 {{ data.context.canonicalOrderId }} · 轮次 {{ data.context.workRound }}</p>
        <p>当前批次 {{ data.context.currentSubmissionId || '无' }} · {{ data.context.current ? '本批次仍为当前批次' : '本批次已不是当前批次' }} · {{ data.context.status }}</p>
        <p>原计划 V{{ data.context.orderRevision }} · 正文摘要 {{ data.context.payloadSha256 }} · 计划摘要 {{ data.context.planSha256 }}</p>
      </details>
    </template>
  </section>
</template>
