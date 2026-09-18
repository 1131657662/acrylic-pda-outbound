import { test, expect, type Page } from '@playwright/test';
import { uiLogin, uiFixture } from './ui-fixture';

for (const [path, api, empty] of [
  ['/inventory/bales', '/inventory/bales', '没有符合条件的包'],
  ['/inventory/imports', '/inventory/imports', '还没有导入记录'],
  ['/inventory/mappings', '/delivery-mappings', '尚无发货代码与产品对应关系'],
  ['/policies', '/policies/current', '尚未设置：'],
  ['/devices', '/devices', '本仓库尚无已登记设备'],
]) {
  test(`auxiliary read failure is recoverable at ${path}`, async ({ page }) => {
    await uiLogin(page); let fail = true;
    await page.route(`**/api/v1${api}?*`, route => fail ? route.abort('failed') : route.continue());
    await page.goto(`${path}?warehouse=${uiFixture().warehouseId}`);
    await expect(page.getByRole('button', { name: '重试查询', exact: true })).toBeVisible();
    await expect(page.getByText(empty!, { exact: false })).not.toBeVisible();
    if (path === '/inventory/bales') await page.screenshot({ path: '../.local/r04-stock-query-error.png', animations: 'disabled' });
    fail = false; await page.getByRole('button', { name: '重试查询', exact: true }).click();
    await expect(page.getByRole('button', { name: '重试查询', exact: true })).not.toBeVisible();
    await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0);
  });
}

async function stageImportPages(page: Page): Promise<void> {
  const warehouse = uiFixture().warehouseId;
  const csrf = (await (await page.request.get('/api/v1/auth/csrf')).json());
  for (let index = 0; index < 12; index++) {
    const response = await page.request.post('/api/v1/inventory/imports', {
      headers: { [csrf.data.headerName]: csrf.data.token, 'X-Server-Recovery-Epoch': csrf.serverRecoveryEpoch, 'Idempotency-Key': crypto.randomUUID() },
      multipart: { warehouseId: warehouse, file: { name: `r04-${index}.txt`, mimeType: 'text/plain', buffer: Buffer.from(`invalid-${crypto.randomUUID()}\r\n`.repeat(55)) } },
    });
    expect(response.status()).toBe(200);
  }
}

test('imports use server cursors and restore page scroll and focus from detail', async ({ page }) => {
  await uiLogin(page); await stageImportPages(page);
  const warehouse = uiFixture().warehouseId;
  await page.setViewportSize({ width: 1280, height: 500 });
  await page.goto(`/inventory/imports?warehouse=${warehouse}`);
  const pager = page.getByRole('navigation', { name: '导入记录分页' });
  const second = page.waitForResponse(r => r.url().includes('/inventory/imports?') && !!new URL(r.url()).searchParams.get('cursor'));
  await pager.getByRole('button', { name: '下一页', exact: true }).click();
  expect((await second).ok()).toBe(true); await expect(pager).toContainText('第 2 页');
  const open = page.getByRole('button', { name: '查看', exact: true }).first();
  await open.scrollIntoViewIfNeeded();
  await page.evaluate(() => document.addEventListener('click', () => {
    sessionStorage.setItem('r04-click-scroll', String(document.querySelector('.page-scroll')?.scrollTop));
  }, { once: true, capture: true }));
  const name = await page.locator('.el-table__body tr').first().locator('td').first().innerText();
  await open.click(); await expect(page.getByRole('heading', { name: '库存导入核对', exact: true })).toBeVisible();
  const top = Number(await page.evaluate(() => sessionStorage.getItem('r04-click-scroll')));
  await expect(page.locator('.detail-panel')).toContainText(name);
  const details = page.getByRole('navigation', { name: '文件明细分页' });
  await details.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(page.locator('.detail-panel .el-table__body tr').first().locator('td').first()).toHaveText('51');
  await page.goBack(); await expect(pager).toContainText('第 2 页');
  await expect(open).toBeFocused();
  expect(await page.locator('.page-scroll').evaluate(el => el.scrollTop)).toBeCloseTo(top, 0);
  await open.click(); await page.getByRole('button', { name: '返回导入记录' }).click();
  await expect(pager).toContainText('第 2 页'); await expect(open).toBeFocused();
  await pager.getByRole('button', { name: '上一页', exact: true }).click(); await expect(pager).toContainText('第 1 页');
});

