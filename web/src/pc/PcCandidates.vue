<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { request, errorText } from '../api';
import FeedbackState from '../components/FeedbackState.vue';
import { kilograms } from '../orders/submission-contracts';
import { candidatesSchema, availabilityLabels, type Candidate, type Candidates, type Product } from './contracts';
const props = defineProps<{ order: string; warehouse: string; products: Product[]; disabled: boolean; version: string }>();
const emit = defineEmits<{ confirm: [candidate: Candidate] }>();
const query = ref(''); const code = ref(''); const result = ref<Candidates | null>(null); const selected = ref<Candidate | null>(null);
const busy = ref(false); const error = ref(''); const composing = ref(false); const input = ref<{ focus: () => void }>();
let generation = 0; let timer: ReturnType<typeof setTimeout> | undefined;
function normalize(): string { return query.value.trim().toUpperCase(); }
function selection(row: Candidate): void { if (!props.disabled && !busy.value && !error.value && row.availability === 'AVAILABLE') selected.value = row; }
async function search(groupCode = code.value, page = 1): Promise<void> {
  clearTimeout(timer); if (composing.value) return;
  const current = ++generation; const text = normalize(); selected.value = null; error.value = '';
  if (!/^[A-Z0-9]{4,11}$/.test(text)) { result.value = null; busy.value = false; return; }
  busy.value = true;
  try {
    const params = new URLSearchParams({ warehouseId: props.warehouse, query: text, code: groupCode, page: String(page) });
    const found = await request(`/orders/${props.order}/pc/candidates?${params}`, candidatesSchema);
    if (current !== generation) return;
    if (groupCode && groupCode !== code.value && result.value?.query === found.query) {
      result.value.groups = result.value.groups.map(group => group.deliveryCode === groupCode ? found.groups[0] ?? group : group);
    } else result.value = found;
    const exact = found.groups.flatMap(group => group.items).find(row => row.barcode === text && row.availability === 'AVAILABLE');
    if (text.length === 11 && exact && !props.disabled) selected.value = exact;
  } catch (cause) { if (current === generation) error.value = errorText(cause); }
  finally { if (current === generation) busy.value = false; }
}
function schedule(): void {
  generation++; selected.value = null; result.value = null; busy.value = false; clearTimeout(timer);
  if (!composing.value) timer = setTimeout(() => { void search(); }, 250);
}
function pieces(barcode: string): string[] {
  const text = result.value?.query ?? ''; const at = text.length === 11 ? 0 : barcode.indexOf(text, 3);
  return at < 0 ? [barcode, '', ''] : [barcode.slice(0, at), barcode.slice(at, at + text.length), barcode.slice(at + text.length)];
}
watch([query, code], schedule);
watch(() => props.version, () => { selected.value = null; void search(); input.value?.focus(); });
watch(() => props.disabled, value => { if (value) selected.value = null; });
onBeforeUnmount(() => { generation++; clearTimeout(timer); });
</script>
<template>
  <section class="pc-candidates">
    <form class="pc-search" @submit.prevent="search()">
      <el-form-item label="条码"><el-input ref="input" v-model="query" aria-label="完整条码或条码片段" maxlength="11" clearable placeholder="完整条码或4位以上片段" @compositionstart="composing = true" @compositionend="composing = false; schedule()" /></el-form-item>
      <el-form-item label="发货代码"><el-select v-model="code" aria-label="本单发货代码" placeholder="本单全部代码"><el-option label="本单全部代码" value="" /><el-option v-for="product in products" :key="product.deliveryCode" :label="product.deliveryCode + ' · ' + product.model" :value="product.deliveryCode" /></el-select></el-form-item>
      <el-button native-type="submit" :loading="busy">查询</el-button>
    </form>
    <p class="muted">查询只查找本单产品。选择具体包并核对实物后，点击“确认计入本单”。回车只查询。</p>
    <el-alert v-if="error" :title="error + ' 请重新查询后选择。'" type="error" :closable="false" />
    <div v-if="result && !error" class="pc-match-summary"><span>{{ result.matchedCodes }} 个代码 · 共 {{ result.total }} 包匹配</span><span v-for="group in result.groups" :key="group.deliveryCode">{{ group.deliveryCode }}：{{ group.total }} 包</span><span v-if="busy">正在更新…</span></div>
    <div class="pc-candidate-scroll" :aria-busy="busy">
      <FeedbackState v-if="busy && !result" title="正在查找条码" loading />
      <FeedbackState v-else-if="!result && !error" title="输入条码开始查询" description="支持4至10位连续片段，完整11位条码按精确匹配查询。" />
      <FeedbackState v-else-if="result?.total === 0" title="本单范围内没有匹配条码" description="请核对标签和所选发货代码。" />
      <template v-else-if="result">
        <section v-for="group in result.groups" :key="group.deliveryCode" class="pc-candidate-group" :aria-label="'发货代码 ' + group.deliveryCode">
          <div class="section-heading"><h3>{{ group.deliveryCode }} <span class="muted">{{ group.total }} 包匹配</span></h3></div>
          <div v-for="row in group.items" :key="row.baleId" class="pc-candidate-row" :class="{ 'is-selected': selected?.baleId === row.baleId }">
            <el-radio :model-value="selected?.baleId" :value="row.baleId" :aria-label="'选择 ' + row.barcode" :disabled="disabled || busy || !!error || row.availability !== 'AVAILABLE'" @change="selection(row)"><span class="barcode">{{ pieces(row.barcode)[0] }}<mark>{{ pieces(row.barcode)[1] }}</mark>{{ pieces(row.barcode)[2] }}</span></el-radio>
            <span class="pc-product-name">{{ row.model }} · {{ row.name }}</span><strong class="numeric">{{ kilograms(row.weightGrams) }} kg</strong>
            <el-tag :type="row.availability === 'AVAILABLE' ? 'info' : 'warning'">{{ availabilityLabels[row.availability] ?? '状态待核对，不能录入' }}</el-tag>
          </div>
          <el-pagination v-if="group.total > group.pageSize" small layout="prev, pager, next" :aria-label="group.deliveryCode + ' 候选分页'" :page-size="group.pageSize" :total="group.total" :current-page="group.page" :disabled="busy" @current-change="search(group.deliveryCode, $event)" />
        </section>
      </template>
    </div>
    <div class="pc-confirm-band">
      <div v-if="selected"><strong class="barcode">{{ selected.barcode }}</strong><p>{{ selected.deliveryCode }} · {{ selected.model }} · {{ kilograms(selected.weightGrams) }} kg</p></div>
      <p v-else class="muted">尚未选择包，选择不会自动计入。</p>
      <el-button type="primary" :disabled="disabled || busy || !!error || !selected" @click="selected && emit('confirm', selected)">确认计入本单</el-button>
    </div>
  </section>
</template>
