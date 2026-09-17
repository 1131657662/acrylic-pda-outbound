import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { z } from 'zod';

test('controlled recall preserves original requests after lost responses and cancels only a stopped empty order', async ({ page }) => {
  const f = z.object({ username: z.string(), password: z.string(), assignedOrderId: z.string().uuid(), stoppedOrderId: z.string().uuid(), changeRequestId: z.string().uuid() })
    .parse(JSON.parse(readFileSync(resolve('../server/build/recall-browser-fixture.json'), 'utf8')));
  await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(f.username); await page.getByLabel('密码', { exact: true }).fill(f.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '装车订单', exact: true })).toBeVisible();
  await page.goto(`/orders/${f.assignedOrderId}?tab=exceptions`);
  await page.getByRole('button', { name: '申请停止装车', exact: true }).click();
  await page.getByLabel('停止原因', { exact: true }).fill('浏览器核对车辆，要求原机保留实物并停止');
  const seen = new Map<string, { body: string | null; key: string | undefined }>();
  await page.route('**/api/v1/**', async route => {
    const url = route.request().url(); const write = route.request().method() === 'POST';
    if (!write || !(/\/change-requests\?/.test(url) || /\/resolve\?/.test(url))) { await route.continue(); return; }
    const previous = seen.get(url); const value = { body: route.request().postData(), key: route.request().headers()['idempotency-key'] };
    if (previous) { expect(value).toEqual(previous); await route.continue(); return; }
    seen.set(url, value); expect((await route.fetch()).status()).toBe(200); await route.abort('failed');
  });
  await page.getByRole('button', { name: '确认此操作', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('结果未确认');
  await page.getByRole('button', { name: '重试原请求', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(page.locator('.page-title')).toContainText('已申请停止');
  await expect(page.getByText('等待原机确认停止', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '主管核验零包作废', exact: true })).toHaveCount(0);
  await page.goto(`/orders/${f.stoppedOrderId}?tab=exceptions`);
  await page.getByRole('button', { name: '主管核验零包作废', exact: true }).click();
  await page.getByLabel('实物核验凭据', { exact: true }).fill('仓库主管现场核验空车；货物仍在原货位，核验记录 RC-01');
  await page.getByRole('button', { name: '确认此操作', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('结果未确认');
  await page.getByRole('button', { name: '重试原请求', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(page.locator('.page-title')).toContainText('已作废');
  await page.reload(); await expect(page.getByText('主管已核验零包作废', { exact: true })).toBeVisible();
  await page.screenshot({ path: '../.local/web-recall-cancelled.png', fullPage: true, animations: 'disabled' });
});
