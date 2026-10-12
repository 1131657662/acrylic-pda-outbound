import { test, expect } from '@playwright/test';
import { writeFileSync } from 'node:fs';
import { expireInFlight, loseFirstResponses, approveAfterLoss, revokeAfterLoss } from './rescue-scenarios';
import { rescueInput, rescueLogin, readRescue, permanentRescue } from './rescue-fixture';

test.skip(!process.env.RESCUE_WEB_INPUT, 'Requires fresh native Keystore request and isolated server');
test.afterEach(async ({ page }, info) => {
  const result = process.env.RESCUE_WEB_RESULT; if (!result) return;
  const storage = await page.evaluate(() => Object.fromEntries(Object.keys(localStorage).filter(key => key.startsWith('acrylic.rescue.')).map(key => [key, localStorage.getItem(key)])));
  writeFileSync(`${result}.${info.testId}.storage.json`, JSON.stringify(storage));
});
test('storage failure and a past timestamp send no approval', async ({ page }) => {
  const f = rescueInput(); await rescueLogin(page, f.supervisor); await readRescue(page); let posts = 0;
  page.on('request', request => { if (request.method() === 'POST' && request.url().includes('/approve?')) posts++; });
  await page.getByLabel('救援到期时间', { exact: true }).fill('2000-01-01T00:00:00Z');
  await page.getByLabel('救援审批或撤销理由', { exact: true }).fill('Isolated past time');
  await page.getByRole('button', { name: '核准只读救援', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('到期时间须晚于'); expect(posts).toBe(0);
  await permanentRescue(page);
  await page.evaluate(() => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function (name, value) { if (name.startsWith('acrylic.rescue.')) throw new Error('Isolated storage full'); original.call(this, name, value); };
  });
  await page.getByRole('button', { name: '核准只读救援', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Isolated storage full'); expect(posts).toBe(0);
});
test('native request approval and revoke retain exact commands through loss and reload', async ({ page }) => {
  test.setTimeout(60000); const f = rescueInput(); await rescueLogin(page, f.supervisor); await readRescue(page);
  const expiredBodies = await expireInFlight(page); const seen = await loseFirstResponses(page);
  const authorized = await approveAfterLoss(page); expect(authorized.status).toBe('AUTHORIZED');
  const revoked = await revokeAfterLoss(page); expect(revoked.authorization).toEqual(authorized.authorization);
  expect(revoked.revocation?.grantId).toBe(authorized.authorization?.grantId); expect(seen.size).toBe(2);
  const result = process.env.RESCUE_WEB_RESULT; if (!result) throw new Error('Evidence path required');
  writeFileSync(result, JSON.stringify({ nativeKeystoreRequest: true, expiredBodies, commands: [...seen.values()], authorized, revoked, reloads: 3 }));
});
test('self approval and cross warehouse material are denied', async ({ page }) => {
  const f = rescueInput(); await rescueLogin(page, f.original); await readRescue(page);
  await expect(page.getByText('原操作员不能核准自己的跨人救援申请。', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '核准只读救援', exact: true })).toBeHidden();
  await page.context().clearCookies(); await rescueLogin(page, f.supervisor);
  await readRescue(page, f.otherWarehouseId); await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByRole('heading', { name: '原申请与签名材料', exact: true })).toBeHidden();
});
test('unprivileged direct page has no rescue command or material', async ({ page }) => {
  const f = rescueInput(); await rescueLogin(page, f.low); await page.goto(`/rescue?request=${f.requestId}`);
  await expect(page.getByText('没有此页面的访问权限', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '读取原救援申请', exact: true })).toBeHidden();
  await expect(page.getByRole('button', { name: '核准只读救援', exact: true })).toBeHidden();
  const response = await page.request.get(`/api/v1/rescue-requests/${f.requestId}?warehouseId=${f.warehouseId}`);
  expect(response.status()).toBe(403);
});
