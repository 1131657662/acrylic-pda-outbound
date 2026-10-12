<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { z } from 'zod';
import { errorText, request } from '../api';
import { useSessionStore } from '../session';
import WorkspaceLayout from '../WorkspaceLayout.vue';
import PageHeading from '../components/PageHeading.vue';
import { dispositionFactsSchema, type DispositionFacts, type DispositionManifest } from './disposition-contracts';
import { savedCommandBody } from './pending-command';
import { useTemporaryCommand } from './useTemporaryCommand';

const session = useSessionStore(); const route = useRoute(); const router = useRouter();
const batch = ref(''); const observed = ref<DispositionFacts | null>(null); const loading = ref(false); const readError = ref('');
const choices = ref<Record<string, 'RELEASE' | 'QUARANTINE' | ''>>({}); const checked = ref<Record<string, boolean>>({});
const reason = ref(''); const permitted = computed(() => session.identity?.permissions.includes('order:dispose') === true);
const { busy, error, receipt, original, restore, execute } = useTemporaryCommand(
  () => ({ userId: session.identity?.id ?? '', warehouseId: session.warehouseId, submissionId: batch.value.trim() }));
const ready = computed(() => !!observed.value && observed.value.items.every(item => checked.value[item.baleId] && choices.value[item.baleId]) && !!reason.value.trim());
const saved = computed(() => original.value ? savedCommandBody(original.value) : null);
let generation = 0;

async function load(): Promise<void> {
  if (busy.value || loading.value || !permitted.value) return;
  const ticket = ++generation; observed.value = null; choices.value = {}; checked.value = {}; readError.value = '';
  if (!z.uuid().safeParse(batch.value.trim()).success) { readError.value = '请填写原机取消回执中的原批次 ID。'; return; }
  restore('CLOSE_DISPOSITION');
  if (error.value || original.value) return;
  const owner = { batch: batch.value.trim(), warehouse: session.warehouseId, user: session.identity?.id };
  loading.value = true;
  try {
    const query = new URLSearchParams({ warehouseId: owner.warehouse, submissionId: owner.batch });
    const value = await request(`/temporary-commands/disposition-facts?${query}`, dispositionFactsSchema);
    if (ticket !== generation || session.warehouseId !== owner.warehouse || session.identity?.id !== owner.user || batch.value.trim() !== owner.batch) return;
    if (value.facts.submissionId !== owner.batch || value.facts.warehouseId !== owner.warehouse) throw new Error('Original scope mismatch');
    observed.value = value;
  } catch (cause) { if (ticket === generation) readError.value = errorText(cause); }
  finally { if (ticket === generation) loading.value = false; }
}

async function submit(): Promise<void> {
  if (original.value) { await execute('CLOSE_DISPOSITION'); return; }
  if (!ready.value || !observed.value) return;
  const items: DispositionManifest['items'] = [];
  for (const item of observed.value.items) {
    const action = choices.value[item.baleId];
    if (action !== 'RELEASE' && action !== 'QUARANTINE') return;
    items.push({ baleId: item.baleId, disposition: action });
  }
  await execute('CLOSE_DISPOSITION', { reason: reason.value, disposition: { observed: observed.value, manifest: { version: 1, items } } });
}

watch(() => [route.params.id, session.identity?.id, session.warehouseId], () => {
  generation++; loading.value = false; observed.value = null; readError.value = ''; reason.value = '';
  original.value = null; receipt.value = null; error.value = ''; choices.value = {}; checked.value = {};
  batch.value = typeof route.params.id === 'string' ? route.params.id : '';
  if (batch.value) void load();
}, { immediate: true });
onBeforeUnmount(() => { generation++; });
</script>
<template>
  <WorkspaceLayout title="临时实物处置">
    <PageHeading title="临时实物处置" subtitle="原批次已停止后，由另一有权主管逐包核实卸货或隔离。" back @back="router.push('/orders')" />
    <section class="panel">
      <label>原批次 ID<el-input v-model="batch" :disabled="busy || loading || !!original" placeholder="从原机取消回执核对" /></label>
      <el-button :disabled="!permitted || busy || loading" :loading="loading" @click="load">核对原批次</el-button>
      <p v-if="!permitted" class="muted">需要本仓实物处置权限，请交由有权主管核对。</p>
      <el-alert v-if="readError || error" :title="readError || error" type="error" :closable="false" />
    </section>
    <section v-if="observed && !original" class="panel">
      <h2>逐包实物核验</h2>
      <p>原批次{{ observed.status === 'ABORTED' ? '已取消' : '已拒绝' }}，完整原机停止证据已核验。取消不自动释放实物占用。</p>
      <p>请在现场核实每包已卸货或隔离，再选择处置；此操作结束原来源，不授放行或新扫码资格。</p>
      <el-table :data="observed.items" row-key="baleId">
        <el-table-column prop="barcode" label="原包条码" min-width="150" />
        <el-table-column prop="dispatchCycle" label="原出库周期" width="110" />
        <el-table-column label="实物核实" min-width="150"><template #default="{ row }">
          <el-checkbox v-model="checked[row.baleId]" :disabled="busy" :aria-label="`${row.barcode} 实物已核实`">已逐包核实</el-checkbox>
        </template></el-table-column>
        <el-table-column label="处置" min-width="220"><template #default="{ row }">
          <el-select v-model="choices[row.baleId]" :disabled="busy" :aria-label="`${row.barcode} 处置`" placeholder="选择核实后的处置">
            <el-option label="已卸货，释放本批合法占用" value="RELEASE" /><el-option label="已隔离，继续阻断使用" value="QUARANTINE" />
          </el-select>
        </template></el-table-column>
      </el-table>
      <label>主管核验理由<el-input v-model="reason" type="textarea" maxlength="500" :disabled="busy" placeholder="记录卸货或隔离核实依据" /></label>
      <el-button type="primary" :disabled="busy || !ready" :loading="busy" @click="submit">签署处置并关闭原来源</el-button>
    </section>
    <section v-if="original && saved" class="panel">
      <h2>保留的原处置</h2><p>原键 {{ saved.commandId }} · {{ saved.reason }}</p>
      <ul><li v-for="item in saved.dispositionManifest?.items" :key="item.baleId">{{ item.baleId }} · {{ item.disposition === 'RELEASE' ? '卸货释放' : '隔离保留' }}</li></ul>
      <p v-if="!receipt">原操作结果尚未核对。重试使用原批次、原证据、原逐包集合与原键。</p>
      <el-button v-if="!receipt" :disabled="busy" :loading="busy" @click="submit">重试原处置请求</el-button>
      <p v-else>关闭原回执已按原键保存。实物按原处置核对，仍不得放行、恢复扫码或删除原记录。</p>
    </section>
  </WorkspaceLayout>
</template>
