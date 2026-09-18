import { readFileSync } from 'node:fs';
import { z } from 'zod';
import { expect, type Page } from '@playwright/test';
const schema = z.object({ username: z.string(), password: z.string(), warehouse: z.string(), order: z.string(),
  query: z.string(), viewer: z.string(), barcodes: z.array(z.string()) });
export type Fixture = z.infer<typeof schema>;
export function pcFixture(index: number): Fixture {
  return schema.array().parse(JSON.parse(readFileSync('../server/build/pc-browser-fixture.json', 'utf8')))[index]!;
}
export function pcPath(f: Fixture): string { return `/orders/${f.order}/pc-outbound?warehouse=${f.warehouse}&search=${encodeURIComponent('苏A')}&page=2&pageSize=20`; }
export async function pcLogin(page: Page, f: Fixture, viewer = false): Promise<void> {
  await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(viewer ? f.viewer : f.username);
  await page.getByLabel('密码', { exact: true }).fill(f.password); await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.locator('.workspace')).toBeVisible();
}
export async function confirmPcDialog(page: Page, reason = '', submit = false): Promise<void> {
  const dialog = page.getByRole('dialog');
  if (reason) await dialog.getByRole('textbox').fill(reason);
  await dialog.getByText('已核对本单、实物及上述操作影响', { exact: true }).click();
  await expect(dialog.getByRole('checkbox')).toBeChecked();
  await dialog.getByRole('button', { name: submit ? '确认结单并提交校验' : /^(确认此操作|发布并开始PC录入)$/ }).click();
  await expect(dialog).toBeHidden();
}
export async function startPc(page: Page, f: Fixture): Promise<void> {
  await page.goto(pcPath(f));
  await page.getByRole('button', { name: '发布并开始PC录入', exact: true }).click();
  await confirmPcDialog(page, '核对仓库车号和计划，开始PC作业');
  await expect(page.getByRole('tab', { name: '候选条码', exact: true })).toBeVisible();
}
export async function addPc(page: Page, barcode: string): Promise<void> {
  await page.getByRole('tab', { name: '候选条码', exact: true }).click();
  await page.getByLabel('完整条码或条码片段', { exact: true }).fill(barcode);
  await expect(page.getByRole('radio', { name: '选择 ' + barcode, exact: true })).toBeChecked();
  await page.getByRole('button', { name: '确认计入本单', exact: true }).click();
  await expect(page.locator('.pc-success')).toContainText('已计入本单');
  await expect(page.locator('.pc-confirm-band')).toContainText('尚未选择包');
}
export async function workView(page: Page, f: Fixture) {
  const response = await page.request.get(`/api/v1/orders/${f.order}/pc?warehouseId=${f.warehouse}`);
  expect(response.ok()).toBe(true); return (await response.json()).data;
}
export async function pendingCount(page: Page): Promise<number> {
  return page.evaluate(() => Object.keys(localStorage).filter(key => key.startsWith('outbound.pc.request.v1:')).length);
}
