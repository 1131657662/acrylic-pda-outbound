<script setup lang="ts">
import { computed, ref } from 'vue';
import { kilograms } from '../orders/submission-contracts';
import { localTime } from '../orders/contracts';
import { dispositionLabels, planLabels, type ExceptionPlan, type Stopped } from './exception-contracts';
const props = defineProps<{ plan: ExceptionPlan; stopped: Stopped }>();
const page = ref(1);
const rows = computed(() => {
  const entries = new Map(props.stopped.snapshot.entries.map(entry => [entry.id, entry]));
  return props.plan.body.lines.map(line => ({ ...line, entry: entries.get(line.entryId) }));
});
const visible = computed(() => rows.value.slice((page.value - 1) * 20, page.value * 20));
</script>
<template>
  <section class="pc-exception-plan">
    <div class="section-heading"><h2>固定处置方案</h2><el-tag :type="plan.status === 'APPLIED' ? 'success' : 'warning'">{{ planLabels[plan.status] }}</el-tag></div>
    <dl class="pc-exception-facts"><div><dt>指定接续人员</dt><dd>{{ plan.body.targetName }} · {{ plan.body.targetUsername }}</dd></div><div><dt>交接后安排</dt><dd>{{ plan.body.outcome === 'CONTINUE' ? '继续本单录入' : '全部卸下，结束本单' }}</dd></div><div><dt>申请时间</dt><dd>{{ localTime(plan.requestedAt) }}</dd></div></dl>
    <p>原因：{{ plan.body.reason }}</p>
    <p v-if="plan.status === 'APPROVED'">主管已批准，包占用尚未改变。请由指定人员登录后逐包核对并确认交接。</p>
    <el-alert v-if="plan.status === 'APPLIED'" title="交接已生效。保留包仍属本单，释放包可按新作业规则使用，隔离包仍禁止出库。" type="success" :closable="false" />
    <p v-if="plan.handoverEvidence">接续核验记录：{{ plan.handoverEvidence }} · {{ plan.appliedAt ? localTime(plan.appliedAt) : '' }}</p>
    <div class="pc-exception-table"><el-table :data="visible" row-key="entryId">
      <el-table-column label="原完整条码" min-width="160"><template #default="{ row }"><strong class="barcode">{{ row.entry?.barcode }}</strong><div>原序号 {{ row.entry?.sequence }}</div></template></el-table-column>
      <el-table-column label="净重 kg" width="120"><template #default="{ row }">{{ row.entry ? kilograms(row.entry.weightGrams) : '' }}</template></el-table-column>
      <el-table-column label="核验去向" min-width="140"><template #default="{ row }">{{ dispositionLabels[row.action as keyof typeof dispositionLabels] }}</template></el-table-column>
      <el-table-column prop="physicalEvidence" label="实物位置、结果及核验凭据" min-width="260" />
    </el-table></div>
    <el-pagination v-if="rows.length > 20" layout="total, prev, pager, next" :total="rows.length" :page-size="20" v-model:current-page="page" />
  </section>
</template>
