import { readFileSync } from 'node:fs';
import { z } from 'zod';
import { expect, type Page } from '@playwright/test';

export function candidateFixture() {
  const path = process.env.TEMP_CANDIDATE_PRIVATE;
  if (!path) throw new Error('Protected isolated fixture is required');
  return z.object({ username: z.string(), password: z.string(), warehouse: z.string().uuid(), order: z.string().uuid() })
    .parse(JSON.parse(readFileSync(path, 'utf8')));
}
export async function candidateLogin(page: Page): Promise<void> {
  const f = candidateFixture(); await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill(f.username);
  await page.getByLabel('密码', { exact: true }).fill(f.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.locator('.workspace')).toBeVisible();
}
export async function candidateOrder(page: Page): Promise<void> {
  const f = candidateFixture(); await page.goto(`/orders/${f.order}?warehouse=${f.warehouse}`);
  await expect(page.getByRole('region', { name: '临时单重复候选核对' })).toContainText('共 21 个候选');
}
export async function checkCandidateCount(page: Page): Promise<void> {
  await expect(page.getByRole('region', { name: '临时单重复候选核对' })).toContainText('共 21 个候选');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
}
