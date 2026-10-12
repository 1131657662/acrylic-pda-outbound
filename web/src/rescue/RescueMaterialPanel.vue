<script setup lang="ts">
import { computed } from 'vue';
import { documentSchema, statusLabels } from './contracts';
import type { RescueMaterial } from './contracts';

const props = defineProps<{ material: RescueMaterial }>();
const original = computed(() => documentSchema.parse(JSON.parse(props.material.originalText)));
function download(): void {
  const url = URL.createObjectURL(new Blob([JSON.stringify(props.material)], { type: 'application/json;charset=utf-8' }));
  const link = document.createElement('a'); link.href = url; link.download = `rescue-${props.material.request.requestId}.json`;
  link.click(); URL.revokeObjectURL(url);
}
</script>
<template>
  <section class="panel">
    <h2>原申请与签名材料</h2>
    <p><strong>{{ statusLabels[material.status] }}</strong> · {{ material.request.requestId }}</p>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="原操作员">{{ original.operatorId }}</el-descriptions-item>
      <el-descriptions-item label="原设备">{{ original.deviceId }}</el-descriptions-item>
      <el-descriptions-item label="指定救援人">{{ original.rescuerId }}</el-descriptions-item>
      <el-descriptions-item label="申请原因">{{ original.reason }}</el-descriptions-item>
      <el-descriptions-item label="原申请摘要">{{ material.request.requestSha256 }}</el-descriptions-item>
    </el-descriptions>
    <el-table :data="material.orderScope" max-height="320">
      <el-table-column prop="source" label="原来源" width="110" /><el-table-column prop="localOrderId" label="原本机订单/领取编号" min-width="320" />
    </el-table>
    <p>只读取回申请范围的原件；不等于出库确认、库存释放或车辆放行。本机记录保留。</p>
    <el-button :disabled="!material.authorization && !material.revocation" @click="download">下载原授权/撤销签名材料</el-button>
    <p>授权由指定救援人在原设备联网预置，撤销原件可在已预置设备离线核验。解密口令另行保管，材料不含登录口令或PDA会话。</p>
  </section>
</template>
