import { readFileSync } from 'node:fs';
import { z } from 'zod';
import { expect, type Page } from '@playwright/test';
import { pcLogin, pcPath } from './pc-fixture';
import { exceptionViewSchema } from '../src/pc/exception-contracts';
const schema = z.object({ username: z.string(), password: z.string(), target: z.string(), viewer: z.string(), warehouse: z.string(), order: z.string(), query: z.string(), barcodes: z.array(z.string()) });
export type ExceptionFixture = z.infer<typeof schema>;
export function exceptionFixture(index: number): ExceptionFixture { return schema.array().parse(JSON.parse(readFileSync('../server/build/pc-exception-browser-fixture.json', 'utf8')))[index]!; }
export function exceptionPath(f: ExceptionFixture): string { return pcPath(f).replace('/pc-outbound?', '/pc-outbound/exception?'); }
export async function exceptionLogin(page: Page, f: ExceptionFixture, account = f.username): Promise<void> {
  await pcLogin(page, { ...f, username: account }); await page.goto(exceptionPath(f));
  await expect(page.getByRole('heading', { name: 'PC 异常处置与交接', exact: true })).toBeVisible();
}
export async function confirmException(page: Page, title: string): Promise<void> {
  const dialog = page.getByRole('dialog', { name: title, exact: true });
  await dialog.getByLabel('现场核验记录与原因', { exact: true }).fill('按车号逐包现场核对，已确认位置、去向和人员，凭据 E2E-EXCEPTION');
  await dialog.getByText('已核对本单、全部实物及人员交接影响', { exact: true }).click();
  await expect(dialog.getByRole('checkbox')).toBeChecked(); await dialog.getByRole('button', { name: title, exact: true }).click();
}
export async function stopException(page: Page): Promise<void> {
  await page.getByRole('button', { name: '停止并核验实物', exact: true }).click(); await confirmException(page, '确认停止PC作业');
  await expect(page.locator('.pc-exception-planner')).toBeVisible();
}
export async function fillExceptionPlan(page: Page, f: ExceptionFixture, target = f.target, actions = ['本车保留', '卸货并释放', '卸货并隔离']): Promise<void> {
  await page.getByLabel('接续账号', { exact: true }).fill(target);
  for (const [position, index] of [0, 4, 8].entries()) {
    const barcode = f.barcodes[index]!;
    await page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: `处置 ${barcode}`, exact: true }) }).click();
    await page.getByRole('listbox', { name: `处置 ${barcode}`, exact: true }).getByRole('option', { name: actions[position]!, exact: true }).click();
    await page.getByLabel(`核验凭据 ${barcode}`, { exact: true }).fill('现场 A1 区核验，凭据 E2E-' + index);
  }
  await page.getByLabel('交接及处置原因', { exact: true }).fill('换班接续，按实物安排卸货释放和隔离');
  await page.getByText('已逐包核对全部原有效包、实物去向和接续账号', { exact: true }).click();
}
export async function exceptionView(page: Page, f: ExceptionFixture) {
  const response = await page.request.get(`/api/v1/orders/${f.order}/pc/exception?warehouseId=${f.warehouse}`);
  expect(response.ok()).toBe(true); return exceptionViewSchema.parse((await response.json()).data);
}
