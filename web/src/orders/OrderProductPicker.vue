<script setup lang="ts">
import { ref } from 'vue';
import { request, errorText } from '../api';
import { optionsSchema } from './contracts';
import type { Product } from './contracts';
const props = defineProps<{ warehouseId: string }>();
const emit = defineEmits<{ select: [product: Product] }>();
const rows = ref<Product[]>([]); const selected = ref(''); const busy = ref(false); const error = ref('');
let generation = 0;
async function search(value = ''): Promise<void> {
  const current = ++generation; busy.value = true; error.value = '';
  try {
    const params = new URLSearchParams({ warehouseId: props.warehouseId, search: value, limit: '50' });
    const result = await request(`/orders/products?${params}`, optionsSchema);
    if (generation === current) rows.value = result.products.items;
  } catch (cause) { if (generation === current) error.value = errorText(cause); }
  finally { if (generation === current) busy.value = false; }
}
function choose(code: string): void {
  const product = rows.value.find((row) => row.deliveryCode === code);
  if (product) emit('select', product);
  selected.value = '';
}
</script>
<template>
  <div>
    <el-select v-model="selected" aria-label="添加产品" filterable remote :remote-method="search" :loading="busy"
      placeholder="搜索发货代码、型号或名称" style="width: 100%" @visible-change="(visible: boolean) => visible && search()" @change="choose">
      <el-option v-for="item in rows" :key="item.deliveryCode" :value="item.deliveryCode"
        :label="`${item.deliveryCode} · ${item.model} · ${item.name}`" />
    </el-select>
    <p v-if="error" class="text-error">{{ error }}</p>
  </div>
</template>
