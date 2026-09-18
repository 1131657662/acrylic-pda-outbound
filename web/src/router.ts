import { createRouter, createWebHistory } from 'vue-router';
import { useSessionStore } from './session';
import { ApiError } from './api';
import { firstWorkspacePath } from './navigation';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('./auth/LoginPage.vue') },
    { path: '/inventory/imports', component: () => import('./inventory/ImportsPage.vue') },
    { path: '/inventory/bales', component: () => import('./inventory/InventoryPage.vue') },
    { path: '/inventory/mappings', component: () => import('./inventory/MappingsPage.vue') },
    { path: '/orders', component: () => import('./orders/OrdersPage.vue') },
    { path: '/orders/:id', component: () => import('./orders/OrderDetailPage.vue') },
    { path: '/orders/:id/pc-outbound', component: () => import('./pc/PcOutboundPage.vue') },
    { path: '/policies', component: () => import('./policies/PoliciesPage.vue') },
    { path: '/devices', component: () => import('./devices/DevicesPage.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/orders' },
  ],
});

router.beforeEach(async (to) => {
  if (to.path === '/login') return true;
  const session = useSessionStore();
  try {
    await session.load();
    const warehouse = to.query.warehouse;
    if (typeof warehouse === 'string' && session.identity?.warehouses.some(item => item.id === warehouse)) session.warehouseId = warehouse;
    else if (warehouse) return { path: to.path, query: { ...to.query, warehouse: session.warehouseId, page: '1' }, replace: true };
    return true;
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 401) return session.identity ? false : { path: '/login', query: { redirect: to.fullPath } };
    return true;
  }
});

export function loginDestination(value: unknown, permissions: string[]): string {
  if (typeof value === 'string' && /^\/(orders(?:\/[^/?#]+(?:\/pc-outbound)?)?|inventory\/(imports|bales|mappings)|policies|devices)(?:\?|$)/.test(value)) {
    const route = router.resolve(value);
    const permission = route.path.startsWith('/orders') || route.path === '/policies' ? 'order:read' : route.path === '/devices' ? 'device:manage' : 'inventory:read';
    if (permissions.includes(permission)) return value;
  }
  return firstWorkspacePath(permissions);
}
