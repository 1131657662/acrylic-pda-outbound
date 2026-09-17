<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useSessionStore } from './session';
import { errorText } from './api';
defineProps<{ title: string; busy?: boolean }>();
const session = useSessionStore();
const router = useRouter();
const error = ref('');
async function logout(): Promise<void> {
  try { await session.logout(); await router.replace('/login'); }
  catch (cause) { error.value = errorText(cause); }
}
</script>
<template>
  <div class="workspace">
    <aside class="sidebar"><div class="sidebar-brand"><span class="brand-mark small">纤</span><div>腈纶仓储<small>出库管理工作台</small></div></div>
      <p class="nav-caption">库存管理</p>
      <router-link to="/inventory/imports" class="nav-item">库存文件导入</router-link>
      <router-link to="/inventory/bales" class="nav-item">库存查询与版本</router-link>
      <router-link to="/inventory/mappings" class="nav-item">发货代码映射</router-link>
      <p class="nav-caption">装车作业</p>
      <router-link to="/orders" class="nav-item">装车订单</router-link>
      <router-link v-if="session.identity?.permissions.includes('order:read')" to="/policies" class="nav-item">出库政策</router-link>
      <div class="sidebar-bottom">主数据 · 装车作业的起点</div>
    </aside>
    <div class="workspace-body">
      <header class="topbar"><span>仓储管理 / {{ title }}</span><div class="topbar-actions">
        <el-select v-model="session.warehouseId" aria-label="当前仓库" :disabled="busy" style="width: 160px">
          <el-option v-for="warehouse in session.identity?.warehouses" :key="warehouse.id" :value="warehouse.id" :label="warehouse.name" />
        </el-select><span>{{ session.identity?.displayName }}</span><el-button text :disabled="busy" @click="logout">退出</el-button>
      </div></header>
      <main class="page-content"><el-alert v-if="error" :title="error" type="error" :closable="false" /><slot /></main>
    </div>
  </div>
</template>
