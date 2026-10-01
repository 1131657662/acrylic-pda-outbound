import { test, expect, type Page } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { z } from 'zod';

const fixture = z.object({ username: z.string(), password: z.string(), warehouseId: z.string(), otherWarehouseId: z.string(), orderId: z.string(), orderNo: z.string() });
function data() { return fixture.parse(JSON.parse(readFileSync(process.env.HANDOVER_BROWSER_FIXTURE!, 'utf8'))); }
test.skip(process.env.HANDOVER_LIST_VERIFY !== 'true', 'Requires explicit isolated fixture');

async function login(page: Page): Promise<void> {
  const item = data();
  await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill(item.username);
  await page.getByLabel('密码', { exact: true }).fill(item.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.locator('.workspace')).toBeVisible();
  const identity = await page.request.get('/api/v1/me');
  expect(identity.status()).toBe(200);
  const body = z.object({ data: z.object({ permissions: z.array(z.string()) }) }).parse(await identity.json());
  expect(body.data.permissions).toEqual(expect.arrayContaining(['order:read', 'order:handover']));
}

test('order entry, real pagination, verified detail, return and responsive layout', async ({ page }) => {
  const item = data(); const calls: { method: string; url: string }[] = [];
  page.on('request', request => { if (request.url().includes('/api/v1/handover')) calls.push({ method: request.method(), url: request.url() }); });
  await login(page);
  await page.goto(`/orders/${item.orderId}?warehouse=${item.warehouseId}&search=preserved&page=3`);
  await page.getByRole('button', { name: '查看换机交接', exact: true }).click();
  await expect(page.getByText('本页 20 条，还有后续记录')).toBeVisible();
  await page.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(page.getByText('本页 1 条，已到末页')).toBeVisible();
  const position = new URL(page.url()).searchParams.get('handoverCursor'); expect(position).toBeTruthy();
  await page.getByRole('button', { name: '查看交接', exact: true }).click();
  for (const label of ['原机停止', '原提交结果', '资料完整', '实物核验']) await expect(page.getByText(label, { exact: true })).toBeVisible();
  await expect(page.getByText('合成交接查询验收', { exact: true })).toBeVisible();
  await layouts(page);
  await page.getByRole('button', { name: '返回交接列表', exact: true }).click();
  await expect(page.getByText('本页 1 条，已到末页')).toBeVisible();
  expect(new URL(page.url()).searchParams.get('handoverCursor')).toBe(position);
  await page.getByRole('button', { name: '返回订单', exact: true }).click();
  await expect(page.getByRole('button', { name: '查看换机交接', exact: true })).toBeVisible();
  expect(new URL(page.url()).searchParams.get('search')).toBe('preserved');
  expect(new URL(page.url()).searchParams.get('page')).toBe('3');
  expect(calls.length).toBeGreaterThan(3); expect(calls.every(call => call.method === 'GET')).toBe(true);
  writeFileSync(`${process.env.HANDOVER_BROWSER_OUTPUT}/read-requests.json`, JSON.stringify(calls, null, 2));
});

async function layouts(page: Page): Promise<void> {
  for (const [width, height] of [[1366, 768], [1440, 900], [1920, 1080], [1024, 768], [390, 844], [720, 450]]) {
    await page.setViewportSize({ width: width!, height: height! });
    await expect(page.getByRole('button', { name: '返回交接列表', exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: `${process.env.HANDOVER_BROWSER_OUTPUT}/detail-${width}.png`, fullPage: true });
  }
}

for (const status of [401, 403, 404]) {
  test(`read failure ${status} clears case rows`, async ({ page }) => {
    const item = data(); await login(page);
    await page.route('**/api/v1/handovers/cases?*', route => route.fulfill({ status, contentType: 'application/json',
      body: JSON.stringify({ code: 'DENIED', message: '查询被拒绝', serviceMode: 'NORMAL' }) }));
    await page.goto(`/orders/${item.orderId}/handovers?warehouse=${item.warehouseId}`);
    await expect(page.getByText('暂时无法查看交接记录', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '查看交接', exact: true })).toHaveCount(0);
  });
}

test('mismatched response does not display another order', async ({ page }) => {
  const item = data(); await login(page);
  await page.route('**/api/v1/handovers/cases?*', async route => {
    const response = await route.fetch(); const body = await response.json(); body.orderId = item.warehouseId;
    await route.fulfill({ response, json: body });
  });
  await page.goto(`/orders/${item.orderId}/handovers?warehouse=${item.warehouseId}`);
  await expect(page.getByText('暂时无法查看交接记录', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '查看交接', exact: true })).toHaveCount(0);
});

test('warehouse change rejects a late previous response', async ({ page }) => {
  const item = data(); await login(page);
  let release: () => void = () => {}; let observed: () => void = () => {};
  const held = new Promise<void>(resolve => { release = resolve; });
  const ready = new Promise<void>(resolve => { observed = resolve; });
  await page.route('**/api/v1/handovers/cases?*', async route => {
    const response = await route.fetch(); observed(); await held;
    await route.fulfill({ response }).catch(() => {});
  });
  await page.goto(`/orders/${item.orderId}/handovers?warehouse=${item.warehouseId}`, { waitUntil: 'domcontentloaded' });
  await ready;
  const warehouse = page.getByRole('combobox', { name: '当前仓库', exact: true });
  await warehouse.focus(); await warehouse.press('ArrowDown');
  await page.getByRole('option', { name: '交接隔离仓', exact: true }).click();
  release();
  await expect(page).toHaveURL(new RegExp(`warehouse=${item.otherWarehouseId}`));
  await expect(page.getByRole('button', { name: '查看交接', exact: true })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: `${item.orderNo} · 换机交接`, exact: true })).toHaveCount(0);
});
