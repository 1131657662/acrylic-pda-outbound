<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { currentRecoveryEpoch, errorText, request } from '../api';
import { useSessionStore } from '../session';
import { reviewSchema, type IndependentReview } from './contracts';
import { digest, evidenceSchema, physicalResultSchema, verifyPhysicalResult, type PhysicalOwner } from './physical-contracts';
import { loadPhysical, lockPhysical, savePhysical, type PhysicalSaved } from './physical-storage';
import { physicalPath, sendPhysicalAttachment, sendPhysicalCheck } from './physical-api';
type Stock = Extract<IndependentReview, { section: 'inventory' }>['items'][number];
interface Observation { stock: Stock; status: '' | 'LOADED' | 'UNLOADED' | 'NOT_FOUND'; note: string }
const props = defineProps<{ data: IndependentReview }>(); const session = useSessionStore();
const busy = ref(false); const error = ref(''); const saved = ref<PhysicalSaved | null>(null); const rows = ref<Observation[]>([]);
const area = ref(''); const reason = ref(''); const checkedAt = ref(''); let generation = 0;
const owner = computed<PhysicalOwner>(() => ({ userId: session.identity?.id ?? '', warehouseId: session.warehouseId, relayId: props.data.original.relayId }));
const selfCheck = computed(() => owner.value.userId === props.data.original.operatorId);
const frozen = computed(() => busy.value || saved.value?.command !== null && saved.value?.command !== undefined);
function assertScope(captured: PhysicalOwner, token: number): void {
  if (token !== generation || JSON.stringify(captured) !== JSON.stringify(owner.value)) throw new Error('身份、仓库或原件已变化，停止发送。');
}
async function refresh(captured: PhysicalOwner, token: number): Promise<void> {
  const state = loadPhysical(captured);
  if (state.command?.result) {
    const result = await request(physicalPath(captured, `checks/${state.command.result.receiptId}`), physicalResultSchema);
    await verifyPhysicalResult(result, captured, state.command.body);
    if (JSON.stringify(result.receipt) !== JSON.stringify(state.command.result)) throw new Error('服务器原回执与本地原记录不一致。');
  }
  assertScope(captured, token); saved.value = state;
}
async function run(action: (captured: PhysicalOwner, assertCurrent: () => void) => Promise<void>): Promise<void> {
  if (busy.value) return; const captured = { ...owner.value }; const token = generation; busy.value = true; error.value = '';
  try { await lockPhysical(captured, async () => { assertScope(captured, token); await action(captured, () => assertScope(captured, token)); }); await refresh(captured, token); }
  catch (cause) {
    if (token === generation) {
      error.value = cause instanceof TypeError ? errorText(cause) : cause instanceof Error ? cause.message : errorText(cause);
      try { await refresh(captured, token); } catch { if (token === generation) saved.value = null; }
    }
  }
  finally { if (token === generation) busy.value = false; }
}
async function prepare(captured: PhysicalOwner, assertCurrent: () => void): Promise<void> {
  const result: Observation[] = []; let cursor = ''; let total = '';
  do {
    const query = new URLSearchParams({ warehouseId: captured.warehouseId, section: 'inventory', limit: '200', cursor });
    const page = await request(`/original-relays/${captured.relayId}/independent-reconciliation?${query}`, reviewSchema); assertCurrent();
    if (page.section !== 'inventory' || page.contextSha256 !== props.data.contextSha256 || page.original.relayId !== captured.relayId ||
        page.original.warehouseId !== captured.warehouseId) throw new Error('完整依据已变化，请先刷新原件核对。');
    result.push(...page.items.map(stock => ({ stock, status: '' as const, note: '' }))); total = page.total; cursor = page.nextCursor ?? '';
    if (result.length > 10000 || page.hasMore !== Boolean(cursor)) throw new Error('有效包分页超过登记上限或不完整，请保全核对。');
  } while (cursor);
  if (result.length !== Number(total) || new Set(result.map(row => row.stock.captureId)).size !== result.length) throw new Error('有效包集合不完整。');
  rows.value = result; checkedAt.value = new Date().toISOString();
}
async function selected(event: Event): Promise<void> {
  const target = event.target as HTMLInputElement; const file = target.files?.[0]; if (!file) return;
  await run(async (captured, assertCurrent) => {
    if (file.size < 1 || file.size > 10 * 1024 * 1024) throw new Error('单附件须为1字节至10MiB。');
    const sha = await digest(await file.arrayBuffer()); assertCurrent(); const state = loadPhysical(captured);
    if (state.command) throw new Error('原登记已保存，请先按原键恢复。');
    let row = state.uploads.find(value => value.sha === sha && value.size === file.size);
    if (!row) {
      if (state.uploads.length >= 5) throw new Error('本条登记最多五份附件，请保留已选原文件。');
      row = { key: crypto.randomUUID(), epoch: currentRecoveryEpoch(), name: file.name.slice(0, 255), sha, size: file.size, reference: null };
      state.uploads.push(row); savePhysical(state);
    }
    if (!row.reference) await sendPhysicalAttachment({ owner: captured, file, saved: row, assertCurrent });
  }); target.value = '';
}
async function record(captured: PhysicalOwner, assertCurrent: () => void): Promise<void> {
  const state = loadPhysical(captured);
  if (!state.command) {
    if (selfCheck.value || rows.value.length !== Number(props.data.summary.activeTotal)) throw new Error('须由独立主管逐包检查完整有效集合。');
    if (state.uploads.some(row => !row.reference)) throw new Error('存在结果未明的附件，请重新选择同一文件恢复原上传。');
    const parsed = evidenceSchema.safeParse({ contextSha256: props.data.contextSha256, checkedAt: checkedAt.value, area: area.value, reason: reason.value,
      packages: rows.value.map(row => ({ captureId: row.stock.captureId, status: row.status, note: row.note })), attachments: state.uploads.map(row => row.reference) });
    if (!parsed.success) throw new Error('请填写责任区、检查时间及说明，并选择全部有效包的实际观察；卸下或未找到须说明。');
    const body = parsed.data;
    state.command = { key: crypto.randomUUID(), epoch: currentRecoveryEpoch(), body, result: null }; savePhysical(state);
  }
  assertCurrent(); await sendPhysicalCheck(captured, assertCurrent);
}
watch(() => [owner.value.userId, owner.value.warehouseId, owner.value.relayId], () => {
  generation++; saved.value = null; rows.value = []; area.value = ''; reason.value = ''; checkedAt.value = ''; error.value = ''; busy.value = false;
  const captured = { ...owner.value }; const token = generation;
  void refresh(captured, token).catch(cause => { if (token === generation) error.value = cause instanceof Error ? cause.message : '无法读取原请求'; });
}, { immediate: true });
onUnmounted(() => { generation++; });
</script>
<template>
  <section class="physical-panel">
    <h2>主管实物登记</h2><p>逐包记录现场观察并上传依据，登记后仍待人工关联与对账。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="selfCheck">原采集者须由另一名有权主管核对。</p>
    <template v-if="saved?.command">
      <p>原检查：{{ saved.command.body.area }} · {{ saved.command.body.checkedAt }} · {{ saved.command.body.packages.length }}包</p>
      <p>{{ saved.command.body.reason }}</p>
      <p v-if="saved.command.result" role="status">实物观察已登记，仍待对账；关联结果见下方，未记账。回执 {{ saved.command.result.receiptId }}</p>
      <template v-else><p>原登记结果未明。已保存原请求，刷新后仍按原键恢复。</p>
        <el-button :disabled="busy || selfCheck" @click="run(record)">按原键恢复登记结果</el-button></template>
    </template>
    <template v-else>
      <el-button :disabled="frozen || selfCheck || !saved" @click="run(prepare)">载入全部有效包并逐包检查</el-button>
      <div v-if="rows.length || checkedAt">
        <p>已载入 {{ rows.length }}包，须全部选择实际观察。</p>
        <el-input v-model="area" aria-label="实物检查责任区" placeholder="车厢或责任区" maxlength="200" :disabled="frozen" />
        <el-input v-model="checkedAt" aria-label="实物检查时间" placeholder="UTC检查时间" :disabled="frozen" />
        <el-input v-model="reason" aria-label="实物检查说明" type="textarea" maxlength="500" :disabled="frozen" />
        <article v-for="row in rows" :key="row.stock.captureId" class="physical-package">
          <h3>原序 {{ row.stock.sequence }} · {{ row.stock.barcode }}</h3>
          <el-radio-group v-model="row.status" :aria-label="`实物观察 ${row.stock.barcode}`" :disabled="frozen">
            <el-radio value="LOADED">已装载</el-radio><el-radio value="UNLOADED">已卸下</el-radio><el-radio value="NOT_FOUND">未找到</el-radio>
          </el-radio-group>
          <el-input v-model="row.note" :aria-label="`实物说明 ${row.stock.barcode}`" placeholder="卸下或未找到必须说明" maxlength="500" :disabled="frozen" />
        </article>
        <label>实物附件（每份最多10MiB，最多五份）<input type="file" aria-label="上传实物附件" :disabled="frozen || selfCheck" @change="selected" /></label>
        <p v-for="upload in saved?.uploads" :key="upload.key">{{ upload.name }} · {{ upload.size }}字节 · {{ upload.reference ? '已保存原字节' : '结果未明，请重新选择同一文件' }}</p>
        <el-button type="primary" :disabled="frozen || selfCheck || !saved?.uploads.length" @click="run(record)">保存实物观察并登记</el-button>
      </div>
    </template>
  </section>
</template>
<style scoped>
.physical-panel { border-top: 1px solid var(--separator); margin-top: var(--space-6); padding-top: var(--space-4); }
.physical-package { margin-block: var(--space-4); padding: var(--space-4); border: 1px solid var(--separator); border-radius: var(--radius-control); }
.physical-panel :deep(.el-input) { margin-bottom: var(--space-2); width: 100%; }
.physical-panel :deep(.el-radio-group) { display: flex; flex-wrap: wrap; gap: var(--space-2); margin-bottom: var(--space-2); }
input[type='file'] { display: block; width: 100%; margin-block: var(--space-2); }
</style>
