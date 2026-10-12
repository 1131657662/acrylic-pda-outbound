import { test, expect, type Page } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { z } from 'zod';

test.skip(!process.env.P05_WEB_INPUT, 'Requires the preserved original native HTTP/MySQL source');
function input() {
  const path = process.env.P05_WEB_INPUT;
  if (!path) throw new Error('Original native input required');
  const account = z.object({ username: z.string(), password: z.string() });
  return z.object({ warehouseId: z.uuid(), submissionId: z.uuid(), operator: account, supervisor: account })
    .parse(JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, '')));
}
async function open(page: Page, original = false): Promise<void> {
  const f = input(); const actor = original ? f.operator : f.supervisor;
  await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(actor.username);
  await page.getByLabel('密码', { exact: true }).fill(actor.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '装车订单', exact: true })).toBeVisible();
  await page.goto(`/temporary-dispositions/${f.submissionId}?warehouse=${f.warehouseId}`);
  await expect(page.getByRole('heading', { name: '临时实物处置', exact: true })).toBeVisible();
}
async function fill(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { name: '逐包实物核验', exact: true })).toBeVisible();
  for (const barcode of ['003B0051601', '571B0051602']) {
    const row = page.getByRole('row').filter({ hasText: barcode });
    await row.getByText('已逐包核实', { exact: true }).click();
    await expect(row.getByRole('checkbox')).toBeChecked();
    await row.locator('.el-select').click();
    const label = barcode === '571B0051602' && process.env.P05_QUARANTINE_SECOND === 'true'
      ? '已隔离，继续阻断使用' : '已卸货，释放本批合法占用';
    await page.getByRole('listbox', { name: `${barcode} 处置`, exact: true })
      .getByRole('option', { name: label, exact: true }).click();
  }
  await page.getByLabel('主管核验理由').fill(process.env.P05_QUARANTINE_SECOND === 'true'
    ? '隔离主管逐包核实一包卸货释放、另一包继续隔离，关闭原取消来源'
    : '隔离主管逐包核实原机两包已卸货，关闭原取消来源');
}
test('storage failure prevents nonzero close POST', async ({ page }) => {
  await open(page); await fill(page); let posts = 0;
  page.on('request', request => { if (request.method() === 'POST' && request.url().endsWith('/temporary-commands')) posts++; });
  await page.evaluate(() => {
    const save = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
      if (key.includes('temporary-command')) throw new Error('Isolated P05 storage failure');
      save.call(this, key, value);
    };
  });
  await page.getByRole('button', { name: '签署处置并关闭原来源', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('无法保存原请求或回执'); expect(posts).toBe(0);
});
test('original native cancellation closes through supervisor page and recovers identical key after lost response', async ({ page }) => {
  test.setTimeout(60000); const commands: string[] = []; const originals: Buffer[] = [];
  const output = process.env.P05_WEB_RESULT; if (!output) throw new Error('Evidence output required');
  await open(page); await fill(page);
  await page.screenshot({ path: `${output}.before.png`, fullPage: true });
  await page.route('**/api/v1/temporary-commands', async route => {
    commands.push(route.request().postData() ?? ''); const response = await route.fetch();
    const bytes = await response.body(); originals.push(bytes);
    writeFileSync(`${output}.command-${commands.length}.json`, commands.at(-1) ?? '');
    writeFileSync(`${output}.response-${commands.length}.json`, bytes);
    expect(response.status(), bytes.toString('utf8')).toBe(200);
    if (commands.length === 1) await route.abort('failed'); else await route.fulfill({ response });
  });
  await page.getByRole('button', { name: '签署处置并关闭原来源', exact: true }).click();
  await expect(page.getByRole('button', { name: '重试原处置请求', exact: true })).toBeVisible();
  await page.reload(); await expect(page.getByRole('heading', { name: '保留的原处置', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '重试原处置请求', exact: true }).click();
  await expect(page.getByText('关闭原回执已按原键保存', { exact: false })).toBeVisible();
  expect(commands[1]).toBe(commands[0]); expect(originals[1]).toEqual(originals[0]);
  writeFileSync(output, JSON.stringify({ source: 'original native staging Keystore', syntheticPeople: true,
    commands, identicalOriginalBytes: originals[1]?.equals(originals[0] ?? Buffer.alloc(0)), localDeletionAllowed: false }));
  await page.screenshot({ path: `${output}.after.png`, fullPage: true });
});
test('original operator cannot approve physical closure even with warehouse permissions', async ({ page }) => {
  await open(page, true);
  await expect(page.getByRole('alert')).toContainText('实物关闭须由另一有权主管逐包核验');
  await expect(page.getByRole('button', { name: '签署处置并关闭原来源', exact: true })).toHaveCount(0);
});

async function retainedPost(page: Page, body: string, status: number): Promise<Buffer> {
  const token = await page.evaluate(async () => (await fetch('/api/v1/auth/csrf')).text());
  const csrf = z.object({ data: z.object({ headerName: z.string(), token: z.string() }) }).parse(JSON.parse(token)).data;
  const response = await page.evaluate(async ({ body, csrf }) => {
    const response = await fetch('/api/v1/temporary-commands', { method: 'POST', body,
      headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token, 'X-Server-Recovery-Epoch': '1' } });
    return { status: response.status, bytes: Array.from(new Uint8Array(await response.arrayBuffer())) };
  }, { body, csrf });
  const bytes = Buffer.from(response.bytes); expect(response.status, bytes.toString('utf8')).toBe(status); return bytes;
}

test('closed original source keeps concurrent replay and rejects changed key content or a new close', async ({ page }) => {
  const output = process.env.P05_WEB_RESULT; if (!output) throw new Error('Evidence output required');
  const original = readFileSync(`${output}.command-1.json`, 'utf8');
  const receipt = readFileSync(`${output}.response-1.json`);
  const command = z.record(z.string(), z.unknown()).parse(JSON.parse(original));
  await open(page);
  const replay = await Promise.all([retainedPost(page, original, 200), retainedPost(page, original, 200)]);
  replay.forEach(bytes => expect(bytes).toEqual(receipt));
  const mismatch = await retainedPost(page, JSON.stringify({ ...command, reason: 'different retained intent' }), 409);
  expect(z.object({ code: z.string() }).parse(JSON.parse(mismatch.toString('utf8'))).code).toBe('IDEMPOTENCY_CONTENT_MISMATCH');
  const stale = await retainedPost(page, JSON.stringify({ ...command, commandId: crypto.randomUUID() }), 409);
  expect(z.object({ code: z.string() }).parse(JSON.parse(stale.toString('utf8'))).code).toBe('COMMAND_CONTEXT_CHANGED');
  writeFileSync(`${output}.postclose.json`, JSON.stringify({ concurrentOriginalBytes: true,
    mismatch: JSON.parse(mismatch.toString('utf8')), stale: JSON.parse(stale.toString('utf8')) }));
});
