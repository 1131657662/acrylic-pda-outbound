import { test, expect } from '@playwright/test';
import { writeFileSync } from 'node:fs';
import { exclusionFixture, exclusionLogin, openExclusion, fillExclusion } from './temporary-exclusion-fixture';

test.skip(!process.env.EXCLUSION_WEB_INPUT, 'Requires fresh isolated HTTP/MySQL fixture');
test('storage failure sends no candidate exclusion', async ({ page }) => {
  const f = exclusionFixture(); await exclusionLogin(page); await openExclusion(page, f.cancelledId);
  await fillExclusion(page); let posts = 0;
  page.on('request', request => { if (request.method() === 'POST' && request.url().endsWith('/temporary-commands')) posts++; });
  await page.evaluate(() => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
      if (key.includes('temporary-command')) throw new Error('Isolated exclusion storage failure');
      original.call(this, key, value);
    };
  });
  await page.getByRole('button', { name: '确认该项排除', exact: true }).click();
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('无法保存原请求或回执');
  expect(posts).toBe(0);
});
test('loss and reload preserve original candidate before explicit next-item reset', async ({ page }) => {
  test.setTimeout(60000); const f = exclusionFixture(); const commands: string[] = []; const originals: unknown[] = [];
  await exclusionLogin(page); await openExclusion(page, f.cancelledId); await fillExclusion(page);
  await page.route('**/api/v1/temporary-commands', async route => {
    commands.push(route.request().postData() ?? ''); const response = await route.fetch();
    const responseBody: unknown = await response.json();
    const out = process.env.EXCLUSION_WEB_RESULT;
    if (out) writeFileSync(`${out}.response-${commands.length}.json`, JSON.stringify({ command: commands.at(-1), status: response.status(), responseBody }));
    expect(response.status(), JSON.stringify(responseBody)).toBe(200); originals.push(responseBody);
    if (commands.length === 1) await route.abort('failed'); else await route.fulfill({ response });
  });
  await page.getByRole('button', { name: '确认该项排除', exact: true }).click();
  await expect(page.getByRole('dialog').getByRole('alert')).toBeVisible();
  await expect(page.getByRole('dialog').getByText('结果尚未核对', { exact: false })).toBeVisible();
  await page.reload(); await openExclusion(page, f.unrelatedId);
  await expect(page.getByRole('dialog').getByText(`原候选 ${f.cancelledId}`, { exact: false })).toBeVisible();
  await page.getByRole('button', { name: '重试原排除请求', exact: true }).click();
  await expect(page.getByText('排除原回执已按原键保存', { exact: false })).toBeVisible();
  expect(commands[1]).toBe(commands[0]); expect(originals[1]).toEqual(originals[0]);
  await page.getByRole('button', { name: '核对下一条／新的集合', exact: true }).click();
  await fillExclusion(page, true); await page.getByRole('button', { name: '确认该项排除', exact: true }).click();
  await expect(page.getByText('排除原回执已按原键保存', { exact: false })).toBeVisible();
  expect(JSON.parse(commands[2] ?? '{}').exclusion.orderId).toBe(f.unrelatedId);
  expect(JSON.parse(commands[2] ?? '{}').commandId).not.toBe(JSON.parse(commands[0] ?? '{}').commandId);
  const out = process.env.EXCLUSION_WEB_RESULT; if (!out) throw new Error('Evidence output required');
  writeFileSync(out, JSON.stringify({ fixture: 'synthetic people', commands, originals }));
  await page.screenshot({ path: `${out}.png`, fullPage: true });
});
test('original operator with permission cannot approve their own exclusion', async ({ page }) => {
  const f = exclusionFixture(); await exclusionLogin(page, true); await openExclusion(page, f.cancelledId);
  await fillExclusion(page);
  const rejected = page.waitForResponse(response => response.url().endsWith('/temporary-commands') && response.request().method() === 'POST');
  await page.getByRole('button', { name: '确认该项排除', exact: true }).click();
  expect((await rejected).status()).toBe(403);
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('候选排除须由另一有权主管逐项核对');
});
