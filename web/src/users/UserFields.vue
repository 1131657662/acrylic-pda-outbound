<script setup lang="ts">
import type { UserOptions } from './contracts';
defineProps<{ options: UserOptions; currentWarehouse: string; disabled: boolean }>();
const displayName = defineModel<string>('displayName', { required: true });
const roles = defineModel<string[]>('roles', { required: true });
const warehouses = defineModel<string[]>('warehouses', { required: true });
</script>
<template>
  <el-form-item label="人员姓名" required><el-input v-model="displayName" aria-label="人员姓名" maxlength="100" :disabled="disabled" /></el-form-item>
  <el-form-item label="角色" required>
    <el-checkbox-group v-model="roles" :disabled="disabled" class="user-choices" aria-label="人员角色">
      <el-checkbox v-for="role in options.roles" :key="role.code" :value="role.code">{{ role.name }}</el-checkbox>
    </el-checkbox-group>
  </el-form-item>
  <details v-if="roles.length" class="file-proof"><summary>所选角色的操作权限</summary>
    <p v-for="role in options.roles.filter(value => roles.includes(value.code))" :key="role.code"><strong>{{ role.name }}</strong>：{{ role.permissionNames.join('、') }}</p>
  </details>
  <el-form-item label="可访问仓库" required>
    <el-checkbox-group v-model="warehouses" :disabled="disabled" class="user-choices" aria-label="人员仓库">
      <el-checkbox v-for="warehouse in options.warehouses" :key="warehouse.id" :value="warehouse.id" :disabled="warehouse.id === currentWarehouse">{{ warehouse.name }}</el-checkbox>
    </el-checkbox-group>
  </el-form-item>
</template>
