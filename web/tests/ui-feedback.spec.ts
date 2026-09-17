import { test, expect } from '@playwright/test';
import { queryOrders, uiLogin } from './ui-fixture';

test('initial pending request shows loading; a server error has an explicit retry', async ({ page }) => {
  await uiLogin(page);
  let release: (() => void) | undefined;
  const gate = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/v1/orders?**', async route => {
    const response = await route.fetch(); const body = await response.json();
    await gate;
    await route.fulfill({ status: 500, json: { ...body, code: 'INTERNAL_ERROR', message: '后台暂时未能确认结果', data: null } });
  });
  await page.goto('/orders?page=2');
  await expect(page.getByRole('heading', { name: '正在查询订单', exact: true })).toBeVisible();
  await expect(page.getByText('共 0 单', { exact: true })).toHaveCount(0);
  release?.();
  await expect(page.getByRole('heading', { name: '订单查询失败', exact: true })).toBeVisible();
  await page.unrouteAll({ behavior: 'wait' });
  await page.getByRole('button', { name: '重试查询', exact: true }).click();
  await expect(page.locator('.orders-grid')).toBeVisible();
});

test('low-height editor keeps footer reachable and drawer navigation remains available', async ({ page }) => {
  await uiLogin(page); await queryOrders(page);
  expect((await page.getByRole('button', { name: '查询', exact: true }).boundingBox())?.height).toBe(36);
  await page.setViewportSize({ width: 1024, height: 500 });
  await page.getByRole('button', { name: '新建订单', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('button', { name: '保存草稿', exact: true })).toBeInViewport();
  const bounds = await dialog.boundingBox();
  expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(500);
  await dialog.getByRole('button', { name: '取消', exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: '打开导航', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '腈纶出库', exact: true })).toBeVisible();
  await page.getByRole('link', { name: '库存查询', exact: true }).click();
  await expect(page.getByRole('heading', { name: '库存查询', exact: true })).toBeVisible();
  await expect(page.getByRole('dialog', { name: '腈纶出库', exact: true })).toBeHidden();
});
