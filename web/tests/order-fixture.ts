import { expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { z } from 'zod';

export async function orderLogin(page: Page): Promise<void> {
  const credentials = process.env.OUTBOUND_UI_FIXTURE
    ? (() => { const f = z.object({ businessUsername: z.string(), password: z.string() }).parse(JSON.parse(readFileSync(resolve('../server/build/order-ui-browser-fixture.json'), 'utf8'))); return { adminUsername: f.businessUsername, adminPassword: f.password }; })()
    : z.object({ adminUsername: z.string(), adminPassword: z.string() }).parse(JSON.parse(readFileSync(resolve('../.local/development.json'), 'utf8')));
  await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill(credentials.adminUsername);
  await page.getByLabel('密码', { exact: true }).fill(credentials.adminPassword);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '装车订单', exact: true })).toBeVisible();
}
export async function orderStock(page: Page): Promise<void> {
  const identity = z.object({ data: z.object({ warehouses: z.array(z.object({ id: z.string() })) }) })
    .parse(await (await page.request.get('/api/v1/me')).json());
  const warehouse = identity.data.warehouses[0]?.id;
  if (!warehouse) throw new Error('Test warehouse missing');
  const csrf = z.object({ data: z.object({ headerName: z.string(), token: z.string() }), serverRecoveryEpoch: z.string() })
    .parse(await (await page.request.get('/api/v1/auth/csrf')).json());
  const headers = { [csrf.data.headerName]: csrf.data.token, 'X-Server-Recovery-Epoch': csrf.serverRecoveryEpoch, 'Idempotency-Key': crypto.randomUUID() };
  const number = String(Date.now() % 10000000).padStart(7, '0');
  const row = (code: string): string => `${'ORDER TEST'.padEnd(20)}${'FIBER'.padEnd(14)}${`${code}X${number}`.padEnd(13)}${'300.5'.padEnd(10)}\r\n`;
  const staged = await page.request.post(`/api/v1/inventory/imports?warehouseId=${warehouse}`, { headers,
    multipart: { file: { name: 'order-browser-sku.txt', mimeType: 'text/plain', buffer: Buffer.from(row('993') + row('994'), 'ascii') } } });
  expect(staged.status()).toBe(200);
  const id = z.object({ data: z.object({ id: z.string() }) }).parse(await staged.json()).data.id;
  const preview = z.object({ data: z.object({ baseline: z.object({ activeSnapshotId: z.string().nullable(), mappingRevision: z.string() }), removed: z.number() }) })
    .parse(await (await page.request.get(`/api/v1/inventory/imports/${id}/diff?warehouseId=${warehouse}`)).json()).data;
  const published = await page.request.post(`/api/v1/inventory/imports/${id}/publish?warehouseId=${warehouse}`, {
    headers: { ...headers, 'Idempotency-Key': crypto.randomUUID() }, data: { expectedActiveSnapshot: preview.baseline.activeSnapshotId,
      expectedMappingRevision: preview.baseline.mappingRevision, acceptIdenticalDuplicates: true, acceptMappingChanges: true,
      confirmedRemovedCount: preview.removed, reason: '订单浏览器测试库存' } });
  expect(published.status()).toBe(200);
}
export async function chooseProduct(page: Page, code: string): Promise<void> {
  const picker = page.getByRole('combobox', { name: '添加产品' });
  await picker.fill(code);
  await page.getByRole('option', { name: new RegExp(`^${code} ·`) }).click();
}
