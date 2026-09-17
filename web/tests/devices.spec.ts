import { expect, test } from '@playwright/test';
import { changeDevice, deviceFixture, freshDevicePublicKey, loginDevices, recoverDeviceRequest, registration, verifyDeviceIdentity } from './device-fixture';

test('registration and status recover original requests after lost responses and page reload', async ({ page }) => {
  const publicKey = freshDevicePublicKey(); await loginDevices(page);
  const seen = new Map<string, { body: string | null; key: string | undefined; epoch: string | undefined }>();
  await page.route('**/api/v1/devices**', async route => {
    if (route.request().method() !== 'POST') { await route.continue(); return; }
    const request = route.request(); const identity = request.headers()['idempotency-key'] ?? '';
    const value = { body: request.postData(), key: identity, epoch: request.headers()['x-server-recovery-epoch'] };
    if (seen.has(identity)) { expect(value).toEqual(seen.get(identity)); await route.continue(); return; }
    seen.set(identity, value); expect((await route.fetch()).status()).toBe(200); await route.abort('failed');
  });
  const code = await registration(page, publicKey);
  await page.getByRole('button', { name: '确认登记 PDA 设备', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('原请求待核验');
  await expect(page.getByLabel('设备编号', { exact: true })).toBeDisabled();
  await recoverDeviceRequest(page); await verifyDeviceIdentity(page, code, publicKey);
  await changeDevice(page, code, false); await changeDevice(page, code, true); expect(seen.size).toBe(3);
  await page.screenshot({ path: '../.local/web-devices-managed.png', animations: 'disabled' });
});

test('warehouse pagination and invalid public key leave current identities intact', async ({ page }) => {
  await loginDevices(page); await expect(page.locator('.el-table__body-wrapper tbody tr')).toHaveCount(50);
  await page.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(page.getByRole('button', { name: '下一页', exact: true })).toBeDisabled();
  await expect(page.locator('.el-table__body-wrapper tbody tr')).not.toHaveCount(0);
  await page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '当前仓库', exact: true }) }).click();
  await page.getByRole('option', { name: '设备测试仓B', exact: true }).click();
  await expect(page.getByText('本仓库尚无已登记设备', { exact: true })).toBeVisible();
  await registration(page, 'AAAA'); await page.getByRole('button', { name: '确认登记 PDA 设备', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('设备公钥必须是P-256');
  await expect(page.getByLabel('本机登记公钥', { exact: true })).toBeEnabled();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.reload(); await expect(page.getByRole('heading', { name: '有一项设备请求待核验' })).toHaveCount(0);
});

test('viewer has no management entry and direct device page sends no device request', async ({ page }) => {
  let requests = 0; page.on('request', r => { if (new URL(r.url()).pathname.startsWith('/api/v1/devices')) requests++; });
  await loginDevices(page, true); await expect(page.getByText('没有此页面的访问权限', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'PDA 设备', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '高级公钥登记', exact: true })).toHaveCount(0); expect(requests).toBe(0);
});

test('unavailable request storage blocks sending a new device mutation', async ({ page }) => {
  await loginDevices(page); let writes = 0;
  page.on('request', r => { if (r.method() === 'POST' && new URL(r.url()).pathname === '/api/v1/devices') writes++; });
  await registration(page, deviceFixture().publicKey);
  await page.evaluate(() => { Storage.prototype.setItem = () => { throw new DOMException('Full', 'QuotaExceededError'); }; });
  await page.getByRole('button', { name: '确认登记 PDA 设备', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('请求尚未发送'); expect(writes).toBe(0);
});

test('mismatched receipt preserves original request and recovery epoch across reload', async ({ page }) => {
  await loginDevices(page); let originalBody: string | null = null; let key: string | undefined; let epoch: string | undefined;
  let changedReceipt = false;
  await page.route('**/api/v1/devices**', async route => {
    const r = route.request(); const response = await route.fetch(); const body = await response.text();
    if (r.method() === 'GET' && changedReceipt) {
      await route.fulfill({ response, body: body.replace('"serverRecoveryEpoch":"1"', '"serverRecoveryEpoch":"999"') }); return;
    }
    if (r.method() !== 'POST') { await route.fulfill({ response }); return; }
    if (changedReceipt) {
      expect(r.postData()).toBe(originalBody); expect(r.headers()['idempotency-key']).toBe(key);
      expect(r.headers()['x-server-recovery-epoch']).toBe(epoch); await route.fulfill({ response }); return;
    }
    originalBody = r.postData(); key = r.headers()['idempotency-key']; epoch = r.headers()['x-server-recovery-epoch'];
    expect(response.status()).toBe(200); changedReceipt = true;
    const damaged = body.replace('"status":"ACTIVE"', '"status":"DISABLED"'); expect(damaged).not.toBe(body);
    await route.fulfill({ response, body: damaged });
  });
  const code = await registration(page, freshDevicePublicKey()); await page.getByRole('button', { name: '确认登记 PDA 设备', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('服务响应异常'); await page.reload();
  await page.getByRole('button', { name: '恢复原设备请求', exact: true }).click();
  await page.getByRole('button', { name: '重试原设备请求', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden(); await expect(page.getByRole('row').filter({ hasText: code })).toContainText('已启用');
});
