import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { z } from 'zod';

const contextSchema = z.object({ username: z.string(), password: z.string(), warehouseId: z.uuid(), orderId: z.uuid() });
const fixtureSchema = z.object({ purpose: z.literal('SYNTHETIC_FAULT_RESTART_CONSUMERS'), storage: contextSchema });
const identitySchema = z.object({ data: z.object({ id: z.uuid(), permissions: z.array(z.string()) }) });
const orderSchema = z.object({ data: z.object({ order: z.object({ rowVersion: z.string(), status: z.string() }) }),
  serverRecoveryEpoch: z.string().regex(/^[1-9][0-9]*$/) });

function fixture() {
  const path = process.env.FAULT_RESTART_CONSUMER_INPUT;
  if (!path) throw new Error('Requires a new isolated synthetic fixture');
  return fixtureSchema.parse(JSON.parse(readFileSync(path, 'utf8'))).storage;
}

async function login(page: Page) {
  const f = fixture();
  await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill(f.username);
  await page.getByLabel('密码', { exact: true }).fill(f.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '装车订单', exact: true })).toBeVisible();
  const identity = identitySchema.parse(await (await page.request.get('/api/v1/me')).json()).data;
  expect(identity.permissions).toContain('order:recall');
  const order = orderSchema.parse(await (await page.request.get(`/api/v1/orders/${f.orderId}?warehouseId=${f.warehouseId}`)).json());
  expect(order.data.order.status).toBe('ASSIGNED');
  return { f, identity, order };
}

function observeWithdrawal(page: Page): () => number {
  let writes = 0;
  page.on('request', request => {
    if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/change-requests')) writes++;
  });
  return () => writes;
}

async function assertClosed(page: Page): Promise<void> {
  await expect(page.locator('.page-title')).toContainText('已领取');
  await expect(page.getByText('故障撤回功能当前关闭；原记录与占用保留。', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '撤回故障订单', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '重试原撤回请求', exact: true })).toHaveCount(0);
  await expect(page.getByRole('dialog')).toHaveCount(0);
}

test.skip(!process.env.FAULT_RESTART_CONSUMER_INPUT, 'Requires an isolated default-off HTTP server and Vite');

test('default-off page hides withdrawal for an authorized owner without sending a command', async ({ page }) => {
  const { f } = await login(page); const writes = observeWithdrawal(page);
  await page.goto(`/orders/${f.orderId}?tab=exceptions`);
  await assertClosed(page); await page.reload(); await assertClosed(page);
  expect(writes()).toBe(0);
});

for (const malformed of [false, true]) {
  test(`default-off reload preserves ${malformed ? 'malformed' : 'valid'} pending bytes without POST`, async ({ page }) => {
    const { f, identity, order } = await login(page); const writes = observeWithdrawal(page);
    const key = `acrylic.fault-withdrawal.v1:${identity.id}:${f.warehouseId}:${f.orderId}`;
    const body = JSON.stringify({ type: 'FAULT_WITHDRAWAL', expectedVersion: order.data.order.rowVersion, reason: '隔离关闭入口：保留原请求' });
    const raw = malformed ? '{synthetic-invalid-json' : JSON.stringify({ userId: identity.id, warehouseId: f.warehouseId,
      orderId: f.orderId, key: randomUUID(), epoch: order.serverRecoveryEpoch, body, receipt: null });
    await page.evaluate(({ key, raw }) => localStorage.setItem(key, raw), { key, raw });
    await page.goto(`/orders/${f.orderId}?tab=exceptions`); await assertClosed(page);
    const message = malformed ? '原撤回请求暂不可读取，请保留浏览器存储并恢复；未发送新请求。'
      : '故障撤回功能当前关闭；原请求已保留，未发送新请求。';
    await expect(page.getByText(message, { exact: true })).toBeVisible();
    await page.reload(); await assertClosed(page);
    await expect(page.getByText(message, { exact: true })).toBeVisible();
    expect(await page.evaluate(key => localStorage.getItem(key), key)).toBe(raw);
    expect(writes()).toBe(0);
  });
}

test('default-off real HTTP rejects an authorized direct withdrawal without changing order state', async ({ page }) => {
  const { f, order } = await login(page);
  const csrfSchema = z.object({ data: z.object({ headerName: z.string(), token: z.string() }) });
  const csrf = csrfSchema.parse(await (await page.request.get('/api/v1/auth/csrf')).json()).data;
  const response = await page.request.post(`/api/v1/orders/${f.orderId}/change-requests?warehouseId=${f.warehouseId}`, {
    headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': randomUUID(), 'X-Server-Recovery-Epoch': order.serverRecoveryEpoch },
    data: { type: 'FAULT_WITHDRAWAL', expectedVersion: order.data.order.rowVersion, reason: '隔离默认关闭HTTP反例' },
  });
  expect(response.status()).toBe(409);
  expect(z.object({ code: z.string() }).parse(await response.json()).code).toBe('FAULT_WITHDRAWAL_DISABLED');
  const after = orderSchema.parse(await (await page.request.get(`/api/v1/orders/${f.orderId}?warehouseId=${f.warehouseId}`)).json());
  expect(after.data).toEqual(order.data);
});