test('failed next page keeps previous results and retry uses original cursor', async ({ page }) => {
  await uiLogin(page); await stageImportPages(page); await page.goto(`/inventory/imports?warehouse=${uiFixture().warehouseId}`);
  const pager = page.getByRole('navigation', { name: '导入记录分页' });
  const original = await page.locator('.el-table__body tr').first().innerText();
  let fail = true; const cursors: string[] = [];
  await page.route('**/api/v1/inventory/imports?*', async route => {
    const cursor = new URL(route.request().url()).searchParams.get('cursor');
    if (cursor) { cursors.push(cursor); if (fail) { await route.abort('failed'); return; } }
    await route.continue();
  });
  await pager.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(page.getByText('当前仍显示上次查询结果。')).toBeVisible();
  await expect(pager).toContainText('第 1 页'); expect(await page.locator('.el-table__body tr').first().innerText()).toBe(original);
  fail = false; await page.getByRole('button', { name: '重试查询' }).click();
  await expect(pager).toContainText('第 2 页'); expect(cursors).toHaveLength(2); expect(cursors[0]).toBe(cursors[1]);
});

test('mapping history closes back to the source row', async ({ page }) => {
  await uiLogin(page); await page.goto(`/inventory/mappings?warehouse=${uiFixture().warehouseId}`);
  const history = page.getByRole('button', { name: '历史', exact: true }).first();
  await history.click(); const drawer = page.getByRole('dialog');
  await expect(drawer).toBeVisible(); await expect(drawer.locator('.el-table__body tr')).not.toHaveCount(0);
  await page.keyboard.press('Escape'); await expect(drawer).toBeHidden(); await expect(history).toBeFocused();
});

test('desktop import detail returns to the original table position', async ({ page }) => {
  await uiLogin(page); await stageImportPages(page);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`/inventory/imports?warehouse=${uiFixture().warehouseId}`);
  const open = page.getByRole('button', { name: '查看', exact: true }).last();
  await open.scrollIntoViewIfNeeded();
  const table = page.locator('.auxiliary-stack .el-scrollbar__wrap');
  const position = await table.evaluate(el => el.scrollTop); expect(position).toBeGreaterThan(0);
  await open.click(); await page.getByRole('button', { name: '返回导入记录' }).click();
  await expect(open).toBeFocused();
  expect(await table.evaluate(el => el.scrollTop)).toBeCloseTo(position, 0);
});

test('late import results cannot overwrite a newly selected warehouse', async ({ page }) => {
  await uiLogin(page);
  let release: (() => void) | undefined;
  const gate = new Promise<void>(resolve => { release = resolve; });
  const f = uiFixture();
  await page.route('**/api/v1/inventory/imports?*', async route => {
    const response = await route.fetch();
    if (new URL(route.request().url()).searchParams.get('warehouseId') === f.warehouseId) await gate;
    await route.fulfill({ response });
  });
  await page.goto(`/inventory/imports?warehouse=${f.warehouseId}`);
  await expect(page.getByRole('heading', { name: '正在查询', exact: true })).toBeVisible();
  await page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '当前仓库' }) }).click();
  await page.getByRole('option', { name: '界面验证二仓' }).click();
  await expect(page.getByRole('heading', { name: '还没有导入记录' })).toBeVisible();
  const oldResponse = page.waitForResponse(r => r.url().includes('/inventory/imports?') && new URL(r.url()).searchParams.get('warehouseId') === f.warehouseId);
  release?.(); await (await oldResponse).finished(); await page.unrouteAll({ behavior: 'wait' });
  await expect(page).toHaveURL(new RegExp(`warehouse=${f.otherWarehouseId}`));
  await expect(page.locator('.el-table__body tr:visible')).toHaveCount(0);
  await expect(page.getByRole('navigation', { name: '导入记录分页' })).toContainText('第 1 页');
});
