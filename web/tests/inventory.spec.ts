import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { z } from 'zod';

const credentials = z.object({ adminUsername: z.string(), adminPassword: z.string() })
  .parse(JSON.parse(readFileSync(resolve('../.local/development.json'), 'utf8')));

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill(credentials.adminUsername);
  await page.getByLabel('密码', { exact: true }).fill(credentials.adminPassword);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '库存文件导入', exact: true })).toBeVisible();
}

test('real login, sample upload, paged rows and logout', async ({ page }) => {
  await login(page);
  await page.locator('input[type=file]').setInputFiles(resolve('../docs/sku.txt'));
  await page.getByRole('button', { name: '上传并预检' }).click();
  await expect(page.locator('.metrics').getByText('12,639', { exact: true }).first()).toBeVisible();
  await expect(page.locator('.metrics').getByText('3,801,362', { exact: true })).toBeVisible();
  await expect(page.locator('.detail-panel')).toContainText('003B0051601');
  await page.locator('.detail-panel').getByRole('button', { name: '下一页' }).click();
  const firstLine = page.locator('.detail-panel .el-table__body tr').first().locator('td').first();
  await expect(firstLine).toHaveText('51');
  await page.locator('.detail-panel').getByRole('button', { name: '第一页' }).click();
  await expect(firstLine).toHaveText('1');
  await expect(page.locator('.detail-panel .el-loading-mask:visible')).toHaveCount(0);
  await page.screenshot({ path: '../.local/web-inventory.png', fullPage: true, animations: 'disabled' });
  await page.getByRole('button', { name: '退出', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
});

test('invalid file is retained and clearly blocked', async ({ page }) => {
  await login(page);
  await page.locator('input[type=file]').setInputFiles({ name: 'invalid-sku.txt', mimeType: 'text/plain', buffer: Buffer.from('bad-row\r\n') });
  await page.getByRole('button', { name: '上传并预检' }).click();
  await expect(page.locator('.detail-panel')).toContainText('存在错误 · 阻断');
  await expect(page.locator('.detail-panel')).toContainText('错误 1 行');
  await expect(page.locator('.detail-panel')).toContainText('每行必须为57字节');
});
