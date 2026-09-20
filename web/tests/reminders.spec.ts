import { test, expect, chromium } from '@playwright/test';
import { resolve } from 'node:path';
import { reminderFixture, reminderLogin, secondReminderPage } from './reminder-fixture';
import { noPageOverflow } from './ui-fixture';

test('real cursor pages preserve detail return and show Chinese system reminder', async ({ page }) => {
  await reminderLogin(page); await secondReminderPage(page);
  await expect(page.locator('.orders-grid .el-table__body tr')).toHaveCount(3);
  const url = page.url(); const id = reminderFixture().orderId;
  const link = page.locator(`.orders-grid [data-order-id="${id}"]`); await link.click();
  await expect(page.getByRole('tab', { name: '装车记录', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByText('短装原因：库存不足，申请短装')).toBeVisible();
  await page.getByRole('tab', { name: '操作记录', exact: true }).click();
  await page.getByRole('tab', { name: '操作时间线', exact: true }).click();
  const row = page.locator('.el-table__row').filter({ hasText: '待审超时提醒' });
  await expect(row).toContainText('系统'); await expect(row).toContainText('待审满30分钟');
  await expect(row).toContainText(reminderFixture().submissionId);
  await page.getByRole('button', { name: '返回超时待审', exact: true }).click();
  await expect(page).toHaveURL(url); await expect(link).toBeFocused();
  await page.getByRole('button', { name: '上一页', exact: true }).click();
  await expect(page.locator('.orders-grid .el-table__body tr')).toHaveCount(20);
});

test('initial failure and refresh failure support retry without mislabeling stale results', async ({ page }) => {
  await reminderLogin(page);
  await page.route('**/api/v1/review-reminders?**', route => route.abort('failed'));
  await page.getByRole('button', { name: '刷新待办', exact: true }).click();
  await expect(page.getByText(/下方为上次读取结果/)).toBeVisible();
  await expect(page.locator('.orders-grid .el-table__body tr')).toHaveCount(20);
  await page.reload(); await expect(page.getByRole('heading', { name: '待办读取失败' })).toBeVisible();
  await expect(page.locator('.orders-grid')).toHaveCount(0);
  await page.unroute('**/api/v1/review-reminders?**');
  await page.getByRole('button', { name: '重试查询', exact: true }).click();
  await expect(page.locator('.orders-grid .el-table__body tr')).toHaveCount(20);
});

test('warehouse change clears old cursor and cannot show a late response from previous warehouse', async ({ page }) => {
  await reminderLogin(page); await secondReminderPage(page);
  let release: () => void = () => {}; const gate = new Promise<void>(resolve => { release = resolve; });
  let received: () => void = () => {}; const waiting = new Promise<void>(resolve => { received = resolve; });
  await page.route('**/api/v1/review-reminders?**', async route => {
    const response = await route.fetch(); received(); await gate; await route.fulfill({ response });
  });
  await page.getByRole('button', { name: '刷新待办', exact: true }).click();
  await waiting; await page.locator('.warehouse-select').click();
  await page.getByRole('option', { name: '测试仓', exact: true }).nth(1).click();
  await expect(page).not.toHaveURL(/cursor=/); release();
  await expect(page.getByRole('heading', { name: '暂无超时待审提醒' })).toBeVisible();
  await expect(page.locator('.orders-grid')).toHaveCount(0);
});

test('read-only clerk cannot discover inbox through navigation or direct route', async ({ page }) => {
  await reminderLogin(page, true);
  await expect(page.locator('.navigation').getByRole('link', { name: '超时待审', exact: true })).toHaveCount(0);
  let calls = 0; page.on('request', request => { if (request.url().includes('/api/v1/review-reminders')) calls++; });
  await page.goto(`/review-reminders?warehouse=${reminderFixture().warehouseId}`);
  await expect(page.getByRole('heading', { name: '没有此页面的访问权限' })).toBeVisible();
  expect(calls).toBe(0);
});

test('invalid and foreign warehouse cursor can return to first page', async ({ page }) => {
  await reminderLogin(page); await secondReminderPage(page);
  const cursor = new URL(page.url()).searchParams.get('cursor')!;
  await page.goto(`/review-reminders?warehouse=${reminderFixture().otherWarehouseId}&cursor=${encodeURIComponent(cursor)}`);
  await expect(page.getByRole('heading', { name: '待办读取失败' })).toBeVisible();
  await expect(page.getByText(/分页位置无效/)).toBeVisible();
  await page.getByRole('button', { name: '返回首页', exact: true }).click();
  await expect(page.getByRole('heading', { name: '暂无超时待审提醒' })).toBeVisible();
});

test('desktop low height and narrow layouts keep pagination reachable', async ({ page }) => {
  await reminderLogin(page);
  for (const [width, height] of [[1440, 900], [1280, 500], [390, 700]]) {
    await page.setViewportSize({ width: width!, height: height! });
    await page.getByRole('navigation', { name: '待办分页' }).scrollIntoViewIfNeeded();
    await noPageOverflow(page);
    await expect(page.getByRole('button', { name: '下一页', exact: true })).toBeInViewport();
    await page.screenshot({ path: `../.local/r05-inbox-20260919/inbox-${width}x${height}.png`, animations: 'disabled' });
  }
});

test('real approval removes the batch when returning to same cursor and retains history', async ({ page }) => {
  await reminderLogin(page); await secondReminderPage(page);
  const url = page.url(); const id = reminderFixture().orderId;
  await page.locator(`.orders-grid [data-order-id="${id}"]`).click();
  await page.getByLabel('主管审批原因', { exact: true }).fill('本机待办联调已核对实物及短装');
  await page.getByRole('button', { name: '批准当前短装出库', exact: true }).click();
  await page.getByRole('button', { name: '确认此决定', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(page.getByText('确认出库 2 包 · 601.5 kg · 已自动扣账')).toBeVisible();
  await page.getByRole('button', { name: '返回超时待审', exact: true }).click();
  await expect(page).toHaveURL(url); await expect(page.locator('.orders-grid .el-table__body tr')).toHaveCount(2);
  await expect(page.locator(`.orders-grid [data-order-id="${id}"]`)).toHaveCount(0);
  const response = await page.request.get(`/api/v1/orders/${id}/events?warehouseId=${reminderFixture().warehouseId}`);
  expect(response.ok()).toBe(true);
  const body = await response.json();
  expect(body.data.items.filter((row: { type: string }) => row.type === 'SUBMISSION_REVIEW_REMINDER')).toHaveLength(1);
});

test('native 200 percent browser zoom keeps inbox readable and controls reachable', async () => {
  const extension = resolve('tests/zoom-extension');
  const context = await chromium.launchPersistentContext('', { channel: 'chromium', headless: true,
    viewport: { width: 1440, height: 900 }, baseURL: process.env.OUTBOUND_WEB_URL,
    args: [`--disable-extensions-except=${extension}`, `--load-extension=${extension}`] });
  try {
    const worker = context.serviceWorkers()[0] ?? await context.waitForEvent('serviceworker');
    const page = context.pages()[0] ?? await context.newPage(); await reminderLogin(page);
    const zoom = await worker.evaluate(async () => {
      const api = (globalThis as unknown as { chrome: { tabs: { query(options: { url: string }): Promise<{ id: number }[]>;
        setZoom(id: number, value: number): Promise<void>; getZoom(id: number): Promise<number> } } }).chrome.tabs;
      const id = (await api.query({ url: 'http://127.0.0.1/*' }))[0]!.id;
      await api.setZoom(id, 2); return api.getZoom(id);
    });
    expect(zoom).toBe(2); await expect.poll(() => page.evaluate(() => innerWidth)).toBe(720);
    await page.getByRole('navigation', { name: '待办分页' }).scrollIntoViewIfNeeded();
    await noPageOverflow(page); await expect(page.getByRole('button', { name: '下一页', exact: true })).toBeInViewport();
    await page.screenshot({ path: '../.local/r05-inbox-20260919/inbox-zoom200.png', animations: 'disabled' });
  } finally { await context.close(); }
});
