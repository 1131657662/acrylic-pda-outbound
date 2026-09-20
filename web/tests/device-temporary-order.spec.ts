import { expect, test } from '@playwright/test';
import { freshDevicePublicKey, loginDevices, recoverDeviceRequest, registration } from './device-fixture';

test('temporary admission recovers the same request after a lost response and reload', async ({ page }) => {
  await loginDevices(page); const code = await registration(page, freshDevicePublicKey());
  await page.getByRole('button', { name: '确认登记 PDA 设备', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  const row = page.getByRole('row').filter({ hasText: code }); await expect(row).toContainText('已关闭');
  let original: { body: string | null; key: string | undefined; epoch: string | undefined } | null = null;
  await page.route('**/api/v1/devices/*/temporary-order?*', async route => {
    const r = route.request(); const value = { body: r.postData(), key: r.headers()['idempotency-key'], epoch: r.headers()['x-server-recovery-epoch'] };
    if (original) { expect(value).toEqual(original); await route.continue(); return; }
    original = value; expect((await route.fetch()).status()).toBe(200); await route.abort('failed');
  });
  await row.getByRole('button', { name: '开启临时准入', exact: true }).click();
  await expect(page.getByRole('button', { name: '确认开启临时作业准入', exact: true })).toBeDisabled();
  await page.getByLabel('准入调整原因', { exact: true }).fill('预先核对本仓库设备');
  await page.getByText('已核对设备和影响，确认调整临时作业准入', { exact: true }).click();
  await page.getByRole('button', { name: '确认开启临时作业准入', exact: true }).click();
  await recoverDeviceRequest(page); await expect(row).toContainText('已开启');
  await page.unroute('**/api/v1/devices/*/temporary-order?*');
  await row.getByRole('button', { name: '关闭临时准入', exact: true }).click();
  await page.getByLabel('准入调整原因', { exact: true }).fill('结束本次准入');
  await page.getByText('已核对设备和影响，确认调整临时作业准入', { exact: true }).click();
  await page.getByRole('button', { name: '确认关闭临时作业准入', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden(); await expect(row).toContainText('已关闭');
});

test('wrong admission receipt keeps recovery while disabled devices cannot be enabled', async ({ page }) => {
  await loginDevices(page); const code = await registration(page, freshDevicePublicKey());
  await page.getByRole('button', { name: '确认登记 PDA 设备', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden(); const row = page.getByRole('row').filter({ hasText: code });
  await page.route('**/api/v1/devices/*/temporary-order?*', async route => {
    const response = await route.fetch(); expect(response.status()).toBe(200);
    const body = await response.text(); expect(body).toContain('"temporaryOrderEnabled":true');
    await route.fulfill({ response, body: body.replace('"temporaryOrderEnabled":true', '"temporaryOrderEnabled":false') });
  });
  await row.getByRole('button', { name: '开启临时准入', exact: true }).click();
  await page.getByLabel('准入调整原因', { exact: true }).fill('核对回执保护');
  await page.getByText('已核对设备和影响，确认调整临时作业准入', { exact: true }).click();
  await page.getByRole('button', { name: '确认开启临时作业准入', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('服务响应异常');
  await page.unroute('**/api/v1/devices/*/temporary-order?*'); await page.reload();
  await page.getByRole('button', { name: '恢复原设备请求', exact: true }).click();
  await page.getByRole('button', { name: '重试原设备请求', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden(); await expect(row).toContainText('已开启');
  await row.getByRole('button', { name: '停用', exact: true }).click();
  await page.getByLabel('启停原因', { exact: true }).fill('核对停用后仍能关闭准入');
  await page.getByText('已核对设备和影响，确认此次启停', { exact: true }).click();
  await page.getByRole('button', { name: '确认停用 PDA 设备', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden(); await expect(row).toContainText('已停用');
  await row.getByRole('button', { name: '关闭临时准入', exact: true }).click();
  await page.getByLabel('准入调整原因', { exact: true }).fill('停用设备关闭准入');
  await page.getByText('已核对设备和影响，确认调整临时作业准入', { exact: true }).click();
  await page.getByRole('button', { name: '确认关闭临时作业准入', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(row.getByRole('button', { name: '开启临时准入', exact: true })).toBeDisabled();
});

test('pre V29 pending status requests remain recoverable without an admission field', async ({ page }) => {
  await loginDevices(page); const code = await registration(page, freshDevicePublicKey());
  await page.getByRole('button', { name: '确认登记 PDA 设备', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden(); const row = page.getByRole('row').filter({ hasText: code });
  await page.route('**/api/v1/devices/*/status?*', async route => {
    expect((await route.fetch()).status()).toBe(200); await route.abort('failed');
  });
  await row.getByRole('button', { name: '停用', exact: true }).click();
  await page.getByLabel('启停原因', { exact: true }).fill('旧版本遗留请求');
  await page.getByText('已核对设备和影响，确认此次启停', { exact: true }).click();
  await page.getByRole('button', { name: '确认停用 PDA 设备', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('连接失败');
  await page.evaluate(() => {
    const key = Object.keys(sessionStorage).find(k => k.startsWith('acrylic.device-request.v1:'));
    if (!key) throw new Error('Missing pending status request');
    const body: unknown = JSON.parse(sessionStorage.getItem(key) ?? 'null');
    if (!body || typeof body !== 'object' || !('device' in body) || !body.device || typeof body.device !== 'object') throw new Error('Invalid device request');
    Reflect.deleteProperty(body.device, 'temporaryOrderEnabled'); sessionStorage.setItem(key, JSON.stringify(body));
  });
  await page.unroute('**/api/v1/devices/*/status?*'); await recoverDeviceRequest(page);
  await expect(row).toContainText('已停用'); await expect(row).toContainText('已关闭');
});

for (const viewport of [{ width: 1440, height: 900 }, { width: 1024, height: 768 }, { width: 768, height: 640 }]) {
  test(`temporary admission dialog remains operable at ${viewport.width}`, async ({ page }) => {
    await page.setViewportSize(viewport); await loginDevices(page);
    await page.getByRole('button', { name: '开启临时准入', exact: true }).first().click();
    await expect(page.getByRole('dialog')).toContainText('原订单、授权和占用保留');
    await expect(page.getByLabel('准入调整原因', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '确认开启临时作业准入', exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: `../.local/r06a-20260920/dialog-${viewport.width}.png`, animations: 'disabled' });
  });
}
