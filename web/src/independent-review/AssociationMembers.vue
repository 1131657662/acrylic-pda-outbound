<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue';
import { z } from 'zod';
import { request, errorText } from '../api';
import { reviewSchema } from './contracts';
import { allCandidates } from './association-api';
import type { PhysicalOwner } from './physical-contracts';
import type { AssociationSelection } from './association-selection';
const props = defineProps<{ owner: PhysicalOwner; target: string; disabled: boolean }>();
const members = defineModel<AssociationSelection[]>({ required: true });
const emit = defineEmits<{ loading: [value: boolean] }>();
const relay = ref(''); const busy = ref(false); const error = ref(''); let generation = 0;
async function add(): Promise<void> {
  if (busy.value || props.disabled || members.value.length >= 19) return;
  const token = generation; const captured = { ...props.owner }; const id = relay.value.trim();
  function assertCurrent(): void {
    if (token !== generation || JSON.stringify(captured) !== JSON.stringify(props.owner)) throw new Error('身份或原件已变化，停止载入。');
  }
  busy.value = true; emit('loading', true); error.value = '';
  try {
    z.uuid().parse(id);
    if (id === captured.relayId || members.value.some(row => row.data.original.relayId === id)) throw new Error('原件已在本组中。');
    const query = new URLSearchParams({ warehouseId: captured.warehouseId, section: 'candidates', limit: '200' });
    const data = await request(`/original-relays/${id}/independent-reconciliation?${query}`, reviewSchema); assertCurrent();
    if (data.original.relayId !== id || data.original.warehouseId !== captured.warehouseId) throw new Error('原件不在所选仓库。');
    const candidates = await allCandidates(data, assertCurrent); assertCurrent();
    members.value = [...members.value, { data, candidates, physical: '', conflict: '', exclusions: {} }]; relay.value = '';
  } catch (cause) { if (token === generation) error.value = cause instanceof Error ? cause.message : errorText(cause); }
  finally { if (token === generation) { busy.value = false; emit('loading', false); } }
}
watch(() => JSON.stringify(props.owner), () => { generation++; relay.value = ''; error.value = ''; busy.value = false; emit('loading', false); });
onUnmounted(() => { generation++; });
</script>
<template>
  <section class="association-members" aria-label="共同关联的额外任务">
    <h3>共同关联其他独立任务</h3>
    <p>须同一车辆并逐个核对；每个任务保留各自原件、实物和冲突依据。共 {{ members.length + 1 }} 个任务。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-input v-model="relay" aria-label="额外任务原件编号" placeholder="输入要共同关联的原件UUID" maxlength="36" :disabled="disabled || busy || members.length >= 19" />
    <el-button :disabled="disabled || busy || !relay || members.length >= 19" @click="add">载入并选择额外任务</el-button>
    <article v-for="(member, index) in members" :key="member.data.original.relayId" class="association-member">
      <h4>额外任务 {{ index + 1 }} · {{ member.data.original.task.vehicleNo }}</h4>
      <p>原件 {{ member.data.original.relayId }} · 设备 {{ member.data.original.deviceId }} · 任务 {{ member.data.original.taskId }}</p>
      <p>有效 {{ member.data.summary.activeTotal }}包 · 原短装 {{ member.data.original.closure.shortfall }}包 · {{ member.data.original.closure.shortfallReason || '无短装' }}</p>
      <p>完整候选 {{ member.candidates.length }}单；须包含同一目标，其余逐项排除。</p>
      <p v-if="target && !member.candidates.some(row => row.id === target)" role="alert">此任务不包含所选目标，不能关联。</p>
      <el-input v-model="member.physical" :aria-label="`额外任务 ${index + 1} 实物回执编号`" maxlength="36" :disabled="disabled" />
      <el-input v-model="member.conflict" :aria-label="`额外任务 ${index + 1} 冲突决定编号`" placeholder="有冲突时显式引用完整原决定" maxlength="36" :disabled="disabled" />
      <template v-for="candidate in member.candidates" :key="candidate.id">
        <p>{{ candidate.orderNo }} · {{ candidate.vehicleNo }} · {{ candidate.plannedTotal }}包</p>
        <p>{{ candidate.status }} · {{ candidate.source }} · {{ candidate.blockers.join('、') || '候选尚须核对产品与实物' }}</p>
        <p v-for="product in candidate.products" :key="product.deliveryCode">{{ product.deliveryCode }} · {{ product.model }} · {{ product.name }} · {{ product.plannedCount }}包</p>
        <el-input v-if="candidate.id !== target" v-model="member.exclusions[candidate.id]" :aria-label="`额外任务 ${index + 1} 排除候选 ${candidate.orderNo}`" maxlength="500" placeholder="逐项说明排除依据" :disabled="disabled" />
      </template>
      <el-button :disabled="disabled" @click="members = members.filter(row => row !== member)">移出额外任务 {{ index + 1 }}</el-button>
    </article>
  </section>
</template>
<style scoped>
.association-members { margin-block: var(--space-4); }
.association-member { padding: var(--space-4); margin-block: var(--space-4); border: 1px solid var(--separator); border-radius: var(--radius-control); overflow-wrap: anywhere; }
</style>
