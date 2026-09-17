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
  await expect(page.getByRole('heading', { name: '库存文件导入', exact: true })).toBeVisible();
  await page.goto(`/orders/${fixture.orderId}`);
  const panel = page.locator('section').filter({ has: page.getByRole('heading', { name: '装车提交与云端回执', exact: true }) });
  await expect(panel).toContainText('待主管复核');
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
  await page.getByRole('button', { name: '重试原审批', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(panel).toContainText('云端已确认出库');
  await expect(panel).toContainText('已自动扣账');
  await expect(page.locator('.order-detail-title')).toContainText('已确认出库');
  await page.reload(); await expect(panel).toContainText('确认出库 2 包 · 601.5 kg');
  await page.screenshot({ path: '../.local/web-submission-approved.png', fullPage: true, animations: 'disabled' });
});
