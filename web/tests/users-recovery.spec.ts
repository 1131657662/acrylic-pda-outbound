import { test, expect } from '@playwright/test';
import { usersLogin, fillNewUser, pendingRecord, searchUser, createUser, confirmUser } from './users-fixture';

test('lost successful create response recovers after reload without persisting password', async ({ page }) => {
  await usersLogin(page); const { username, password, dialog } = await fillNewUser(page);
  await page.route('**/api/v1/users?*', async route => {
    if (route.request().method() !== 'POST') return route.continue();
    expect((await route.fetch()).ok()).toBe(true); await route.abort();
  });
  await dialog.getByRole('button', { name: '新增人员', exact: true }).click();
  await expect(dialog.getByText('操作结果待核对。原请求已保存在当前标签页，密码未保存。')).toBeVisible();
  const saved = await pendingRecord(page); expect(saved).toContain(username); expect(saved).not.toContain(password); expect(saved).not.toContain('password');
  await page.unroute('**/api/v1/users?*'); await page.reload();
  await page.getByRole('button', { name: '恢复原人员请求' }).click();
  await page.getByRole('dialog').getByRole('button', { name: '查询原提交结果' }).click();
  await expect(page.getByRole('dialog')).toBeHidden(); expect(await pendingRecord(page)).toBe('');
  await searchUser(page, username); await expect(page.getByRole('navigation', { name: '人员列表分页' })).toContainText('共 1 人');
});

test('request never sent may be resent only with original key and reentered password', async ({ page }) => {
  await usersLogin(page); const { username, password, dialog } = await fillNewUser(page);
  await page.route('**/api/v1/users?*', route => route.request().method() === 'POST' ? route.abort() : route.continue());
  await dialog.getByRole('button', { name: '新增人员', exact: true }).click();
  await expect(dialog.getByRole('button', { name: '查询原提交结果' })).toBeEnabled();
  const saved = JSON.parse(await pendingRecord(page)) as { key: string };
  await page.unroute('**/api/v1/users?*'); await page.reload();
  await page.getByRole('button', { name: '恢复原人员请求' }).click();
  await page.getByRole('button', { name: '查询原提交结果' }).click();
  await expect(page.getByRole('button', { name: '补发原请求' })).toBeVisible();
  await page.getByRole('button', { name: '补发原请求' }).click(); await expect(page.getByText('密码至少12位', { exact: true })).toBeVisible();
  await page.getByRole('textbox', { name: '新密码', exact: true }).fill(password);
  const sent = page.waitForRequest(request => request.method() === 'POST' && request.url().includes('/api/v1/users?'));
  await page.getByRole('button', { name: '补发原请求' }).click(); expect((await sent).headers()['idempotency-key']).toBe(saved.key);
  await expect(page.getByRole('dialog')).toBeHidden(); await searchUser(page, username);
});

test('recovered update rejected on old version closes safely and reloads current target', async ({ page }) => {
  await usersLogin(page); const { row } = await createUser(page);
  await row.getByRole('button', { name: '授权与启停' }).click(); await confirmUser(page.getByRole('dialog'));
  await page.route(/\/api\/v1\/users\/[^/?]+\?/, route => route.request().method() === 'POST' ? route.abort() : route.continue());
  await page.getByRole('button', { name: '保存人员授权' }).click();
  await expect(page.getByRole('button', { name: '查询原提交结果' })).toBeEnabled(); await page.reload();
  await page.getByRole('button', { name: '恢复原人员请求' }).click(); await page.getByRole('button', { name: '查询原提交结果' }).click();
  await page.unroute(/\/api\/v1\/users\/[^/?]+\?/);
  await page.route(/\/api\/v1\/users\/[^/?]+\?/, async route => {
    if (route.request().method() !== 'POST') return route.continue();
    const response = await page.request.get('/api/v1/me'); const body = await response.json() as Record<string, unknown>;
    await route.fulfill({ status: 409, json: { ...body, code: 'USER_VERSION_CONFLICT', message: '人员资料已变化', data: null } });
  });
  await page.getByRole('button', { name: '补发原请求' }).click();
  await expect(page.getByText('本次操作未执行。请关闭窗口后重新发起操作，并核对最新人员资料。')).toBeVisible();
  await expect(page.getByRole('button', { name: '保存人员授权' })).toHaveCount(0); expect(await pendingRecord(page)).toBe('');
  await page.getByRole('button', { name: '取消', exact: true }).click(); await page.unroute(/\/api\/v1\/users\/[^/?]+\?/);
  await row.getByRole('button', { name: '授权与启停' }).click(); await confirmUser(page.getByRole('dialog'));
  await page.getByRole('button', { name: '保存人员授权' }).click(); await expect(page.getByRole('dialog')).toBeHidden();
});

test('corrupt pending metadata blocks new commands', async ({ page }) => {
  await usersLogin(page); const { dialog } = await fillNewUser(page);
  await page.route('**/api/v1/users?*', route => route.request().method() === 'POST' ? route.abort() : route.continue());
  await dialog.getByRole('button', { name: '新增人员', exact: true }).click();
  await expect(dialog.getByRole('button', { name: '查询原提交结果' })).toBeEnabled();
  await page.evaluate(() => { for (const key of Object.keys(sessionStorage)) if (key.startsWith('acrylic.user-request.v1:')) sessionStorage.setItem(key, '{broken'); });
  await page.reload(); await expect(page.getByText('原人员请求记录无法读取，已暂停新操作。请保留本标签页并联系管理员核对。')).toBeVisible();
  await expect(page.getByRole('button', { name: '新增人员', exact: true })).toBeDisabled();
});

test('storage failure prevents sending a command', async ({ page }) => {
  await usersLogin(page); const { dialog } = await fillNewUser(page); let posts = 0;
  page.on('request', request => { if (request.method() === 'POST' && request.url().includes('/users')) posts++; });
  await page.evaluate(() => { const set = Storage.prototype.setItem; Storage.prototype.setItem = function(key, value) {
    if (key.startsWith('acrylic.user-request.v1:')) throw new DOMException('full', 'QuotaExceededError'); set.call(this, key, value);
  }; });
  await dialog.getByRole('button', { name: '新增人员', exact: true }).click();
  await expect(dialog.getByText('无法保存原请求，请恢复本标签页存储后再提交。请求尚未发送。')).toBeVisible(); expect(posts).toBe(0);
});
