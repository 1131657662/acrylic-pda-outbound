import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import { orderLogin, orderStock, chooseProduct } from './order-fixture';

async function setPolicy(page: Page, choice: string, reason: string): Promise<void> {
  await page.getByRole('link', { name: '出库政策', exact: true }).click();
  await page.getByRole('button', { name: '设置政策', exact: true }).click();
  await page.getByRole('dialog').getByText(choice, { exact: true }).click();
  await expect(page.getByRole('radio', { name: choice, exact: true })).toBeChecked();
  await page.getByLabel('政策设置原因', { exact: true }).fill(reason);
  await page.getByRole('button', { name: '保存政策', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0);
}
async function createOrder(page: Page): Promise<string> {
  await page.getByRole('link', { name: '装车订单', exact: true }).click();
  await page.getByRole('button', { name: '新建订单', exact: true }).click();
  await page.getByLabel('车号', { exact: true }).fill(`发布测试-${Date.now()}`);
  await chooseProduct(page, '993');
  await page.getByRole('spinbutton', { name: '993计划包数' }).fill('350');
  await page.getByRole('button', { name: '保存草稿', exact: true }).click();
  await expect(page).toHaveURL(/\/orders\/[^/]+$/);
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0);
  return page.url();
}
async function publishWithLostResponse(page: Page): Promise<void> {
  await page.getByRole('button', { name: '发布订单', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('自动扣库存开启');
  await page.getByLabel('发布原因', { exact: true }).fill('现场核对后发布');
  let lost = false; let key = '';
  await page.route('**/api/v1/orders/*/publish?warehouseId=*', async (route) => {
    if (lost) { expect(route.request().headers()['idempotency-key']).toBe(key); await route.continue(); return; }
    lost = true; key = route.request().headers()['idempotency-key'] ?? '';
    expect((await route.fetch()).status()).toBe(200); await route.abort('failed');
  });
  await page.getByRole('button', { name: '确认发布', exact: true }).click();
  await expect(page.getByText('发布结果未确认，请重试原发布请求。')).toBeVisible();
  await expect(page.getByLabel('发布原因', { exact: true })).toBeDisabled();
  await page.getByRole('button', { name: '重试原发布请求', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(page.locator('.order-detail-title')).toContainText('待领取');
}
test('publish fixes policy; changed policy applies only on explicit republish, then cancel', async ({ page }) => {
  await orderLogin(page); await orderStock(page);
  await setPolicy(page, '开启自动扣库存', '浏览器测试开启，验证旧单政策固定');
  const url = await createOrder(page);
  await publishWithLostResponse(page);
  await setPolicy(page, '关闭自动扣库存', '浏览器测试关闭，验证新版本政策');
  await page.goto(url);
  const history = page.locator('section').filter({ has: page.getByRole('heading', { name: '发布记录', exact: true }) });
  await expect(history.locator('.el-table__body tr')).toHaveCount(1);
  await expect(history.locator('.el-table__body tr')).toContainText('开启');
  await page.getByRole('button', { name: '修订并重新发布', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('自动扣库存关闭');
  await page.getByRole('spinbutton', { name: '993计划包数' }).fill('351');
  await page.getByLabel('修订原因', { exact: true }).fill('计划改为351包并采用新政策');
  await page.getByRole('button', { name: '保存并重新发布', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(page.locator('.metrics')).toContainText('351 包');
  await expect(history.locator('.el-table__body tr')).toHaveCount(2);
  await expect(history.locator('.el-table__body tr').first()).toContainText('关闭');
  await expect(history.locator('.el-table__body tr').last()).toContainText('已被替代');
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0);
  await page.screenshot({ path: '../.local/web-order-published.png', fullPage: true, animations: 'disabled' });
  await page.getByRole('button', { name: '作废订单', exact: true }).click();
  await page.getByLabel('作废原因', { exact: true }).fill('测试结束，保留发布记录');
  await page.getByRole('button', { name: '确认作废', exact: true }).click();
  await expect(page.locator('.order-detail-title')).toContainText('已作废');
  await expect(history.getByText('已被替代', { exact: true })).toHaveCount(2);
});
