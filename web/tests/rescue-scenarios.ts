import { expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { materialSchema } from '../src/rescue/contracts';
import { permanentRescue, downloadedRescue } from './rescue-fixture';

export async function expireInFlight(page: Page): Promise<(string | null)[]> {
  const bodies: (string | null)[] = [];
  await page.route('**/api/v1/rescue-requests/*/approve?*', async route => {
    bodies.push(route.request().postData()); await new Promise(resolve => setTimeout(resolve, 6000));
    const response = await route.fetch(); expect(response.status()).toBe(400); await route.fulfill({ response });
  });
  await page.getByLabel('救援到期时间', { exact: true }).fill(new Date(Date.now() + 4000).toISOString());
  await page.getByLabel('救援审批或撤销理由', { exact: true }).fill('Isolated original expires while in flight');
  await page.getByRole('button', { name: '核准只读救援', exact: true }).click();
  await expect(page.getByRole('button', { name: '保留拒绝原件并重新核准', exact: true })).toBeVisible({ timeout: 15000 });
  await page.unroute('**/api/v1/rescue-requests/*/approve?*');
  await page.reload(); await page.getByRole('button', { name: '读取原救援申请', exact: true }).click();
  await page.getByRole('button', { name: '保留拒绝原件并重新核准', exact: true }).click();
  await expect(page.getByRole('button', { name: '核准只读救援', exact: true })).toBeVisible();
  expect(await page.evaluate(() => Object.keys(localStorage).filter(name => name.includes(':rejected:')).length)).toBe(1);
  return bodies;
}
export async function loseFirstResponses(page: Page) {
  const seen = new Map<string, { body: string | null; key?: string; epoch?: string }>();
  await page.route('**/api/v1/rescue-*/**', async route => {
    const request = route.request(); if (request.method() !== 'POST') { await route.continue(); return; }
    const value = { body: request.postData(), key: request.headers()['idempotency-key'], epoch: request.headers()['x-server-recovery-epoch'] };
    if (seen.has(request.url())) { expect(value).toEqual(seen.get(request.url())); await route.continue(); return; }
    seen.set(request.url(), value); expect((await route.fetch()).status()).toBe(200); await route.abort('failed');
  });
  return seen;
}
export async function approveAfterLoss(page: Page) {
  await permanentRescue(page); await page.getByRole('button', { name: '核准只读救援', exact: true }).click();
  await expect(page.getByRole('button', { name: '重试原救援核准', exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: '读取原救援申请', exact: true }).click();
  await expect(page.getByLabel('救援审批或撤销理由', { exact: true })).toBeHidden();
  await page.getByRole('button', { name: '重试原救援核准', exact: true }).click();
  await expect(page.getByText('已签发只读授权', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '读取原救援申请', exact: true })).toBeEnabled();
  return materialSchema.parse(await downloadedRescue(page));
}
export async function revokeAfterLoss(page: Page) {
  await page.getByLabel('救援审批或撤销理由', { exact: true }).fill('Isolated human revoke: original retained');
  await page.getByRole('button', { name: '撤销原只读授权', exact: true }).click();
  await expect(page.getByRole('button', { name: '重试原救援撤销', exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: '读取原救援申请', exact: true }).click();
  const name = await page.evaluate(() => Object.keys(localStorage).find(key => key.endsWith(':REVOKE')));
  if (!name) throw new Error('Original revoke was not persisted');
  const raw = await page.evaluate(key => localStorage.getItem(key), name); const grantId = randomUUID();
  await page.evaluate(({ key, wrong }) => { const value = JSON.parse(localStorage.getItem(key)!); value.grantId = wrong; localStorage.setItem(key, JSON.stringify(value)); }, { key: name, wrong: grantId });
  await page.getByRole('button', { name: '重试原救援撤销', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('不属于所读原授权');
  await page.evaluate(({ key, original }) => localStorage.setItem(key, original!), { key: name, original: raw });
  await page.getByRole('button', { name: '重试原救援撤销', exact: true }).click();
  await expect(page.getByText('只读授权已撤销', { exact: true })).toBeVisible();
  return materialSchema.parse(await downloadedRescue(page));
}
