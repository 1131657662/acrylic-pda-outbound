import { createRouter, createWebHistory } from 'vue-router';
import { useSessionStore } from './session';
import { ApiError } from './api';
import { firstWorkspacePath } from './navigation';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('./auth/LoginPage.vue') },
    { path: '/pda-download', component: () => import('./pda-download/DownloadPage.vue') },
    { path: '/inventory/imports', component: () => import('./inventory/ImportsPage.vue') },
    { path: '/inventory/bales', component: () => import('./inventory/InventoryPage.vue') },
    { path: '/inventory/mappings', component: () => import('./inventory/MappingsPage.vue') },
    { path: '/orders', component: () => import('./orders/OrdersPage.vue') },
    { path: '/exports', component: () => import('./exports/ExportCenterPage.vue') },
    { path: '/review-reminders', component: () => import('./review/ReviewRemindersPage.vue') },
    { path: '/temporary-reviews', component: () => import('./temporary-review/TemporaryReviewsPage.vue') },
    { path: '/temporary-reviews/:id', component: () => import('./temporary-review/TemporaryReviewPage.vue') },
    { path: '/orders/:id', component: () => import('./orders/OrderDetailPage.vue') },
    { path: '/orders/:id/pc-outbound', component: () => import('./pc/PcOutboundPage.vue') },
    { path: '/orders/:id/pc-outbound/exception', component: () => import('./pc/PcExceptionPage.vue') },
    { path: '/policies', component: () => import('./policies/PoliciesPage.vue') },
    { path: '/devices', component: () => import('./devices/DevicesPage.vue') },
    { path: '/users', component: () => import('./users/UsersPage.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/orders' },
  ],
});

router.beforeEach(async (to) => {
  if (to.path === '/login' || to.path === '/pda-download') return true;
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
  if (typeof value === 'string' && /^\/review-reminders(?:\?|$)/.test(value) && permissions.some(p => ['submission:review', 'temporary_order:confirm'].includes(p))) return value;
  if (typeof value === 'string' && /^\/temporary-reviews(?:\/[0-9a-f-]+)?(?:\?|$)/.test(value) && permissions.includes('temporary_order:confirm')) return value;
  if (typeof value === 'string' && /^\/exports(?:\?|$)/.test(value) && permissions.includes('order:read')) return value;
  if (typeof value === 'string' && /^\/(orders(?:\/[^/?#]+(?:\/pc-outbound(?:\/exception)?)?)?|inventory\/(imports|bales|mappings)|policies|devices|users)(?:\?|$)/.test(value)) {
    const route = router.resolve(value);
    const permission = route.path.startsWith('/orders') || route.path === '/policies' ? 'order:read' : route.path === '/devices' ? 'device:manage' : route.path === '/users' ? 'user:manage' : 'inventory:read';
    if (permissions.includes(permission)) return value;
  }
  return firstWorkspacePath(permissions);
}
