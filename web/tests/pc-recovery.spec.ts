import { test, expect } from '@playwright/test';
import { pcFixture, pcLogin, pcPath, startPc, workView, pendingCount } from './pc-fixture';

test('committed package with lost response recovers after reload without a second write', async ({ page }) => {
  const f = pcFixture(2); await pcLogin(page, f); await startPc(page, f); let writes = 0;
  await page.route(`**/orders/${f.order}/pc/entries?**`, async route => {
    if (route.request().method() !== 'POST') return route.continue();
    writes++; expect((await route.fetch()).status()).toBe(200); await route.abort('failed');
  });
  await page.getByLabel('完整条码或条码片段', { exact: true }).fill(f.barcodes[0]!);
  await page.getByRole('button', { name: '确认计入本单', exact: true }).click();
  await expect(page.getByRole('button', { name: '恢复原PC请求', exact: true })).toBeVisible(); expect(await pendingCount(page)).toBe(1);
  await page.reload(); await page.getByRole('button', { name: '恢复原PC请求', exact: true }).click();
  await expect.poll(() => pendingCount(page)).toBe(0); expect(writes).toBe(1);
  expect((await workView(page, f)).products[0].actualCount).toBe(1);
  await expect(page.locator('.pc-writer-notice')).toContainText('当前窗口只读');
});

test('wrong receipt and failed lookup retain the original command until matching recovery', async ({ page }) => {
  const f = pcFixture(3); await pcLogin(page, f); await startPc(page, f);
  await page.route(`**/orders/${f.order}/pc/entries?**`, async route => {
    if (route.request().method() !== 'POST') return route.continue();
    const response = await route.fetch(); const body = await response.json(); body.data.orderId = crypto.randomUUID();
    await route.fulfill({ response, json: body });
  });
  await page.getByLabel('完整条码或条码片段', { exact: true }).fill(f.barcodes[0]!);
  await page.getByRole('button', { name: '确认计入本单', exact: true }).click();
  await expect.poll(() => pendingCount(page)).toBe(1);
  await page.route(`**/orders/${f.order}/pc/requests/**`, async route => {
    const response = await route.fetch(); const body = await response.json(); body.code = 'NOT_FOUND'; body.message = '暂时无法查询原回执';
    await route.fulfill({ response, status: 404, json: body });
  });
  await page.getByRole('button', { name: '恢复原PC请求', exact: true }).click();
  await expect(page.getByText(/暂时无法查询原回执/).first()).toBeVisible(); expect(await pendingCount(page)).toBe(1);
  await page.unroute(`**/orders/${f.order}/pc/requests/**`);
  await page.getByRole('button', { name: '恢复原PC请求', exact: true }).click();
  await expect.poll(() => pendingCount(page)).toBe(0); expect((await workView(page, f)).products[0].actualCount).toBe(1);
});

test('browser storage failure prevents the outgoing command', async ({ page }) => {
  const f = pcFixture(7); await pcLogin(page, f); await startPc(page, f); let writes = 0;
  page.on('request', req => { if (req.method() === 'POST' && req.url().includes('/pc/entries')) writes++; });
  await page.evaluate(() => { const original = Storage.prototype.setItem; Storage.prototype.setItem = function (key, value) {
    if (key.startsWith('outbound.pc.request.v1:')) throw new DOMException('test quota', 'QuotaExceededError'); original.call(this, key, value);
  }; });
  await page.getByLabel('完整条码或条码片段', { exact: true }).fill(f.barcodes[0]!);
  await page.getByRole('button', { name: '确认计入本单', exact: true }).click();
  await expect(page.getByText('浏览器无法保存恢复资料，本次请求未发送。请恢复浏览器存储后重试。')).toBeVisible();
  expect(writes).toBe(0); expect((await workView(page, f)).products[0].actualCount).toBe(0);
});

test('query failure pauses selection and expired session keeps the pending package', async ({ page }) => {
  const f = pcFixture(8); await pcLogin(page, f); await startPc(page, f);
  await page.route('**/pc/candidates?**', route => route.abort('failed'));
  await page.getByLabel('完整条码或条码片段', { exact: true }).fill(f.query);
  await expect(page.getByText(/请重新查询后选择/)).toBeVisible(); await expect(page.getByRole('button', { name: '确认计入本单' })).toBeDisabled();
  await page.unroute('**/pc/candidates?**'); await page.getByLabel('完整条码或条码片段', { exact: true }).fill(f.barcodes[0]!);
  await expect(page.getByRole('button', { name: '确认计入本单' })).toBeEnabled();
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json();
  await page.request.post('/api/v1/auth/web/logout', { headers: { [csrf.data.headerName]: csrf.data.token, 'X-Server-Recovery-Epoch': csrf.serverRecoveryEpoch } });
  await page.getByRole('button', { name: '确认计入本单' }).click();
  const login = page.getByRole('dialog', { name: '重新登录', exact: true }); await expect(login).toBeVisible();
  await page.getByLabel('重新登录密码', { exact: true }).fill(f.password); await login.getByRole('button', { name: '重新登录', exact: true }).click();
  await expect(login).toBeHidden(); expect(await pendingCount(page)).toBe(1);
  await page.getByRole('button', { name: '恢复原PC请求', exact: true }).click(); await expect.poll(() => pendingCount(page)).toBe(0);
  expect((await workView(page, f)).products[0].actualCount).toBe(1);
});
