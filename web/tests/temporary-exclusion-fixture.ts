import { readFileSync } from 'node:fs';
import { z } from 'zod';
import { expect, type Page } from '@playwright/test';

const login = z.object({ username: z.string(), password: z.string() });
export function exclusionFixture() {
  const path = process.env.EXCLUSION_WEB_INPUT;
  if (!path) throw new Error('Fresh isolated exclusion fixture required');
  return z.object({ purpose: z.literal('SYNTHETIC_EXCLUSION_WEB'), supervisor: login, original: login,
    warehouseId: z.uuid(), submissionId: z.uuid(), cancelledId: z.uuid(), unrelatedId: z.uuid() })
    .parse(JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, '')));
}
export async function exclusionLogin(page: Page, original = false): Promise<void> {
  const f = exclusionFixture(); const actor = original ? f.original : f.supervisor;
  await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(actor.username);
  await page.getByLabel('密码', { exact: true }).fill(actor.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '装车订单', exact: true })).toBeVisible();
  await page.goto(`/temporary-reviews/${f.submissionId}?warehouse=${f.warehouseId}`);
  await expect(page.getByRole('heading', { name: '疑似重复正式单', exact: true })).toBeVisible();
}
export async function openExclusion(page: Page, id: string): Promise<void> {
  await page.locator('article.panel').filter({ hasText: id })
    .getByRole('button', { name: '核对并排除该候选', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
}
export async function fillExclusion(page: Page, unrelated = false): Promise<void> {
  const dialog = page.getByRole('dialog');
  if (unrelated) {
    await dialog.getByText('已作废', { exact: true }).click();
    await page.getByRole('option', { name: '不是同一业务', exact: true }).click();
  }
  await dialog.getByLabel('逐单核对依据').fill('Isolated original candidate facts checked');
  await dialog.getByLabel('主管理由').fill('Isolated supervisor decision with retained evidence');
}
