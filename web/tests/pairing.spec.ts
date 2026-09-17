import { expect, test } from '@playwright/test';
import { PairingSimulator, pairingLogin, reviewPairing } from './pairing-fixture';

test('short code approval survives lost response and reload before original PDA confirms and logs in', async ({ page, request }) => {
  const pda = new PairingSimulator(request); const application = await pda.call('CREATE'); await pairingLogin(page);
  let original: { body: string | null; key: string | undefined; epoch: string | undefined } | null = null;
  await page.route('**/api/v1/device-pairings/*/approval?*', async route => {
    const r = route.request(); const observed = { body: r.postData(), key: r.headers()['idempotency-key'], epoch: r.headers()['x-server-recovery-epoch'] };
    if (original) { expect(observed).toEqual(original); await route.continue(); return; }
    original = observed; expect((await route.fetch()).status()).toBe(200); await route.abort('failed');
  });
  const code = await reviewPairing(page, application.shortCode);
  await page.getByRole('button', { name: '发送给原PDA确认', exact: true }).click();
  await expect(page.getByText('连接失败，请检查网络后重试。原提交不会自动重复发送。', { exact: true })).toBeVisible();
  await expect(page.getByText('原确认请求待核对，刷新本标签页后可按原内容恢复。', { exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: '恢复原配对确认', exact: true }).click();
  await expect(page.getByText('等待原PDA核对并确认', { exact: true })).toBeVisible();
  const pending = await pda.call('STATUS'); expect(pending.deviceId).toBeNull(); expect(pending.approval?.deviceCode).toBe(code);
  await expect(page.getByRole('row').filter({ hasText: code })).toHaveCount(0);
  pda.payload.approvalSha256 = pending.approvalSha256 ?? ''; const completed = await pda.call('CONFIRM');
  expect(completed.deviceId).not.toBeNull(); await pda.login(completed.deviceId!);
  await page.getByRole('button', { name: '核对绑定结果', exact: true }).click();
  await expect(page.getByText('原PDA已确认，登记完成', { exact: true })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: code })).toContainText('已启用');
  await page.screenshot({ path: '../.local/web-pairing-completed.png', fullPage: true });
});

test('storage failure prevents sending approval and does not create an enabled device', async ({ page, request }) => {
  const pda = new PairingSimulator(request); const application = await pda.call('CREATE'); await pairingLogin(page);
  await reviewPairing(page, application.shortCode); let writes = 0;
  page.on('request', r => { if (r.method() === 'POST' && r.url().includes('/device-pairings/')) writes++; });
  await page.evaluate(() => { Storage.prototype.setItem = () => { throw new DOMException('Full', 'QuotaExceededError'); }; });
  await page.getByRole('button', { name: '发送给原PDA确认', exact: true }).click();
  await expect(page.getByText('无法保存原请求，请允许本标签页存储后重试；请求尚未发送。', { exact: true })).toBeVisible();
  expect(writes).toBe(0); expect((await pda.call('STATUS')).status).toBe('WAITING'); await pda.call('CANCEL');
});

test('mismatched approval receipt stays recoverable and retains original identity', async ({ page, request }) => {
  const pda = new PairingSimulator(request); const application = await pda.call('CREATE'); await pairingLogin(page);
  let damaged = false;
  await page.route('**/api/v1/device-pairings/*/approval?*', async route => {
    const response = await route.fetch();
    if (damaged) { await route.fulfill({ response }); return; }
    damaged = true; const body = (await response.text()).replace('北侧装车 PDA', '另一台 PDA'); await route.fulfill({ response, body });
  });
  const code = await reviewPairing(page, application.shortCode); await page.getByRole('button', { name: '发送给原PDA确认', exact: true }).click();
  await expect(page.getByText('服务响应异常，请稍后重试。', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '恢复原配对确认', exact: true })).toBeEnabled(); await page.reload();
  await page.getByRole('button', { name: '恢复原配对确认', exact: true }).click();
  await expect(page.getByText('等待原PDA核对并确认', { exact: true })).toBeVisible();
  expect((await pda.call('STATUS')).approval?.deviceCode).toBe(code); await pda.call('CANCEL');
  await page.getByRole('button', { name: '核对绑定结果', exact: true }).click();
  await expect(page.getByText('申请已取消', { exact: true })).toBeVisible();
});
