import { test, expect } from '@playwright/test';
import { uiFixture, uiLogin, queryOrders } from './ui-fixture';

test('real pages, exact search, page size and detail return restore scroll and row focus', async ({ page }) => {
  await uiLogin(page); await queryOrders(page);
  await expect(page.locator('.orders-grid .el-table__body tr')).toHaveCount(20);
  await page.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(page).toHaveURL(/page=2/);
  const links = page.locator('.orders-grid .order-link');
  const row = links.nth(15); await row.scrollIntoViewIfNeeded();
  const id = await row.getAttribute('data-order-id');
  const before = await page.locator('.orders-grid .el-scrollbar__wrap').evaluate(el => el.scrollTop);
  await row.click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(/^OB/);
  await page.getByRole('tab', { name: '操作记录', exact: true }).click();
  await page.getByRole('button', { name: '返回订单列表', exact: true }).click();
  await expect(page).toHaveURL(/page=2/);
  await expect(page.getByLabel('订单或车号', { exact: true })).toHaveValue('苏A-分页验证');
  await expect(page.locator(`.orders-grid [data-order-id="${id}"]`)).toBeFocused();
  expect(await page.locator('.orders-grid .el-scrollbar__wrap').evaluate(el => el.scrollTop)).toBeCloseTo(before, 0);
  await page.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(page.locator('.orders-grid .el-table__body tr')).toHaveCount(3);
  await expect(page.getByRole('button', { name: '下一页', exact: true })).toBeDisabled();
  await page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '每页条数' }) }).click();
  await page.getByRole('option', { name: '50 条', exact: true }).click();
  await expect(page.locator('.orders-grid .el-table__body tr')).toHaveCount(43);
  await expect(page).toHaveURL(/page=1&pageSize=50/);
  await page.getByRole('combobox', { name: '订单状态', exact: true }).press('Enter');
  await page.getByRole('option', { name: '已作废', exact: true }).click();
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByRole('heading', { name: '没有符合条件的订单' })).toBeVisible();
});

test('empty filter, bounded page and direct detail refresh preserve query context', async ({ page }) => {
  await uiLogin(page); await queryOrders(page);
  await page.getByLabel('订单或车号', { exact: true }).fill('不存在的车号');
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByRole('heading', { name: '没有符合条件的订单' })).toBeVisible();
  await page.getByRole('button', { name: '清除筛选' }).click();
  await expect(page.locator('.orders-grid')).toBeVisible();
  await page.goto(`/orders?warehouse=${uiFixture().warehouseId}&search=${encodeURIComponent('苏A-分页验证')}&page=999&pageSize=20`);
  await expect(page).toHaveURL(/page=3/);
  await expect(page.getByText('结果已更新，已返回最后一页。')).toBeVisible();
  await page.locator('.orders-grid .order-link').first().click();
  await page.getByRole('tab', { name: '装车记录', exact: true }).click();
  await expect(page).toHaveURL(/tab=records/);
  await page.reload();
  await expect(page.getByRole('tab', { name: '装车记录', exact: true })).toHaveAttribute('aria-selected', 'true');
  await page.getByRole('button', { name: '返回订单列表', exact: true }).click();
  await expect(page).toHaveURL(/page=3/);
  await expect(page.locator('.orders-grid .el-table__body tr')).toHaveCount(3);
});

test('failed refresh keeps labeled previous results; initial loading and failure can retry', async ({ page }) => {
  await uiLogin(page); await queryOrders(page);
  await page.route('**/api/v1/orders?**', route => route.abort('failed'));
  await page.getByRole('button', { name: '刷新当前页', exact: true }).click();
  await expect(page.getByText(/下方保留上次成功查询的结果/)).toBeVisible();
  await expect(page.locator('.orders-grid .el-table__body tr')).toHaveCount(20);
  await page.screenshot({ path: '../.local/ui-orders-error.png', animations: 'disabled' });
  await page.reload();
  await expect(page.getByRole('heading', { name: '订单查询失败' })).toBeVisible();
  await page.unroute('**/api/v1/orders?**');
  await page.getByRole('button', { name: '重试查询', exact: true }).click();
  await expect(page.locator('.orders-grid .el-table__body tr')).toHaveCount(20);
});

test('warehouse switch clears results and rejects late responses from the old warehouse', async ({ page }) => {
  await uiLogin(page); await queryOrders(page);
  let release: (() => void) | undefined;
  const wait = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/v1/orders?**', async route => {
    const response = await route.fetch();
    if (new URL(route.request().url()).searchParams.get('warehouseId') === uiFixture().warehouseId) await wait;
    await route.fulfill({ response });
  });
  await page.getByRole('button', { name: '刷新当前页', exact: true }).click();
  await page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '当前仓库', exact: true }) }).click();
  await page.getByRole('option', { name: '界面验证二仓', exact: true }).click();
  await expect(page.getByRole('heading', { name: '没有符合条件的订单' })).toBeVisible();
  release?.();
  await expect(page.locator('.result-summary')).toContainText('共 0 单');
  await expect(page.locator('.orders-grid .order-link')).toHaveCount(0);
  await page.getByRole('button', { name: '清除筛选' }).click();
  await expect(page.getByRole('heading', { name: '还没有订单' })).toBeVisible();
});

test('expired identity recovers in place without losing applied filters', async ({ page }) => {
  await uiLogin(page); await queryOrders(page);
  await page.route('**/api/v1/orders?**', async route => {
    const response = await route.fetch(); const body = await response.json();
    await route.fulfill({ status: 401, json: { ...body, code: 'UNAUTHENTICATED', message: '登录已失效', data: null } });
  });
  await page.getByRole('button', { name: '刷新当前页', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '重新登录', exact: true });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('textbox').first()).toBeDisabled();
  await page.unroute('**/api/v1/orders?**');
  await page.getByLabel('重新登录密码', { exact: true }).fill(uiFixture().password);
  await dialog.getByRole('button', { name: '重新登录', exact: true }).click();
  await expect(dialog).toBeHidden();
  await page.getByRole('button', { name: '刷新当前页', exact: true }).click();
  await expect(page.getByLabel('订单或车号', { exact: true })).toHaveValue('苏A-分页验证');
  await expect(page.locator('.result-summary')).toContainText('共 43 单');
});

test('read-only and inventory-only accounts have correct actions and access feedback', async ({ page }) => {
  await uiLogin(page, 'viewer'); await queryOrders(page);
  await expect(page.getByRole('button', { name: '新建订单' })).toHaveCount(0);
  await page.locator('.orders-grid .order-link').first().click();
  await expect(page.getByRole('button', { name: '修订草稿' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '作废订单', exact: true })).toHaveCount(0);
  await uiLogin(page, 'inventoryViewer');
  let reads = 0; page.on('request', request => { if (request.url().includes('/api/v1/orders?')) reads++; });
  await page.goto('/orders');
  await expect(page.getByRole('heading', { name: '没有此页面的访问权限' })).toBeVisible();
  expect(reads).toBe(0);
});
