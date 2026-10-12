import { Box, Document, Upload, Files, Setting, Monitor, User, Bell } from '@element-plus/icons-vue';
import { ref } from 'vue';
export const navigationCollapsed = ref(window.innerWidth < 1280);

export const navigation = [
  { group: '装车作业', items: [
    { path: '/orders', label: '装车订单', permission: 'order:read', icon: Document },
    { path: '/header-corrections', label: '抬头更正', permission: 'outbound:correct', icon: Document },
    { path: '/temporary-reviews', label: '临时待确认', permission: 'temporary_order:confirm', icon: Files },
    { path: '/temporary-dispositions', label: '临时实物处置', permission: 'order:dispose', icon: Files },
    { path: '/exports', label: '导出中心', permission: 'order:read', icon: Files },
    { path: '/rescue', label: '主管只读救援', permission: 'rescue:authorize', icon: Files },
    { path: '/review-reminders', label: '超时待审', permission: 'submission:review', icon: Bell },
  ] },
  { group: '库存管理', items: [
    { path: '/inventory/bales', label: '库存查询', permission: 'inventory:read', icon: Box },
    { path: '/inventory/imports', label: '库存导入', permission: 'inventory:read', icon: Upload },
    { path: '/inventory/mappings', label: '发货代码', permission: 'inventory:read', icon: Files },
  ] },
  { group: '系统管理', items: [
    { path: '/users', label: '人员与权限', permission: 'user:manage', icon: User },
    { path: '/policies', label: '出库规则', permission: 'order:read', icon: Setting },
    { path: '/devices', label: 'PDA 设备', permission: 'device:manage', icon: Monitor },
  ] },
];
export function firstWorkspacePath(permissions: string[]): string {
  return navigation.flatMap(group => group.items).find(item => canNavigate(item, permissions))?.path ?? '/orders';
}
export function canNavigate(item: { path: string; permission: string }, permissions: string[] = []): boolean {
  return permissions.includes(item.permission) || (item.path === '/review-reminders' && permissions.includes('temporary_order:confirm'))
    || (item.path === '/header-corrections' && permissions.includes('correction:approve'));
}
