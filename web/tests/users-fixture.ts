import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { expect, type Page, type Locator } from '@playwright/test';
import { z } from 'zod';

export const usersFixture = () => z.object({ username: z.string(), password: z.string(), warehouseId: z.string(),
  otherWarehouseId: z.string(), viewer: z.string() }).parse(JSON.parse(readFileSync('../server/build/user-management-browser-fixture.json', 'utf8')));
export async function usersLogin(page: Page, viewer = false): Promise<void> {
  const fixture = usersFixture(); await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill(viewer ? fixture.viewer : fixture.username);
  await page.getByLabel('密码', { exact: true }).fill(fixture.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.locator('.workspace')).toBeVisible();
  await page.goto(`/users?warehouse=${fixture.warehouseId}`);
  if (!viewer) await expect(page.getByRole('navigation', { name: '人员列表分页' })).toBeVisible();
}
export async function fillNewUser(page: Page): Promise<{ username: string; password: string; dialog: Locator }> {
  await page.getByRole('button', { name: '新增人员', exact: true }).click();
  const dialog = page.getByRole('dialog'); const username = `ui_${randomUUID().replaceAll('-', '')}`;
  const password = randomUUID();
  await dialog.getByRole('textbox', { name: '登录账号', exact: true }).fill(username);
  await dialog.getByRole('textbox', { name: '人员姓名', exact: true }).fill('浏览器验证人员');
  await dialog.locator('label').filter({ hasText: /^PDA操作员$/ }).click();
  await expect(dialog.getByRole('checkbox', { name: 'PDA操作员', exact: true })).toBeChecked();
  await dialog.getByRole('textbox', { name: '新密码', exact: true }).fill(password);
  await confirmUser(dialog);
  return { username, password, dialog };
}
export async function confirmUser(dialog: Locator): Promise<void> {
  await dialog.getByRole('textbox', { name: '操作原因', exact: true }).fill('本机隔离浏览器验证');
  await dialog.locator('label').filter({ hasText: '已核对人员、授权范围和操作影响' }).click();
}
export async function searchUser(page: Page, username: string): Promise<Locator> {
  await page.getByRole('textbox', { name: '查询登录账号' }).fill(username);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  const row = page.locator('.el-table__row').filter({ hasText: username });
  await expect(row).toHaveCount(1); return row;
}
export async function createUser(page: Page): Promise<{ username: string; password: string; row: Locator }> {
  const input = await fillNewUser(page);
  await input.dialog.getByRole('button', { name: '新增人员', exact: true }).click();
  await expect(input.dialog).toBeHidden();
  return { username: input.username, password: input.password, row: await searchUser(page, input.username) };
}
export async function pendingRecord(page: Page): Promise<string> {
  return page.evaluate(() => Object.entries(sessionStorage).filter(([key]) => key.startsWith('acrylic.user-request.v1:')).map(([, value]) => value).join(''));
}
