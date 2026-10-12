import { expect, type Page, type Locator } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { z } from 'zod';
import { receiptSchema } from '../src/corrections/header-contracts';

const account = z.object({ username: z.string(), password: z.string() });
const inputSchema = z.object({ warehouse: z.uuid(), order: z.uuid(), maker: account, checker: account });
export function headerInput(): z.infer<typeof inputSchema> {
  if (!process.env.P06_WEB_INPUT) throw new Error('Preserved isolated header input required');
  return inputSchema.parse(JSON.parse(readFileSync(process.env.P06_WEB_INPUT, 'utf8').replace(/^\uFEFF/, '')));
}
export function headerEvidence(name: string, bytes: string | Buffer): void {
  if (!process.env.P06_WEB_EVIDENCE) throw new Error('Evidence directory required');
  writeFileSync(`${process.env.P06_WEB_EVIDENCE}/${name}`, bytes);
}
export async function headerOpen(page: Page, role: 'maker' | 'checker'): Promise<void> {
  const input = headerInput(); const actor = input[role];
  await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(actor.username);
  await page.getByLabel('密码', { exact: true }).fill(actor.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: role === 'maker' ? '装车订单' : '抬头更正', exact: true })).toBeVisible();
  await page.goto(role === 'maker' ? `/orders/${input.order}?warehouse=${input.warehouse}&tab=corrections`
    : `/header-corrections?warehouse=${input.warehouse}`);
  if (role === 'checker') {
    await page.getByLabel('原订单编号', { exact: true }).fill(input.order);
    await page.getByRole('button', { name: '读取抬头更正', exact: true }).click();
  }
  await expect(page.getByRole('region', { name: '抬头更正与撤销补偿' })).toBeVisible();
  await expect(page.getByRole('button', { name: '刷新抬头与更正记录', exact: true })).toBeEnabled();
}
export function headerDocument(page: Page, id: string): Locator {
  return page.locator(`[data-header-document="${id}"]`);
}
export async function headerDialog(page: Page, options: { button: Locator; reason: string; vehicle?: string }): Promise<void> {
  await options.button.click();
  await expect(page.getByRole('dialog')).toBeVisible();
  if (options.vehicle !== undefined) await page.getByLabel('更正车号', { exact: true }).fill(options.vehicle);
  await page.getByLabel('抬头更正操作原因', { exact: true }).fill(options.reason);
}
export async function headerSubmit(page: Page, label: string): Promise<z.infer<typeof receiptSchema>> {
  const response = page.waitForResponse(r => r.url().endsWith('/api/v1/corrections') && r.request().method() === 'POST');
  await page.getByRole('dialog').getByRole('button', { name: label, exact: true }).click();
  const result = await response; const bytes = await result.body();
  expect(result.status(), bytes.toString('utf8')).toBe(200);
  const receipt = receiptSchema.parse(JSON.parse(bytes.toString('utf8')));
  headerEvidence(`${receipt.commandId}.command.json`, result.request().postData() ?? '');
  headerEvidence(`${receipt.commandId}.receipt.json`, bytes);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  return receipt;
}
