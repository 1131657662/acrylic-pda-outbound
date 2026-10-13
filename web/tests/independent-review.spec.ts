import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
interface Input { username: string; password: string; relayId: string; warehouseId: string; evidence: string }
test.afterEach(async ({ page }) => { await page.unrouteAll({ behavior: 'wait' }); });
function input(): Input {
  const path = process.env.INDEPENDENT_REVIEW_WEB_INPUT;
  if (!path) throw new Error('Requires actual closed Android original and isolated supervisor');
  return JSON.parse(readFileSync(path, 'utf8')) as Input;
}
async function login(page: Page): Promise<void> {
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url()); url.port = '19434';
    const response = await route.fetch({ url: url.toString(), maxRedirects: 0 }); await route.fulfill({ response });
  });
  const f = input(); await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill(f.username); await page.getByLabel('密码', { exact: true }).fill(f.password);
  await page.getByRole('button', { name: '登录', exact: true }).click(); await expect(page).not.toHaveURL(/\/login/);
  await page.goto(`/independent-reviews?relay=${f.relayId}&warehouse=${f.warehouseId}`);
}
test('supervisor reads actual complete pages and original inventory peers at 390px', async ({ page }) => {
  const f = input(); await login(page); await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click();
  await expect(page.getByText('历史签发、原登记设备钥及完整结束已核验。实物未核验，尚未人工关联。')).toBeVisible();
  await page.getByRole('button', { name: '下一页', exact: true }).click(); await expect(page.getByText(/第 2 页/)).toBeVisible();
  await page.getByRole('button', { name: '有效包库存', exact: true }).click(); await expect(page.getByRole('heading', { name: /原序/ }).first()).toBeVisible();
  await page.getByRole('button', { name: '其他独立原件', exact: true }).click(); await expect(page.getByText(/跨设备有效条码重叠/)).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByText('待实物对账 · 未关联 · 未记账')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: `${f.evidence}/390-peers.png`, fullPage: true });
  await page.getByText(/跨设备有效条码重叠/).scrollIntoViewIfNeeded(); await page.screenshot({ path: `${f.evidence}/390-conflict.png` });
  await page.getByRole('button', { name: '有效包库存', exact: true }).click();
  await expect(page.getByRole('heading', { name: /原序/ }).first()).toBeVisible();
  const stock = page.locator('article').filter({ has: page.getByText('查看库存原依据', { exact: true }) }).first();
  await stock.getByText('查看库存原依据', { exact: true }).click();
  await expect(stock.getByText(/^服务端：/)).toBeVisible(); await expect(stock.getByText(/^仓库快照 /)).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: `${f.evidence}/390-inventory.png`, fullPage: true });
  await stock.getByText(/^仓库快照 /).scrollIntoViewIfNeeded();
  await page.screenshot({ path: `${f.evidence}/390-inventory-facts.png` });
  writeFileSync(`${f.evidence}/product.json`, JSON.stringify({ actualHttp: true, originalAndroid: true, syntheticSupervisor: true, width: 390 }));
});
test('a failed page removes previous evidence and can reload from the original ID', async ({ page }) => {
  await login(page); await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click();
  await expect(page.getByText(/第 1 页/)).toBeVisible();
  await page.route('**/independent-reconciliation?**', async route => {
    const url = new URL(route.request().url()); url.port = '19434';
    url.searchParams.set('cursor', Buffer.from(`${'0'.repeat(64)}|candidates|1`).toString('base64url'));
    const response = await route.fetch({ url: url.toString(), maxRedirects: 0 }); await route.fulfill({ response });
  });
  await page.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('已变化'); await expect(page.getByText(/第 1 页/)).toBeHidden();
  await page.unroute('**/independent-reconciliation?**'); await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click();
  await expect(page.getByText(/第 1 页/)).toBeVisible();
});
