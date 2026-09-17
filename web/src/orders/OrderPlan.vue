<script setup lang="ts">
import type { Detail, Preflight } from './contracts';
defineProps<{ detail: Detail; preflight: Preflight | null; checkError: string }>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <section class="panel">
    <h2>产品计划</h2>
    <el-table :data="detail.lines"><el-table-column prop="deliveryCode" label="发货代码" width="110" /><el-table-column prop="model" label="型号" min-width="190" /><el-table-column prop="name" label="品名" min-width="150" /><el-table-column prop="plannedCount" label="计划包数" width="115" align="right" /></el-table>
    <p class="detail-note">备注：{{ detail.plan.notes || '无' }}</p>
  </section>
  <section v-if="detail.order.status === 'DRAFT'" class="panel">
    <div class="section-heading"><div><h2>发布前检查</h2><p class="muted">候选包数未扣除其他订单占用，本次检查不预留库存。</p></div><el-tag v-if="preflight" :type="preflight.checksPassed ? 'success' : 'danger'">{{ preflight.checksPassed ? '版本检查通过' : '存在阻断项' }}</el-tag></div>
    <el-alert v-if="checkError" :title="checkError" type="error" :closable="false"><el-button text @click="$emit('retry')">重新检查</el-button></el-alert>
    <el-alert v-for="issue in preflight?.blockers" :key="issue.code" :title="issue.message" type="error" :closable="false" />
    <el-table v-if="preflight" :data="preflight.lines"><el-table-column prop="deliveryCode" label="发货代码" width="110" /><el-table-column prop="plannedCount" label="计划包数" width="110" align="right" /><el-table-column prop="candidateCount" label="库存候选包数" width="140" align="right" /><el-table-column prop="shortage" label="提示缺口" width="110" align="right" /><el-table-column label="结果" min-width="110"><template #default="{ row }"><el-tag :type="row.mappingValid && row.shortage === 0 ? 'success' : 'warning'">{{ row.mappingValid && row.shortage === 0 ? '数量充足' : '需核对' }}</el-tag></template></el-table-column></el-table>
  </section>
</template>
