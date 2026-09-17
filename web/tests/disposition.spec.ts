import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { z } from 'zod';

for (const action of ['RELEASE', 'QUARANTINE']) {
  test(`physical ${action} evidence survives a lost response and reload before continuation`, async ({ page }) => {
    const fixtures = z.array(z.object({ action: z.string(), username: z.string(), password: z.string(), orderId: z.string().uuid(), barcode: z.string() }))
      .parse(JSON.parse(readFileSync(resolve('../server/build/disposition-browser-fixture.json'), 'utf8')));
    const f = fixtures.find(x => x.action === action); if (!f) throw new Error('Missing disposition fixture');
    await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(f.username); await page.getByLabel('密码', { exact: true }).fill(f.password);
    await page.getByRole('button', { name: '登录', exact: true }).click();
    await expect(page.getByRole('heading', { name: '装车订单', exact: true })).toBeVisible(); await page.goto(`/orders/${f.orderId}?tab=exceptions`);
    const choice = page.getByRole('combobox', { name: `处置 ${f.barcode}`, exact: true });
    await expect(choice).toBeEnabled(); await page.locator('.el-select').filter({ has: choice }).click();
    await page.getByRole('option', { name: action === 'RELEASE' ? '卸货释放' : '卸货隔离', exact: true }).click();
    await expect(page.getByRole('button', { name: '申请恢复装车', exact: true })).toBeDisabled();
    await page.getByLabel(`实物凭据 ${f.barcode}`, { exact: true }).fill('已逐包卸至 D-12，双人核验凭据 WEB-D-13');
    await page.getByRole('button', { name: '主管批准实物处置凭据', exact: true }).click();
    await page.getByText('已现场逐包核验，确认实物移出原车且位置与凭据一致', { exact: true }).click();
    let previous: { body: string | null; key: string | undefined; epoch: string | undefined } | null = null;
    await page.route('**/disposition-evidence?*', async route => {
      if (route.request().method() !== 'POST') { await route.continue(); return; }
      const value = { body: route.request().postData(), key: route.request().headers()['idempotency-key'], epoch: route.request().headers()['x-server-recovery-epoch'] };
      if (previous) { expect(value).toEqual(previous); await route.continue(); return; }
      previous = value; expect((await route.fetch()).status()).toBe(200); await route.abort('failed');
    });
    await page.getByRole('button', { name: '确认批准实物处置', exact: true }).click();
    await expect(page.getByRole('dialog')).toContainText('结果未确认'); await page.reload();
    await expect(page.getByRole('dialog')).toContainText('结果未确认');
    await page.getByRole('button', { name: '重试原请求', exact: true }).click(); await expect(page.getByRole('dialog')).toBeHidden();
    await expect(page.getByText(action === 'RELEASE' ? '已核验卸货' : '已核验隔离', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: '申请恢复装车', exact: true }).click();
    await expect(page.getByRole('dialog')).toContainText('批准减少 1 包，继续保留 0 包');
    await page.getByLabel('恢复原因', { exact: true }).fill('按已批准实物处置减少，继续扫描其他计划产品');
    await page.getByText('已逐包核对保留清单及卸货、隔离凭据', { exact: true }).click();
    await page.getByRole('button', { name: '确认恢复装车', exact: true }).click();
    await page.getByRole('button', { name: '主管核验并批准恢复', exact: true }).click();
    await page.getByLabel('恢复装车核验凭据', { exact: true }).fill('原车减少清单与独立实物凭据一致');
    await page.getByText('已逐包核对保留清单及卸货、隔离凭据', { exact: true }).click();
    await page.getByRole('button', { name: '确认恢复装车', exact: true }).click();
    await expect(page.getByText('主管已批准，待原 PDA 核验', { exact: true })).toBeVisible();
    await page.screenshot({ path: `../.local/web-disposition-${action.toLowerCase()}.png`, fullPage: true });
  });
}
