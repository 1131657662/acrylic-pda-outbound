<script setup lang="ts">
import { useRouter } from 'vue-router';
import { localTime } from './contracts';
import { useTemporaryCandidates } from './useTemporaryCandidates';
const props = defineProps<{ warehouse: string; order: string; revision: number; version: string }>();
const router = useRouter(); const { result, busy, error, from, through, load } = useTemporaryCandidates(props);
</script>
<template>
  <section class="panel temporary-candidates" aria-label="临时单重复候选核对">
    <h2>临时单重复候选核对</h2>
    <p>同仓已成功或待审临时单，按相同车号或重叠产品提示；请人工核对是否同一趟装车。</p>
    <form class="candidate-range" @submit.prevent="load()">
      <label>接收起日<el-date-picker v-model="from" type="date" value-format="YYYY-MM-DD" aria-label="临时单接收起日" :disabled="busy" /></label>
      <label>接收止日<el-date-picker v-model="through" type="date" value-format="YYYY-MM-DD" aria-label="临时单接收止日" :disabled="busy" /></label>
      <el-button native-type="submit" :loading="busy">重新核对候选</el-button>
    </form>
    <el-alert v-if="error" :title="`候选暂未核对：${error}`" type="warning" :closable="false" />
    <p v-if="busy" role="status">正在查询临时单候选…</p>
    <template v-if="result">
      <p class="muted">范围：{{ result.range.fromDate }} 至 {{ result.range.throughDate }}（北京时间接收日期），共 {{ result.candidates.total }} 个候选。查询于 {{ localTime(result.checkedAt) }}。</p>
      <el-alert v-if="result.candidates.total" title="发现可能重复的临时单，请核对原记录" type="warning" :closable="false" />
      <p v-else>所选日期及匹配条件下暂无候选；这不表示已排除重复，可扩大日期范围继续核对。</p>
      <el-table v-if="result.candidates.total" :data="result.candidates.items" max-height="300" show-overflow-tooltip>
        <el-table-column label="原临时单" min-width="200"><template #default="{ row }">
          <el-button link type="primary" @click="router.push({ path: `/orders/${row.orderId}`, query: { warehouse } })">{{ row.orderNo }}</el-button>
        </template></el-table-column>
        <el-table-column label="原状态" min-width="150"><template #default="{ row }">{{ row.status === 'SUCCEEDED' ? '后台已确认出库' : '待主管处理' }}</template></el-table-column>
        <el-table-column prop="vehicleNo" label="车号" min-width="150" />
        <el-table-column prop="containerNo" label="柜号" min-width="100" />
        <el-table-column label="计划 / 实扫" min-width="100"><template #default="{ row }">{{ row.plannedTotal }} / {{ row.actualCount }}</template></el-table-column>
        <el-table-column label="提示依据" min-width="150"><template #default="{ row }">{{ [row.sameVehicle ? '相同车号' : '', row.overlappingProducts ? '重叠产品' : ''].filter(Boolean).join('、') }}</template></el-table-column>
      </el-table>
      <el-pagination v-if="result.candidates.total > 20" :current-page="result.candidates.page" :page-size="20" :total="result.candidates.total" layout="prev, pager, next" :disabled="busy" @current-change="load" />
      <p v-if="result.hasFormalWork">正式单已有作业或出库证据。若核实重复，请保留双方证据，进入差异与更正处理。</p>
      <p v-else>若人工核实为重复且正式单尚无作业，可由具有既有作废权限的人员处理。</p>
    </template>
    <p class="muted">候选仅供核对，不自动合并、关联或阻断操作，也不构成放行依据。原临时单成功事实保持。</p>
  </section>
</template>
<style scoped>
.candidate-range { display: flex; flex-wrap: wrap; align-items: end; gap: var(--space-4); }
.candidate-range label { display: grid; gap: var(--space-2); }
</style>
