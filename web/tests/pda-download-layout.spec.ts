import { test, expect } from '@playwright/test';
import jsQR from 'jsqr';
import { PNG } from 'pngjs';
import { mockRelease, releaseFixture } from './pda-download-fixture';

test('an independent QR decoder reads only the permanent HTTPS page address', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await mockRelease(page); await page.goto('/pda-download?token=must-not-be-shared');
  const qr = page.getByRole('img', { name: 'PDA 应用固定下载页二维码' });
  await expect(qr).toBeVisible(); const png = PNG.sync.read(await qr.screenshot());
  const decoded = jsQR(new Uint8ClampedArray(png.data), png.width, png.height);
  expect(decoded?.data).toBe('https://127.0.0.1:5189/pda-download');
  await page.getByRole('button', { name: '复制下载页地址' }).click();
  await expect(page.getByRole('status')).toContainText('下载页地址已复制');
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(decoded?.data);
});

for (const screen of [
  { label: 'desktop', width: 1440, height: 900, font: 14 },
  { label: 'narrow', width: 320, height: 533, font: 14 },
  { label: 'low-height', width: 320, height: 400, font: 14 },
  { label: 'large-font', width: 320, height: 533, font: 28 },
]) {
  test(`download content stays usable at ${screen.label}`, async ({ page }) => {
    await page.setViewportSize(screen);
    await mockRelease(page, releaseFixture({ versionName: '长版本名称用于检查窄屏换行'.repeat(5) }));
    await page.goto('/pda-download');
    await page.evaluate(font => document.documentElement.style.fontSize = `${font}px`, screen.font);
    const button = page.getByRole('button', { name: '下载安装包', exact: true });
    await expect(button).toBeVisible(); await button.scrollIntoViewIfNeeded();
    expect((await button.boundingBox())?.height).toBeGreaterThanOrEqual(48);
    expect(await button.evaluate(element => parseFloat(getComputedStyle(element).fontSize))).toBeGreaterThanOrEqual(screen.font);
    await page.getByRole('heading', { name: '首次安装', exact: true }).scrollIntoViewIfNeeded();
    await expect(page.getByText('使用已授权的人员账号登录，再到设置中检查应用更新。')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: `../.local/pda-download-${screen.label}.png`, fullPage: true, animations: 'disabled' });
  });
}
