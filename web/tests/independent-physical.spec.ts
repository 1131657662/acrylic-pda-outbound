import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
interface Input { username: string; password: string; relayId: string; warehouseId: string; evidence: string }
function input(): Input {
  const path = process.env.INDEPENDENT_PHYSICAL_WEB_INPUT;
  if (!path) throw new Error('Requires isolated real HTTP physical environment');
  return JSON.parse(readFileSync(path, 'utf8')) as Input;
}
async function login(page: Page): Promise<void> {
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url()); url.port = '19434';
    const response = await route.fetch({ url: url.toString(), maxRedirects: 0 }); await route.fulfill({ response });
  });
  const f = input(); await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill(f.username); await page.getByLabel('密码', { exact: true }).fill(f.password);
  await page.getByRole('button', { name: '登录', exact: true }).click(); await expect(page).not.toHaveURL(/\/login/);
  await page.goto(`/independent-reviews?relay=${f.relayId}&warehouse=${f.warehouseId}`);
  await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click();
}
test.afterEach(async ({ page }) => { await page.unrouteAll({ behavior: 'wait' }); });
test('supervisor uploads actual file, records all packages and recovers original key after lost response at 390px', async ({ page }) => {
  const f = input(); await login(page); await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: '载入全部有效包并逐包检查', exact: true }).click();
  await expect(page.getByLabel('实物检查责任区')).toBeVisible();
  await page.getByLabel('实物检查责任区').fill('隔离合成车厢'); await page.getByLabel('实物检查说明').fill('浏览器合成逐包实物观察，不冒充现场');
  for (const group of await page.locator('.physical-package').all()) {
    await group.getByText('已装载', { exact: true }).click();
    await expect(group.getByRole('radio', { name: '已装载', exact: true })).toBeChecked();
  }
  const filename = `${f.evidence}/web-physical-original.bin`; writeFileSync(filename, Buffer.from('browser synthetic physical evidence\x00\xff', 'binary'));
  await page.getByLabel('上传实物附件', { exact: true }).setInputFiles(filename);
  await expect(page.getByText(/web-physical-original.bin.*已保存原字节/)).toBeVisible();
  const keys: string[] = []; let drop = true;
  await page.route('**/physical/checks?**', async route => {
    const key = route.request().headers()['idempotency-key']; if (key) keys.push(key);
    const url = new URL(route.request().url()); url.port = '19434';
    const response = await route.fetch({ url: url.toString(), maxRedirects: 0 });
    if (drop) { drop = false; expect(response.ok()).toBe(true); await route.abort('failed'); }
    else await route.fulfill({ response });
  });
  await page.getByRole('button', { name: '保存实物观察并登记', exact: true }).click();
  await expect(page.getByText('原登记结果未明。已保存原请求，刷新后仍按原键恢复。')).toBeVisible();
  await page.screenshot({ path: `${f.evidence}/390-pending.png`, fullPage: true });
  await page.reload(); await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click();
  await page.getByRole('button', { name: '按原键恢复登记结果', exact: true }).click();
  await expect(page.getByText(/实物观察已登记，仍待对账；未关联、未记账。回执/)).toBeVisible();
  expect(keys.length).toBe(2); expect(keys[0]).toBe(keys[1]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: `${f.evidence}/390-recovered.png`, fullPage: true });
  const saved = await page.evaluate(() => Object.entries(localStorage).find(([key]) => key.startsWith('acrylic.independent-physical.v1:'))?.[1]);
  if (!saved) throw new Error('Original command disappeared'); writeFileSync(`${f.evidence}/browser-original-command.json`, saved);
  await page.reload(); await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click();
  await expect(page.getByText(/实物观察已登记，仍待对账；未关联、未记账。回执/)).toBeVisible();
  writeFileSync(`${f.evidence}/browser-key-recovery.json`, JSON.stringify({ keys, syntheticPhysical: true, actualHttp: true, width: 390 }));
});
