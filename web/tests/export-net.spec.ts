import { test, expect, type Page } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';

test.skip(!process.env.E05_WEB_INPUT, 'Requires preserved isolated E05 source');
type Input = { username: string; warehouse: string; order: string; latestVersion: number };
const input = (): Input => JSON.parse(readFileSync(process.env.E05_WEB_INPUT!, 'utf8'));
const evidence = (name: string, value: string | Buffer) => writeFileSync(`${process.env.E05_WEB_EVIDENCE}/${name}`, value);
async function open(page: Page): Promise<void> {
  const source = input();
  await page.goto(`/login?redirect=${encodeURIComponent(`/exports?warehouse=${source.warehouse}`)}`);
  await page.getByLabel('账号', { exact: true }).fill(source.username);
  await page.getByLabel('密码', { exact: true }).fill('Test-password-123');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '跨订单最新净值汇总', exact: true })).toBeVisible();
}
test('latest net page recovers accepted lost response with same key and downloads retained zero rows', async ({ page }) => {
  await open(page); await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: '查询汇总', exact: true }).click();
  await expect(page.locator('.summary-head')).toContainText('1 单 · 0 包 · 0 kg');
  await expect(page.locator('.summary-head')).toContainText('原确认 2 包');
  await page.getByText('查看 1 个订单及结果版本', { exact: true }).click();
  await expect(page.locator('.summary-members')).toContainText(`V${input().latestVersion}`);
  let accepted = ''; let key = ''; let body = '';
  await page.route('**/api/v1/exports?*', async route => {
    if (route.request().method() !== 'POST' || accepted) { await route.continue(); return; }
    key = route.request().headers()['idempotency-key']!; body = route.request().postData()!;
    const response = await route.fetch(); expect(response.status()).toBe(200);
    const bytes = await response.body(); accepted = JSON.parse(bytes.toString()).data.id;
    evidence('lost-request.json', body); evidence('lost-response.json', bytes); await route.abort('failed');
  });
  await page.getByRole('button', { name: '导出汇总 Excel', exact: true }).click();
  await expect(page.getByRole('button', { name: '重试原申请', exact: true })).toBeVisible();
  await page.reload(); await expect(page.getByRole('button', { name: '重试原申请', exact: true })).toBeVisible();
  await page.unroute('**/api/v1/exports?*');
  const replay = page.waitForRequest(r => r.method() === 'POST' && new URL(r.url()).pathname === '/api/v1/exports');
  await page.getByRole('button', { name: '重试原申请', exact: true }).click();
  const sent = await replay; expect(sent.headers()['idempotency-key']).toBe(key); expect(sent.postData()).toBe(body);
  const card = page.locator(`[data-export-id="${accepted}"]`);
  await expect(card).toContainText('各单版本已固定');
  await expect(card.getByRole('button', { name: '下载', exact: true })).toBeVisible({ timeout: 20000 });
  await card.getByText('查看固定结果版本', { exact: true }).click();
  await expect(card).toContainText(`V${input().latestVersion}`);
  const pendingDownload = page.waitForEvent('download'); await card.getByRole('button', { name: '下载', exact: true }).click();
  const download = await pendingDownload; expect(download.suggestedFilename()).toContain('latest-net-');
  await download.saveAs(`${process.env.E05_WEB_EVIDENCE}/browser-zero-net.xlsx`);
  await expect(page.getByRole('button', { name: '重试原申请', exact: true })).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await card.scrollIntoViewIfNeeded(); await page.screenshot({ path: `${process.env.E05_WEB_EVIDENCE}/mobile-fixed-task.png`, fullPage: true });
  evidence('browser-result.json', JSON.stringify({ accepted, key, sameKey: true, sameBody: true, width: 390 }));
});
test('PDF capacity counts retained original rows even if all net rows are zero', async ({ page }) => {
  await open(page);
  await page.route('**/api/v1/exports/summary?*', async route => {
    const response = await route.fetch(); const payload = await response.json();
    expect(response.status()).toBe(200); payload.data.originalCount = 1001; payload.data.actualCount = 0;
    await route.fulfill({ response, json: payload });
  });
  await page.getByRole('button', { name: '查询汇总', exact: true }).click();
  await expect(page.getByRole('button', { name: '导出汇总 PDF', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: '导出汇总 Excel', exact: true })).toBeEnabled();
});
test('old durable request without basis remains original confirmation', async ({ page }) => {
  await open(page); const source = input(); const key = randomUUID();
  const actor = (await (await page.request.get('/api/v1/me')).json()).data.id as string;
  const day = await page.getByLabel('开始日期', { exact: true }).inputValue();
  await page.evaluate(({ actor, warehouse, key, day }) => {
    localStorage.setItem(`r08-center:${actor}:${warehouse}`, JSON.stringify({ key, epoch: '1',
      action: { kind: 'range', format: 'XLSX', range: { fromDate: day, throughDate: day } } }));
  }, { actor, warehouse: source.warehouse, key, day });
  await page.reload(); const pending = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/v1/exports');
  await page.getByRole('button', { name: '重试原申请', exact: true }).click();
  const response = await pending; expect(response.status()).toBe(200); const view = (await response.json()).data;
  expect(response.request().headers()['idempotency-key']).toBe(key); expect(view.basis).toBe('ORIGINAL_CONFIRMED');
  expect(view.members).toEqual([{ orderId: source.order, resultVersion: 1 }]);
  evidence('legacy-pending-response.json', await response.body());
});
test('durable storage failure prevents transmitting new export', async ({ page }) => {
  await open(page); await page.getByRole('button', { name: '查询汇总', exact: true }).click();
  await expect(page.getByRole('button', { name: '导出汇总 Excel', exact: true })).toBeEnabled(); let posts = 0;
  page.on('request', r => { if (r.method() === 'POST' && new URL(r.url()).pathname === '/api/v1/exports') posts++; });
  await page.evaluate(() => { const original = Storage.prototype.setItem; Storage.prototype.setItem = function (key, value) {
    if (key.startsWith('r08-center:')) throw new Error('Isolated quota'); original.call(this, key, value);
  }; });
  await page.getByRole('button', { name: '导出汇总 Excel', exact: true }).click();
  await expect(page.getByRole('alert').filter({ hasText: '原请求结果尚未确认' })).toBeVisible(); expect(posts).toBe(0);
});
