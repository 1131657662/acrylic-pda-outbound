import { test, expect } from '@playwright/test';
import { usersLogin, usersFixture, createUser, searchUser, confirmUser } from './users-fixture';

test('create, grant two warehouses, disable, reset password and inspect history', async ({ page }) => {
  await usersLogin(page); const { username, row } = await createUser(page);
  await row.getByRole('button', { name: '授权与启停' }).click();
  let dialog = page.getByRole('dialog'); await dialog.locator('label').filter({ hasText: /^人员验证二仓$/ }).click();
  await dialog.locator('.el-switch').click(); await confirmUser(dialog);
  await dialog.getByRole('button', { name: '保存人员授权' }).click(); await expect(dialog).toBeHidden();
  await expect(row).toContainText('已停用'); await expect(row).toContainText('人员验证二仓');
  await row.getByRole('button', { name: '重置密码' }).click(); dialog = page.getByRole('dialog');
  await dialog.getByRole('textbox', { name: '新密码', exact: true }).fill(crypto.randomUUID()); await confirmUser(dialog);
  await dialog.getByRole('button', { name: '重置密码', exact: true }).click(); await expect(dialog).toBeHidden();
  await row.getByRole('button', { name: '记录', exact: true }).click();
  await expect(page.getByRole('dialog').locator('.el-table__row')).toHaveCount(3);
  await expect.poll(async () => { const box = (await page.locator('.el-drawer.open').boundingBox())!; return Math.round(box.x + box.width); }).toBe(await page.evaluate(() => innerWidth));
  await page.screenshot({ path: '../.local/r05-user-history.png', animations: 'disabled' }); await page.keyboard.press('Escape');
  await page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '当前仓库' }) }).click();
  await page.getByRole('option', { name: '人员验证二仓', exact: true }).click();
  const otherRow = await searchUser(page, username); await expect(otherRow).toContainText('已停用');
  await otherRow.getByRole('button', { name: '授权与启停' }).click(); dialog = page.getByRole('dialog');
  await dialog.locator('.el-switch').click(); await confirmUser(dialog);
  await dialog.getByRole('button', { name: '保存人员授权' }).click(); await expect(dialog).toBeHidden();
  await expect(otherRow).toContainText('已启用');
});

test('reset password invalidates a real browser session and new password permits login', async ({ page, browser }) => {
  await usersLogin(page); const { username, password, row } = await createUser(page);
  const context = await browser.newContext(); const operator = await context.newPage();
  try {
    await operator.goto(new URL('/login', page.url()).href);
    await operator.getByLabel('账号', { exact: true }).fill(username); await operator.getByLabel('密码', { exact: true }).fill(password);
    await operator.getByRole('button', { name: '登录', exact: true }).click(); await expect(operator.locator('.workspace')).toBeVisible();
    await row.getByRole('button', { name: '重置密码' }).click(); const dialog = page.getByRole('dialog'); const changed = crypto.randomUUID();
    await dialog.getByRole('textbox', { name: '新密码', exact: true }).fill(changed); await confirmUser(dialog);
    await dialog.getByRole('button', { name: '重置密码', exact: true }).click(); await expect(dialog).toBeHidden();
    expect((await operator.request.get(new URL('/api/v1/me', page.url()).href)).status()).toBe(401);
    await operator.reload(); await expect(operator.getByRole('button', { name: '登录', exact: true })).toBeVisible();
    await operator.getByLabel('账号', { exact: true }).fill(username); await operator.getByLabel('密码', { exact: true }).fill(changed);
    await operator.getByRole('button', { name: '登录', exact: true }).click(); await expect(operator.locator('.workspace')).toBeVisible();
  } finally { await context.close(); }
});

