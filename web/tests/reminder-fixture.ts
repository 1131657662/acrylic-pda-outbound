import { readFileSync } from 'node:fs';
import { z } from 'zod';
import { expect, type Page } from '@playwright/test';

export const reminderFixture = () => z.object({ username: z.string(), password: z.string(), clerk: z.string(),
  warehouseId: z.string().uuid(), otherWarehouseId: z.string().uuid(), orderId: z.string().uuid(), submissionId: z.string().uuid() })
  .parse(JSON.parse(readFileSync('../server/build/review-reminder-browser-fixture.json', 'utf8')));

export async function reminderLogin(page: Page, clerk = false): Promise<void> {
  const fixture = reminderFixture();
  await page.goto(`/login?redirect=${encodeURIComponent('/review-reminders?warehouse=' + fixture.warehouseId)}`);
  await page.getByLabel('账号', { exact: true }).fill(clerk ? fixture.clerk : fixture.username);
  await page.getByLabel('密码', { exact: true }).fill(fixture.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.locator('.workspace')).toBeVisible();
}

export async function secondReminderPage(page: Page): Promise<void> {
  await page.goto(`/review-reminders?warehouse=${reminderFixture().warehouseId}`);
  await expect(page.locator('.orders-grid .el-table__body tr')).toHaveCount(20);
  await page.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(page).toHaveURL(/cursor=/);
  await expect(page.getByRole('button', { name: '下一页', exact: true })).toBeDisabled();
}
