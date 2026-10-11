import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { z } from 'zod';

const contextSchema = z.object({ username: z.string(), password: z.string(), warehouseId: z.uuid(), orderId: z.uuid() });
function input() {
  const path = process.env.FAULT_RESTART_CONSUMER_INPUT;
  if (!path) throw new Error('One-shot consumer input is required');
  return z.object({ purpose: z.literal('SYNTHETIC_FAULT_RESTART_CONSUMERS'), web: contextSchema, storage: contextSchema })
    .parse(JSON.parse(readFileSync(path, 'utf8')));
}
async function login(page: Page, f: z.infer<typeof contextSchema>): Promise<void> {
  await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(f.username);
  await page.getByLabel('密码', { exact: true }).fill(f.password); await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '装车订单', exact: true })).toBeVisible();
  await page.goto(`/orders/${f.orderId}?tab=exceptions`);
}
test.skip(!process.env.FAULT_RESTART_CONSUMER_INPUT, 'Requires isolated fault restart consumer server');

test('withdraw and publish recover exact commands after page reload and lost HTTP responses', async ({ page }) => {
  const f = input().web; await login(page, f);
  await page.getByRole('button', { name: '申请停止装车', exact: true }).click();
  await page.getByLabel('停止原因', { exact: true }).fill('隔离浏览器：原机尚未回传STOP');
  await page.getByRole('button', { name: '确认此操作', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(page.locator('.page-title')).toContainText('停止作业处理中');
  const seen = new Map<string, { body: string | null; key?: string; epoch?: string }>();
  await page.route('**/api/v1/orders/**', async route => {
    const request = route.request(); const url = request.url();
    if (request.method() !== 'POST' || !/\/(change-requests|publish)\?/.test(url)) { await route.continue(); return; }
    const value = { body: request.postData(), key: request.headers()['idempotency-key'], epoch: request.headers()['x-server-recovery-epoch'] };
    if (seen.has(url)) { expect(value).toEqual(seen.get(url)); await route.continue(); return; }
    expect(value.key).toMatch(/^[0-9a-f-]{36}$/); seen.set(url, value);
    expect((await route.fetch()).status()).toBe(200); await route.abort('failed');
  });
  await page.getByRole('button', { name: '撤回故障订单', exact: true }).click();
  await page.getByLabel('故障撤回原因', { exact: true }).fill('隔离浏览器：坏机离线，无STOP，实物保留核对');
  await page.getByRole('button', { name: '确认故障撤回', exact: true }).click();
  await expect(page.getByRole('button', { name: '重试原撤回请求', exact: true })).toBeVisible();
  await page.reload(); await expect(page.getByLabel('故障撤回原因', { exact: true })).toBeDisabled();
  await expect(page.getByLabel('故障撤回原因', { exact: true })).toHaveValue('隔离浏览器：坏机离线，无STOP，实物保留核对');
  await page.getByRole('button', { name: '重试原撤回请求', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden(); await expect(page.locator('.page-title')).toContainText('草稿');
  await page.getByRole('button', { name: '发布订单', exact: true }).click();
  await page.getByLabel('发布原因', { exact: true }).fill('隔离浏览器：新设备从头作业');
  await page.getByRole('button', { name: '确认发布', exact: true }).click();
  await expect(page.getByRole('button', { name: '重试原发布请求', exact: true })).toBeVisible();
  await page.reload(); await expect(page.getByLabel('发布原因', { exact: true })).toBeDisabled();
  await expect(page.getByLabel('发布原因', { exact: true })).toHaveValue('隔离浏览器：新设备从头作业');
  await page.getByRole('button', { name: '重试原发布请求', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden(); await expect(page.locator('.page-title')).toContainText('待领取');
  expect(seen.size).toBe(2);
  await page.getByRole('tab', { name: '操作记录', exact: true }).click();
  const history = page.locator('section').filter({ has: page.getByRole('heading', { name: '发布记录', exact: true }) });
  await expect(history.locator('.el-table__body tr')).toHaveCount(2);
  const result = process.env.FAULT_RESTART_WEB_RESULT;
  if (!result) throw new Error('Consumer result path is required');
  writeFileSync(result, JSON.stringify({ orderId: f.orderId, commands: [...seen.values()], reloads: 2, publications: 2 }));
});

test('failed local request persistence sends no withdrawal and preserves the assigned order', async ({ page }) => {
  const f = input().storage; await login(page, f); let writes = 0;
  page.on('request', request => { if (request.method() === 'POST' && request.url().includes('/change-requests?')) writes++; });
  await page.evaluate(() => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
      if (key.startsWith('acrylic.fault-withdrawal.')) throw new DOMException('test storage full', 'QuotaExceededError');
      original.call(this, key, value);
    };
  });
  await page.getByRole('button', { name: '撤回故障订单', exact: true }).click();
  await page.getByLabel('故障撤回原因', { exact: true }).fill('隔离浏览器存储故障反例');
  await page.getByRole('button', { name: '确认故障撤回', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('test storage full');
  expect(writes).toBe(0); await page.getByRole('button', { name: '返回核对', exact: true }).click();
  await expect(page.locator('.page-title')).toContainText('已领取');
});