test('real pagination preserves applied filters and cancels editor with focus restored', async ({ page }) => {
  await usersLogin(page);
  const first = await page.locator('.el-table__row td:first-child').allTextContents(); expect(first).toHaveLength(20);
  await page.getByRole('textbox', { name: '查询登录账号' }).fill('not_applied');
  await page.locator('.btn-next').click(); await expect(page.getByRole('navigation', { name: '人员列表分页' })).toContainText('第 2 页');
  const second = await page.locator('.el-table__row td:first-child').allTextContents();
  expect(second.length).toBeGreaterThan(0); expect(second.some(value => first.includes(value))).toBe(false);
  expect(new URL(page.url()).searchParams.get('username')).toBeNull();
  const enabled = page.locator('.el-table__row button:enabled').filter({ hasText: '授权与启停' }).first();
  await enabled.focus(); await enabled.click();
  await expect(page.getByRole('dialog')).toBeVisible(); await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toBeHidden(); await expect(enabled).toBeFocused();
  await expect(page.getByRole('navigation', { name: '人员列表分页' })).toContainText('第 2 页');
  await page.reload(); await expect(page.getByRole('navigation', { name: '人员列表分页' })).toContainText('第 2 页');
});

test('current administrator cannot remove own access', async ({ page }) => {
  await usersLogin(page); const row = await searchUser(page, usersFixture().username);
  await expect(row.getByRole('button', { name: '授权与启停' })).toBeDisabled();
  await expect(row.getByRole('button', { name: '重置密码' })).toBeEnabled();
});

test('viewer has no management entry or direct access', async ({ page }) => {
  await usersLogin(page, true);
  await expect(page.getByText('没有此页面的访问权限')).toBeVisible();
  await expect(page.getByRole('button', { name: '新增人员' })).toHaveCount(0);
  expect((await page.request.get(`/api/v1/users?warehouseId=${usersFixture().warehouseId}`)).status()).toBe(403);
});

test('empty query and retry after network failure', async ({ page }) => {
  await usersLogin(page);
  await page.getByRole('textbox', { name: '查询登录账号' }).fill('nonexistent_account');
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.getByText('没有符合条件的人员')).toBeVisible();
  await page.route('**/api/v1/users?*', route => route.abort());
  await page.getByRole('button', { name: '刷新人员列表' }).click();
  await expect(page.getByRole('button', { name: '重试查询' })).toBeVisible();
  await page.screenshot({ path: '../.local/r05-users-query-error.png' });
  await page.unroute('**/api/v1/users?*'); await page.getByRole('button', { name: '重试查询' }).click();
  await expect(page.getByText('没有符合条件的人员')).toBeVisible();
});

test('warehouse switch discards a late response and starts on the first page', async ({ page }) => {
  await usersLogin(page); await page.locator('.btn-next').click();
  await expect(page.getByRole('navigation', { name: '人员列表分页' })).toContainText('第 2 页');
  let release: (() => void) | undefined; let intercepted: (() => void) | undefined; let delivered: (() => void) | undefined;
  const waiting = new Promise<void>(resolve => { release = resolve; });
  const started = new Promise<void>(resolve => { intercepted = resolve; });
  const completed = new Promise<void>(resolve => { delivered = resolve; });
  await page.route('**/api/v1/users?*', async route => {
    const response = await route.fetch();
    const old = new URL(route.request().url()).searchParams.get('warehouseId') === usersFixture().warehouseId;
    if (old) { intercepted?.(); await waiting; }
    await route.fulfill({ response }); if (old) delivered?.();
  });
  try {
    await page.getByRole('button', { name: '刷新人员列表' }).click(); await started;
    await page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '当前仓库' }) }).click();
    await page.getByRole('option', { name: '人员验证二仓', exact: true }).click();
    await expect(page.getByRole('navigation', { name: '人员列表分页' })).toContainText('第 1 页');
    const before = await page.locator('.el-table__row').allTextContents();
    release?.(); await completed; await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0);
    expect(await page.locator('.el-table__row').allTextContents()).toEqual(before);
    expect(new URL(page.url()).searchParams.get('warehouse')).toBe(usersFixture().otherWarehouseId);
  } finally { release?.(); }
});
