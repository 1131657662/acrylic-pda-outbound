import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { z } from 'zod';

test('short shipment approval recovers its lost response without creating a second decision', async ({ page }) => {
  const fixture = z.object({ username: z.string(), password: z.string(), orderId: z.string().uuid(), submissionId: z.string().uuid() })
    .parse(JSON.parse(readFileSync(resolve('../server/build/submission-browser-fixture.json'), 'utf8')));
  await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill(fixture.username);
  await page.getByLabel('密码', { exact: true }).fill(fixture.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '装车订单', exact: true })).toBeVisible();
  await page.goto(`/orders/${fixture.orderId}?tab=records`);
  const panel = page.locator('section').filter({ has: page.getByRole('heading', { name: '后台收到的装车记录', exact: true }) });
  await expect(panel).toContainText('后台已接收，待主管处理');
  await expect(panel).toContainText('2 包 · 601.5 kg');
  await expect(panel.locator('.el-table__body tr')).toHaveCount(2);
  await page.getByLabel('主管审批原因', { exact: true }).fill('浏览器实物核对批准短装');
  await page.getByRole('button', { name: '批准当前短装出库', exact: true }).click();
  let originalBody: string | null = null;
  await page.route(`**/api/v1/submissions/${fixture.submissionId}/review`, async (route) => {
    if (originalBody !== null) { expect(route.request().postData()).toBe(originalBody); await route.continue(); return; }
    originalBody = route.request().postData(); expect((await route.fetch()).status()).toBe(200); await route.abort('failed');
  });
  await page.getByRole('button', { name: '确认此决定', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('原审批内容已保留');
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json();
  await page.request.post('/api/v1/auth/web/logout', { headers: { [csrf.data.headerName]: csrf.data.token, 'X-Server-Recovery-Epoch': csrf.serverRecoveryEpoch } });
  await page.getByRole('button', { name: '重试原审批', exact: true }).click();
  const login = page.getByRole('dialog', { name: '重新登录', exact: true });
  await expect(login).toBeVisible();
  await page.getByLabel('重新登录密码', { exact: true }).fill(fixture.password);
  await login.getByRole('button', { name: '重新登录', exact: true }).click();
  await expect(login).toBeHidden();
  await expect(page.getByRole('button', { name: '重试原审批', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '重试原审批', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(panel).toContainText('后台已确认出库');
  await expect(panel).toContainText('已自动扣账');
  await expect(page.locator('.page-title')).toContainText('已确认出库');
  await page.reload(); await expect(panel).toContainText('确认出库 2 包 · 601.5 kg');
  await page.screenshot({ path: '../.local/web-submission-approved.png', fullPage: true, animations: 'disabled' });
});
