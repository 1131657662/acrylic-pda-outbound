import { test, expect, chromium, type Page } from '@playwright/test';
import { resolve } from 'node:path';
import { writeFileSync } from 'node:fs';
import { usersLogin } from './users-fixture';
import { noPageOverflow } from './ui-fixture';

async function inspectUsers(page: Page, suffix: string): Promise<void> {
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0); await noPageOverflow(page);
  await page.getByRole('navigation', { name: '人员列表分页' }).scrollIntoViewIfNeeded();
  await page.screenshot({ path: `../.local/r05-users-${suffix}.png`, animations: 'disabled' });
  await page.getByRole('heading', { name: '人员与权限', exact: true }).scrollIntoViewIfNeeded();
  await page.screenshot({ path: `../.local/r05-users-top-${suffix}.png`, animations: 'disabled' });
  await page.getByRole('button', { name: '新增人员', exact: true }).click();
  const dialog = page.getByRole('dialog'); await expect(dialog).toBeVisible();
  expect((await dialog.boundingBox())!.width).toBeLessThanOrEqual(await page.evaluate(() => innerWidth));
  await dialog.locator('label').filter({ hasText: /^库存管理员$/ }).click();
  await dialog.locator('summary').click();
  await dialog.getByRole('textbox', { name: '操作原因', exact: true }).fill('核对角色与仓库后的隔离演练');
  await dialog.getByRole('button', { name: '取消', exact: true }).scrollIntoViewIfNeeded();
  await noPageOverflow(page); await page.screenshot({ path: `../.local/r05-user-dialog-${suffix}.png`, animations: 'disabled' });
  await dialog.getByRole('button', { name: '取消', exact: true }).click(); await expect(dialog).toBeHidden();
  await page.getByRole('button', { name: '记录', exact: true }).first().click();
  await expect(page.getByRole('dialog')).toBeVisible(); await expect(page.getByRole('dialog').locator('.el-loading-mask:visible')).toHaveCount(0);
  await expect.poll(async () => { const box = (await page.locator('.el-drawer.open').boundingBox())!; return Math.round(box.x + box.width); }).toBe(await page.evaluate(() => innerWidth));
  await noPageOverflow(page); await page.screenshot({ path: `../.local/r05-user-records-${suffix}.png`, animations: 'disabled' });
  await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toBeHidden();
}

test('users list forms and history at desktop low height and narrow viewport', async ({ page }) => {
  test.setTimeout(90000); await usersLogin(page);
  for (const [width, height] of [[1440, 900], [1280, 500], [390, 700]]) {
    await page.setViewportSize({ width: width!, height: height! }); await inspectUsers(page, `${width}x${height}`);
  }
});

test('users list forms and history at native 200 percent browser zoom', async () => {
  test.setTimeout(90000); const extension = resolve('tests/zoom-extension');
  const context = await chromium.launchPersistentContext('', { channel: 'chromium', headless: true,
    viewport: { width: 1440, height: 900 }, baseURL: process.env.OUTBOUND_WEB_URL ?? 'http://127.0.0.1:5179',
    args: [`--disable-extensions-except=${extension}`, `--load-extension=${extension}`] });
  try {
    const worker = context.serviceWorkers()[0] ?? await context.waitForEvent('serviceworker');
    const page = context.pages()[0] ?? await context.newPage(); await usersLogin(page);
    const before = await page.evaluate(() => ({ width: innerWidth, dpr: devicePixelRatio }));
    const zoom = await worker.evaluate(async () => {
      const api = (globalThis as unknown as { chrome: { tabs: { query(options: { url: string }): Promise<{ id: number }[]>;
        setZoom(id: number, value: number): Promise<void>; getZoom(id: number): Promise<number> } } }).chrome.tabs;
      const id = (await api.query({ url: 'http://127.0.0.1/*' }))[0]!.id;
      await api.setZoom(id, 2); return api.getZoom(id);
    });
    expect(zoom).toBe(2); await expect.poll(() => page.evaluate(() => innerWidth)).toBe(before.width / 2);
    await inspectUsers(page, 'zoom200'); const after = await page.evaluate(() => ({ width: innerWidth, dpr: devicePixelRatio }));
    expect(after.dpr).toBe(before.dpr * 2); writeFileSync('../.local/r05-native-zoom.json', JSON.stringify({ zoom, before, after }, null, 2));
  } finally { await context.close(); }
});
