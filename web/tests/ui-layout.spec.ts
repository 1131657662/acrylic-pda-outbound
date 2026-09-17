import { test, expect } from '@playwright/test';
import { noPageOverflow, queryOrders, uiFixture, uiLogin } from './ui-fixture';

test('representative desktop, narrow and low-height layouts remain usable', async ({ page }) => {
  test.setTimeout(90000);
  await uiLogin(page);
  for (const [width, height] of [[1366, 768], [1440, 900], [1920, 1080], [1024, 768], [1280, 500], [390, 844]]) {
    await page.setViewportSize({ width: width!, height: height! }); await queryOrders(page); await noPageOverflow(page);
    await page.screenshot({ path: `../.local/ui-orders-${width}x${height}.png`, animations: 'disabled' });
    await page.getByRole('button', { name: '下一页', exact: true }).click(); await expect(page).toHaveURL(/page=2/);
    await page.getByRole('link', { name: /^OB/ }).first().click();
    await expect(page.getByRole('tab', { name: '产品计划', exact: true })).toBeVisible();
    await noPageOverflow(page);
    await page.screenshot({ path: `../.local/ui-detail-${width}x${height}.png`, animations: 'disabled' });
    await page.getByRole('tab', { name: '异常处理', exact: true }).click();
    await expect(page.getByRole('heading', { name: '停止与恢复装车', exact: true })).toBeVisible();
    await page.getByRole('button', { name: '返回订单列表', exact: true }).click();
    await expect(page).toHaveURL(/page=2/);
  }
});

test('unknown state, long identifiers and failed detail do not imply success or hide navigation', async ({ page }) => {
  await uiLogin(page);
  await page.route('**/api/v1/orders?**', async route => {
    const response = await route.fetch(); const body = await response.json();
    body.data.items[0].status = 'FUTURE_STATUS'; body.data.items[0].vehicleNo = '长车号'.repeat(13);
    await route.fulfill({ response, json: body });
  });
  await queryOrders(page); await expect(page.getByText('状态待核对', { exact: true }).first()).toBeVisible();
  await noPageOverflow(page);
  await page.goto(`/orders/00000000-0000-0000-0000-000000000001?warehouse=${uiFixture().warehouseId}`);
  await expect(page.getByRole('heading', { name: '暂时无法查看订单' })).toBeVisible();
  await expect(page.getByText('当前仓库中没有此订单，请返回列表重新查询。')).toBeVisible();
  await page.getByRole('button', { name: '返回订单列表', exact: true }).click();
  await expect(page.getByRole('heading', { name: '装车订单', exact: true })).toBeVisible();
  await expect(page.locator('.orders-grid .order-link').first()).toBeVisible();
  await page.unrouteAll({ behavior: 'wait' });
});
