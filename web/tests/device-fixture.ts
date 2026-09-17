import { expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { generateKeyPairSync } from 'node:crypto';
import { z } from 'zod';

const schema = z.object({ username: z.string(), password: z.string(), viewerUsername: z.string(), warehouseId: z.string().uuid(),
  otherWarehouseId: z.string().uuid(), publicKey: z.string() });
export function deviceFixture(): z.infer<typeof schema> {
  return schema.parse(JSON.parse(readFileSync(resolve('../server/build/device-browser-fixture.json'), 'utf8')));
}
export function freshDevicePublicKey(): string {
  return generateKeyPairSync('ec', { namedCurve: 'prime256v1' }).publicKey.export({ type: 'spki', format: 'der' }).toString('base64');
}
export async function loginDevices(page: Page, viewer = false): Promise<void> {
  const f = deviceFixture();
  await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(viewer ? f.viewerUsername : f.username);
  await page.getByLabel('密码', { exact: true }).fill(f.password); await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '装车订单', exact: true })).toBeVisible();
  await page.goto('/devices');
  if (!viewer) await expect(page.getByRole('heading', { name: '设备测试仓A · 已登记设备', exact: true })).toBeVisible();
}
export async function registration(page: Page, publicKey: string): Promise<string> {
  const code = `PDA-${crypto.randomUUID().slice(0, 8)}`;
  await page.getByRole('button', { name: '高级公钥登记', exact: true }).click();
  await page.getByLabel('设备编号', { exact: true }).fill(code);
  await page.getByLabel('设备名称', { exact: true }).fill('东侧装车 PDA');
  await page.getByLabel('本机登记公钥', { exact: true }).fill(publicKey);
  await expect(page.getByRole('button', { name: '确认登记 PDA 设备', exact: true })).toBeDisabled();
  await page.getByText('已核对当前仓库，并确认公钥来自这台PDA', { exact: true }).click();
  return code;
}
export async function recoverDeviceRequest(page: Page): Promise<void> {
  await expect(page.getByRole('dialog')).toContainText('连接失败');
  await page.reload(); await expect(page.getByRole('heading', { name: '有一项设备请求待核验', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '高级公钥登记', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: '恢复原设备请求', exact: true }).click();
  await page.getByRole('button', { name: '重试原设备请求', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
}
export async function verifyDeviceIdentity(page: Page, code: string, publicKey: string): Promise<void> {
  const row = page.getByRole('row').filter({ hasText: code }); await expect(row).toHaveCount(1);
  await row.getByRole('button', { name: '查看身份', exact: true }).click();
  await expect(page.getByLabel('登记公钥', { exact: true })).toHaveValue(publicKey);
  await expect(page.getByLabel('设备ID', { exact: true })).toHaveValue(/[0-9a-f-]{36}/);
  await page.getByRole('button', { name: '关闭', exact: true }).click();
}
export async function changeDevice(page: Page, code: string, enable: boolean): Promise<void> {
  const row = page.getByRole('row').filter({ hasText: code });
  await row.getByRole('button', { name: enable ? '启用' : '停用', exact: true }).click();
  await page.getByLabel('启停原因', { exact: true }).fill(enable ? '维护完成，要求原人员重新登录' : '原机维护，保留当前订单占用');
  await page.getByText('已核对设备和影响，确认此次启停', { exact: true }).click();
  await page.screenshot({ path: '../.local/web-device-status-confirmation.png', animations: 'disabled' });
  await page.getByRole('button', { name: enable ? '确认重新启用 PDA 设备' : '确认停用 PDA 设备', exact: true }).click();
  await recoverDeviceRequest(page);
  await expect(row).toContainText(enable ? '已启用' : '已停用');
}
