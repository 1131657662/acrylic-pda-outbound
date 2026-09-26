import { readFileSync } from 'node:fs';
import { z } from 'zod';
import { test, expect } from '@playwright/test';

const path = process.env.TEMP_EXPORT_PRIVATE;
const schema = z.array(z.object({ username: z.string(), password: z.string(), warehouse: z.string().uuid(), order: z.string().uuid() }));
test.skip(!path, 'Requires the protected TEMP export MySQL/HTTP fixture');

test('normal and short TEMP originals are accessible from order and summary pages', async ({ browser }, info) => {
  if (!path) throw new Error('Protected fixture required');
  const fixtures = schema.parse(JSON.parse(readFileSync(path, 'utf8')));
  for (const f of fixtures) {
    const context = await browser.newContext({ baseURL: 'http://127.0.0.1:19436' }); const page = await context.newPage();
    await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(f.username);
    await page.getByLabel('密码', { exact: true }).fill(f.password);
    await page.getByRole('button', { name: '登录', exact: true }).click(); await expect(page.locator('.workspace')).toBeVisible();
    await page.goto(`/orders/${f.order}?warehouse=${f.warehouse}&tab=exports`);
    await expect(page.getByRole('tab', { name: '原确认文件', exact: true })).toBeVisible();
    const panel = page.getByRole('region', { name: '原确认文件', exact: true });
    await expect(panel.getByRole('button', { name: '下载', exact: true }).first()).toBeVisible();
    const download = page.waitForEvent('download'); await panel.getByRole('button', { name: '下载', exact: true }).first().click();
    expect((await download).suggestedFilename()).toMatch(/\.xlsx$/);
    await page.screenshot({ path: info.outputPath(`${f.order}-originals.png`), fullPage: true });
    await page.goto(`/exports?warehouse=${f.warehouse}`);
    const summary = page.getByRole('region', { name: '跨订单原确认汇总' });
    await expect(summary).toContainText('已核验临时来源'); await summary.getByRole('button', { name: '查询汇总' }).click();
    await expect(summary).toContainText('1 单'); await expect(summary).toContainText('2 包');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await context.close();
  }
});
