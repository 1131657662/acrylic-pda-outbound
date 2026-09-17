import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { z } from 'zod';
import { orderLogin } from './order-fixture';

async function login(page: Page): Promise<void> {
  await orderLogin(page);
  await page.goto('/inventory/imports');
}

function row(barcode: string, weight: string): string {
  return `${'WEB TEST'.padEnd(20)}${'FIBER'.padEnd(14)}${barcode.padEnd(13)}${weight.padEnd(10)}\r\n`;
}
async function upload(page: Page, text: string): Promise<void> {
  await page.locator('input[type=file]').setInputFiles({ name: 'web-publication-sku.txt', mimeType: 'text/plain', buffer: Buffer.from(text, 'ascii') });
  await page.getByRole('button', { name: '上传并预检' }).click();
  await expect(page.locator('.detail-panel')).toContainText('web-publication-sku.txt');
  await page.getByRole('button', { name: '核对差异并发布' }).click();
  await expect(page.getByRole('button', { name: '确认发布库存版本' })).toBeVisible();
  for (const label of ['候选映射', '相同重复条码', '本次全量文件缺少']) {
    const target = page.getByRole('checkbox', { name: new RegExp(label) });
    if (await target.count()) {
      await page.locator('.el-checkbox').filter({ hasText: label }).click();
      await expect(target).toBeChecked();
    }
  }
}

test('publish survives lost response, inventory query, mapping history and second snapshot', async ({ page }) => {
  const number = Date.now() % 9999990;
  const first = `997B${String(number).padStart(7, '0')}`;
  const second = `997B${String(number + 1).padStart(7, '0')}`;
  await login(page);
  await upload(page, row(first, '300.5') + row(second, '15.5'));
  let loseResponse = true;
  let receipt = '';
  await page.route('**/api/v1/inventory/imports/*/publish?*', async (route) => {
    if (!loseResponse) { await route.continue(); return; }
    loseResponse = false;
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    receipt = z.object({ data: z.object({ id: z.string() }) }).parse(await response.json()).data.id;
    await route.abort('failed');
  });
  await page.getByRole('button', { name: '确认发布库存版本' }).click();
  await expect(page.locator('.publication-panel')).toContainText('连接失败');
  const retry = page.waitForResponse((response) => response.url().includes('/publish?') && response.status() === 200);
  await page.getByRole('button', { name: '确认发布库存版本' }).click();
  expect(z.object({ data: z.object({ id: z.string() }) }).parse(await (await retry).json()).data.id).toBe(receipt);
  await expect(page.locator('.detail-panel')).toContainText('此文件已发布');
  await page.getByRole('link', { name: '库存查询' }).click();
  await page.getByLabel('查询条码', { exact: true }).fill(first);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.locator('.el-table__body tr').filter({ hasText: first })).toContainText('300.5');
  await expect(page.getByText('当前版本', { exact: true })).toBeVisible();
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0);
  await expect(page.locator('.el-table').first().locator('.el-table__body tr')).toHaveCount(1);
  await page.screenshot({ path: '../.local/web-stock.png', fullPage: true, animations: 'disabled' });
  await page.getByRole('link', { name: '发货代码' }).click();
  const mappingRow = page.locator('.el-table__body tr').filter({ hasText: '997' });
  await mappingRow.getByRole('button', { name: '修订' }).click();
  await page.getByLabel('产品型号', { exact: true }).fill('WEB VERIFIED');
  const enabled = page.getByRole('switch', { name: '启用状态' });
  if (await enabled.isChecked()) await page.getByRole('dialog').locator('.el-switch__core').click();
  await expect(enabled).not.toBeChecked();
  const reason = `浏览器验证：人工核对并停用 ${first}`;
  await page.getByLabel('变更原因', { exact: true }).fill(reason);
  await page.getByRole('button', { name: '保存映射版本' }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await mappingRow.getByRole('button', { name: '历史', exact: true }).click();
  await expect(page.getByRole('heading', { name: '997 · 映射历史' })).toBeVisible();
  await expect(page.getByText(reason, { exact: true })).toBeVisible();
  await page.getByRole('link', { name: '库存导入' }).click();
  await upload(page, row(first, '301.25'));
  await expect(page.getByRole('checkbox', { name: '确认本次全量文件缺少 1 包' })).toBeChecked();
  await page.getByText('候选映射', { exact: true }).click();
  await expect(page.getByText('保持停用', { exact: true })).toBeVisible();
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0);
  await page.screenshot({ path: '../.local/web-publication.png', fullPage: true, animations: 'disabled' });
  await page.getByRole('button', { name: '确认发布库存版本' }).click();
  await expect(page.locator('.detail-panel')).toContainText('此文件已发布');
  await page.getByRole('link', { name: '库存查询' }).click();
  await page.getByLabel('查询条码', { exact: true }).fill(second);
  await page.getByRole('button', { name: '查询', exact: true }).click();
  await expect(page.locator('.el-table__body tr').filter({ hasText: second })).toContainText('未列出');
  await page.getByText('待核对差异', { exact: true }).click();
  await expect(page.locator('.el-table__body tr').filter({ hasText: second }).last()).toContainText('上游未列出');
});
