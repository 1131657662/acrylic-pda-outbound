import { createRouter, createWebHistory } from 'vue-router';
import { useSessionStore } from './session';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('./auth/LoginPage.vue') },
    { path: '/inventory/imports', component: () => import('./inventory/ImportsPage.vue') },
    { path: '/inventory/bales', component: () => import('./inventory/InventoryPage.vue') },
    { path: '/inventory/mappings', component: () => import('./inventory/MappingsPage.vue') },
    { path: '/orders', component: () => import('./orders/OrdersPage.vue') },
    { path: '/orders/:id', component: () => import('./orders/OrderDetailPage.vue') },
    { path: '/policies', component: () => import('./policies/PoliciesPage.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/inventory/imports' },
  ],
});

router.beforeEach(async (to) => {
  if (to.path === '/login') return true;
  const session = useSessionStore();
  try { await session.load(); return true; }
  catch { return '/login'; }
});
