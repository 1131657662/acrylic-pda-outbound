import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { releaseFixture, mockRelease, envelope } from './pda-download-fixture';

test('anonymous direct access and reload never invoke protected APIs', async ({ page }) => {
  const apis: string[] = []; page.on('request', request => { if (request.url().includes('/api/')) apis.push(request.url()); });
  await mockRelease(page); await page.goto('/pda-download'); await page.reload();
  await expect(page.getByRole('button', { name: '下载安装包', exact: true })).toBeVisible();
  expect(apis).toEqual([]); await expect(page).toHaveURL(/\/pda-download$/);
});
test('login entry opens the shared page without login', async ({ page }) => {
  await mockRelease(page); await page.goto('/login');
  await page.getByRole('link', { name: '下载 PDA 应用' }).click();
  await expect(page).toHaveURL(/\/pda-download$/);
  await expect(page.getByText('测试版 · 仅用于测试环境')).toBeVisible();
});
test('signed first install does not require an installed source version', async ({ page }) => {
  const fixture = releaseFixture({ minSupportedVersionCode: 100 }); await mockRelease(page, fixture);
  await page.goto('/pda-download');
  const saved = page.waitForEvent('download'); await page.getByRole('button', { name: '下载安装包', exact: true }).click();
  const download = await saved; expect(download.suggestedFilename()).toBe('test-20.apk');
  expect(await readFile((await download.path())!)).toEqual(fixture.apk);
  await expect(page.getByRole('status')).toContainText('系统安装器中确认安装');
});
test('new release refreshes the display before a subsequent download', async ({ page }) => {
  await mockRelease(page); await page.goto('/pda-download');
  await expect(page.getByText('1.0.0-首次安装测试（20）')).toBeVisible();
  const next = releaseFixture({ releaseId: 'test-21', apkPath: 'updates/staging/test-21.apk', versionCode: 21, versionName: '新版本' });
  await mockRelease(page, next); const downloads: string[] = []; page.on('download', item => downloads.push(item.url()));
  await page.getByRole('button', { name: '下载安装包', exact: true }).click();
  await expect(page.getByText('发布信息已更新，请核对当前版本后再次下载。')).toBeVisible();
  await expect(page.getByText('新版本（21）')).toBeVisible(); expect(downloads).toEqual([]);
  const saved = page.waitForEvent('download'); await page.getByRole('button', { name: '下载安装包', exact: true }).click();
  expect((await saved).suggestedFilename()).toBe('test-21.apk');
});
test('suspension before click removes the old download recommendation', async ({ page }) => {
  await mockRelease(page); await page.goto('/pda-download');
  await expect(page.getByRole('button', { name: '下载安装包', exact: true })).toBeVisible();
  await page.route('**/manifest.json', route => route.fulfill({ status: 410 }));
  await page.getByRole('button', { name: '下载安装包', exact: true }).click();
  await expect(page.getByRole('heading', { name: '已暂停分发' })).toBeVisible();
  await expect(page.getByRole('button', { name: '下载安装包', exact: true })).toHaveCount(0);
});
test('suspension during APK transfer prevents handing the file to the browser', async ({ page }) => {
  const fixture = releaseFixture(); await mockRelease(page, fixture);
  await page.route('**/*.apk', async route => {
    await page.route('**/manifest.json', request => request.fulfill({ status: 410 }));
    await route.fulfill({ body: fixture.apk });
  });
  const downloads: string[] = []; page.on('download', item => downloads.push(item.url()));
  await page.goto('/pda-download'); await page.getByRole('button', { name: '下载安装包', exact: true }).click();
  await expect(page.getByRole('heading', { name: '已暂停分发' })).toBeVisible(); expect(downloads).toEqual([]);
});
test('device entry preserves its original cursor and context in the first tab', async ({ page, context }) => {
  await mockRelease(context); const warehouse = '00000000-0000-4000-8000-000000000001';
  await page.route('**/api/v1/me', route => route.fulfill({ json: envelope({ id: warehouse, username: 'synthetic', displayName: '测试人员',
    permissions: ['device:manage'], warehouses: [{ id: warehouse, code: 'W', name: '测试仓库' }] }) }));
  await page.route('**/api/v1/devices?*', route => route.fulfill({ json: envelope({ items: [], nextCursor: new URL(route.request().url()).searchParams.get('beforeId') ? null : warehouse }) }));
  await page.goto(`/devices?warehouse=${warehouse}`); await page.getByRole('button', { name: '下一页' }).click();
  await expect(page.getByText(/第 2 页/)).toBeVisible();
  const popupEvent = page.waitForEvent('popup'); await page.getByRole('link', { name: '下载 PDA 应用' }).click();
  const popup = await popupEvent; await expect(popup.getByRole('button', { name: '下载安装包', exact: true })).toBeVisible();
  expect(await popup.evaluate(() => window.opener === null)).toBe(true); await popup.close();
  await expect(page.getByText(/第 2 页/)).toBeVisible(); await expect(page).toHaveURL(new RegExp(`warehouse=${warehouse}`));
});
