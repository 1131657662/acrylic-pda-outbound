<script setup lang="ts">
import { computed } from 'vue';
import { kilograms } from '../orders/submission-contracts';
import type { PcView } from './contracts';
const props = defineProps<{ view: PcView; disabled: boolean }>();
const emit = defineEmits<{ close: [] }>();
const count = computed(() => props.view.products.reduce((sum, p) => sum + p.actualCount, 0));
const plan = computed(() => props.view.products.reduce((sum, p) => sum + p.plannedCount, 0));
const weight = computed(() => props.view.products.reduce((sum, p) => sum + BigInt(p.weightGrams), 0n).toString());
</script>
<template>
  <aside class="pc-summary" aria-label="本单计划进度">
    <div class="pc-total"><span>本单有效录入 / 计划</span><strong class="numeric">{{ count }} <small>/ {{ plan }} 包</small></strong><span>净重 {{ kilograms(weight) }} kg</span></div>
    <div class="pc-product-progress"><section v-for="product in view.products" :key="product.deliveryCode"><h3>{{ product.deliveryCode }} <span>{{ product.model }}</span></h3><p>{{ product.actualCount }} / {{ product.plannedCount }} 包 <span class="muted">剩余 {{ product.plannedCount - product.actualCount }} 包</span></p><el-progress :percentage="Math.min(100, Math.round(product.actualCount / product.plannedCount * 100))" :show-text="false" /></section></div>
    <div v-if="view.work?.status === 'ACTIVE'" class="pc-close-action"><p class="muted">录入成功尚不代表出库。请核对实物后结单。</p><el-button type="primary" :disabled="disabled || count === 0" @click="emit('close')">结单核对</el-button></div>
  </aside>
</template>
