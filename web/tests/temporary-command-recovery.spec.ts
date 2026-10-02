import { test, expect } from '@playwright/test';
import { browserRecovery, openCommand, sendCommand, lostResponse } from './temporary-command-browser-fixture';

test.skip(process.env.TEMP_WEB_BROWSER_VERIFY !== 'true', 'Explicit isolated browser boundary verification required');
for (const kind of ['LINK', 'NORMAL_CONFIRM'] as const) {
  test(`${kind}: reload preserves exact POST bytes and original epoch`, async ({ context, page }, info) => {
    const f = await browserRecovery(context, kind); await page.goto(f.url);
    await openCommand(page); await sendCommand(page); await lostResponse(page);
    const stored = await page.evaluate(() => Object.values(localStorage));
    expect(stored).toHaveLength(1); expect(JSON.parse(stored[0]).json).toBe(f.posts[0].json);
    f.controls.epoch = '2'; f.controls.success = true; f.data.expected.decisionVersion = '9';
    await page.reload(); await openCommand(page, 'Changed browser reason');
    await expect(page.getByRole('dialog').getByRole('textbox')).toHaveValue('Original browser reason');
    await expect(page.getByRole('dialog').getByRole('textbox')).toBeDisabled();
    await sendCommand(page); await expect(page.getByRole('dialog')).toBeHidden();
    expect(f.posts).toHaveLength(2); expect(f.posts[1]).toEqual(f.posts[0]);
    expect(f.posts[1].epoch).toBe('1'); await expect(page.getByText('已记录原回执', { exact: false })).toBeVisible();
    await page.screenshot({ path: info.outputPath('retained-original-receipt.png'), fullPage: true });
    await info.attach('boundary-posts', { body: JSON.stringify(f.posts, null, 2), contentType: 'application/json' });
  });
  test(`${kind}: localStorage failure blocks the actual POST`, async ({ context, page }) => {
    const f = await browserRecovery(context, kind);
    await page.addInitScript(() => { Storage.prototype.setItem = () => { throw new Error('Synthetic quota denied'); }; });
    await page.goto(f.url); await openCommand(page); await sendCommand(page);
    await expect(page.locator('.command-action > .el-alert')).toContainText('无法保存原请求');
    expect(f.posts).toHaveLength(0);
  });
  test(`${kind}: native cross-page locks preserve one original command`, async ({ context, page }) => {
    const f = await browserRecovery(context, kind); const other = await context.newPage();
    await page.goto(f.url); await other.goto(f.url);
    await openCommand(page, 'First reason'); await openCommand(other, 'Other page reason');
    await Promise.all([sendCommand(page), sendCommand(other)]);
    await lostResponse(page); await lostResponse(other);
    expect(f.posts).toHaveLength(2); expect(f.posts[1]).toEqual(f.posts[0]);
    expect(await page.evaluate(() => localStorage.length)).toBe(1);
  });
  test(`${kind}: completed receipt survives reload and requires explicit new intent`, async ({ context, page }) => {
    const f = await browserRecovery(context, kind); f.controls.success = true;
    await page.goto(f.url); await openCommand(page); await sendCommand(page); await expect(page.getByRole('dialog')).toBeHidden();
    await page.reload(); await openCommand(page); await sendCommand(page); await expect(page.getByRole('dialog')).toBeHidden();
    expect(f.posts).toHaveLength(1);
    await page.getByRole('button', { name: '准备新的决定' }).click();
    await expect.poll(() => page.evaluate(() => localStorage.length)).toBe(0);
    await openCommand(page, 'New reviewed reason'); await sendCommand(page); await expect(page.getByRole('dialog')).toBeHidden();
    expect(f.posts).toHaveLength(2);
    expect(JSON.parse(f.posts[1].json).commandId).not.toBe(JSON.parse(f.posts[0].json).commandId);
  });
}

test('LINK: identity change during actual CSRF fetch blocks POST', async ({ context, page }) => {
  const f = await browserRecovery(context, 'LINK'); await page.goto(f.url);
  f.controls.beforeCsrf = () => page.evaluate(async () => {
    const { useSessionStore } = await import('/src/session.ts');
    useSessionStore().identity.id = crypto.randomUUID();
  });
  await openCommand(page); await sendCommand(page);
  await expect(page.locator('.command-action > .el-alert')).toContainText('登录身份或提交范围已变化');
  expect(f.posts).toHaveLength(0); expect(await page.evaluate(() => localStorage.length)).toBe(1);
});

test('LINK: receipt persistence failure retains the unknown original through reload', async ({ context, page }) => {
  const f = await browserRecovery(context, 'LINK'); f.controls.success = true;
  await page.goto(f.url);
  await page.evaluate(() => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function(key, value) {
      if (JSON.parse(value).receipt) throw new Error('Synthetic receipt quota denied');
      return original.call(this, key, value);
    };
  });
  await openCommand(page); await sendCommand(page);
  await expect(page.locator('.command-action > .el-alert')).toContainText('无法保存原请求或回执');
  await page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).click();
  await page.getByRole('button', { name: '准备新的决定' }).click();
  await expect(page.locator('.command-action > .el-alert')).toContainText('原操作结果尚未核对');
  await page.reload(); await openCommand(page); await sendCommand(page); await expect(page.getByRole('dialog')).toBeHidden();
  expect(f.posts).toHaveLength(2); expect(f.posts[1]).toEqual(f.posts[0]);
});
