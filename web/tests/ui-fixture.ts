import { readFileSync } from 'node:fs';
import { z } from 'zod';
import { expect, type Page } from '@playwright/test';
export const uiFixture = () => z.object({ username: z.string(), password: z.string(), warehouseId: z.string(),
  otherWarehouseId: z.string(), viewer: z.string(), inventoryViewer: z.string(), orders: z.array(z.string()) })
  .parse(JSON.parse(readFileSync('../server/build/order-ui-browser-fixture.json', 'utf8')));
export async function uiLogin(page: Page, role: 'username' | 'viewer' | 'inventoryViewer' = 'username'): Promise<void> {
  const fixture = uiFixture();
  await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill(fixture[role]);
  await page.getByLabel('密码', { exact: true }).fill(fixture.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.locator('.workspace')).toBeVisible();
}
export async function queryOrders(page: Page): Promise<void> {
  await page.goto(`/orders?warehouse=${uiFixture().warehouseId}&search=${encodeURIComponent('苏A-分页验证')}&page=1&pageSize=20`);
  await expect(page.locator('.result-summary')).toContainText('共 43 单');
}
export async function noPageOverflow(page: Page): Promise<void> {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  const header = await page.locator('.topbar').boundingBox();
  expect(header?.y).toBe(0);
  const main = await page.locator('.page-scroll').boundingBox();
  expect(main?.y).toBeGreaterThanOrEqual((header?.height ?? 0) - 1);
}
