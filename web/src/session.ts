import { defineStore } from 'pinia';
import { ref } from 'vue';
import { z } from 'zod';
import { request } from './api';

export const identitySchema = z.object({
  id: z.string().uuid(), username: z.string(), displayName: z.string(), permissions: z.array(z.string()),
  warehouses: z.array(z.object({ id: z.string().uuid(), code: z.string(), name: z.string() })),
});

export const useSessionStore = defineStore('session', () => {
  const identity = ref<z.infer<typeof identitySchema> | null>(null);
  const warehouseId = ref('');
  async function load(): Promise<void> {
    identity.value = await request('/me', identitySchema);
    if (!identity.value.warehouses.some((w) => w.id === warehouseId.value))
      warehouseId.value = identity.value.warehouses[0]?.id ?? '';
  }
  async function login(username: string, password: string): Promise<void> {
    await request('/auth/web/login', z.object({ authenticated: z.boolean() }), {
      method: 'POST', body: new URLSearchParams({ username, password }),
    });
    await load();
  }
  async function logout(): Promise<void> {
    await request('/auth/web/logout', z.object({ authenticated: z.boolean() }), { method: 'POST' });
    identity.value = null;
    warehouseId.value = '';
  }
  return { identity, warehouseId, load, login, logout };
});
