import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { z } from 'zod';

test('supervisor requests and approves retained continuation with exact retries after lost responses', async ({ page }) => {
  const f = z.object({ username: z.string(), password: z.string(), orderId: z.string().uuid() })
    .parse(JSON.parse(readFileSync(resolve('../server/build/continuation-browser-fixture.json'), 'utf8')));
  await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(f.username); await page.getByLabel('密码', { exact: true }).fill(f.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '装车订单', exact: true })).toBeVisible(); await page.goto(`/orders/${f.orderId}?tab=exceptions`);
  await page.getByRole('button', { name: '申请恢复装车', exact: true }).click();
  const seen = new Map<string, { body: string | null; key: string | undefined }>();
  await page.route('**/api/v1/**', async route => {
    const url = route.request().url();
    if (route.request().method() !== 'POST' || !(/\/change-requests\?/.test(url) || /\/resolve\?/.test(url))) { await route.continue(); return; }
    const previous = seen.get(url); const value = { body: route.request().postData(), key: route.request().headers()['idempotency-key'] };
    if (previous) { expect(value).toEqual(previous); await route.continue(); return; }
    seen.set(url, value); expect((await route.fetch()).status()).toBe(200); await route.abort('failed');
  });
  await page.getByLabel('恢复原因', { exact: true }).fill('原车保留全部已装包，补扫剩余产品');
  await expect(page.getByRole('button', { name: '确认恢复装车', exact: true })).toBeDisabled();
  await page.getByText('已逐包核对保留清单及卸货、隔离凭据', { exact: true }).click(); await page.getByRole('button', { name: '确认恢复装车', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('结果未确认');
  await expect(page.getByRole('button', { name: '刷新停止状态', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: '重试原请求', exact: true }).click(); await expect(page.getByRole('dialog')).toBeHidden();
  await page.getByRole('button', { name: '主管核验并批准恢复', exact: true }).click();
  await page.getByLabel('恢复装车核验凭据', { exact: true }).fill('主管核验原车货堆与装车清单一致，全量保留，凭据 CW-01');
  await page.getByText('已逐包核对保留清单及卸货、隔离凭据', { exact: true }).click(); await page.getByRole('button', { name: '确认恢复装车', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('结果未确认');
  await page.getByRole('button', { name: '重试原请求', exact: true }).click(); await expect(page.getByRole('dialog')).toBeHidden();
  await expect(page.getByText('主管已批准，待原 PDA 核验', { exact: true })).toBeVisible();
  await expect(page.locator('.page-title')).toContainText('恢复装车'); await page.reload();
  await expect(page.getByText('主管已批准，待原 PDA 核验', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '主管核验零包作废', exact: true })).toHaveCount(0);
  await page.screenshot({ path: '../.local/web-continuation-approved.png', fullPage: true, animations: 'disabled' });
});
