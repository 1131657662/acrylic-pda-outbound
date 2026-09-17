import { Box, Document, Upload, Files, Setting, Monitor } from '@element-plus/icons-vue';
import { ref } from 'vue';
export const navigationCollapsed = ref(window.innerWidth < 1280);

export const navigation = [
  { group: '装车作业', items: [{ path: '/orders', label: '装车订单', permission: 'order:read', icon: Document }] },
  { group: '库存管理', items: [
    { path: '/inventory/bales', label: '库存查询', permission: 'inventory:read', icon: Box },
    { path: '/inventory/imports', label: '库存导入', permission: 'inventory:read', icon: Upload },
    { path: '/inventory/mappings', label: '发货代码', permission: 'inventory:read', icon: Files },
  ] },
  { group: '系统管理', items: [
    { path: '/policies', label: '出库规则', permission: 'order:read', icon: Setting },
    { path: '/devices', label: 'PDA 设备', permission: 'device:manage', icon: Monitor },
  ] },
];
export function firstWorkspacePath(permissions: string[]): string {
  return navigation.flatMap(group => group.items).find(item => permissions.includes(item.permission))?.path ?? '/orders';
}
