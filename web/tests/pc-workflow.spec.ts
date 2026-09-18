import { test, expect } from '@playwright/test';
import { addPc, confirmPcDialog, pcFixture, pcLogin, pcPath, startPc, workView } from './pc-fixture';

test('PC full plan uses explicit package confirmation and preserves order navigation', async ({ page }) => {
  const f = pcFixture(0); await pcLogin(page, f);
  await page.goto(pcPath(f).replace('/pc-outbound', ''));
  await page.getByRole('button', { name: 'PC 手工出库', exact: true }).click();
  await expect(page).toHaveURL(/page=2/);
  await page.getByRole('button', { name: '发布并开始PC录入', exact: true }).click();
  await confirmPcDialog(page, '按订单核对开始');
  await page.getByLabel('完整条码或条码片段', { exact: true }).fill(f.query);
  await expect(page.locator('.pc-candidate-group')).toHaveCount(3);
  await expect(page.locator('.pc-match-summary')).toContainText('共 24 包匹配');
  await page.getByLabel('完整条码或条码片段', { exact: true }).press('Enter');
  expect((await workView(page, f)).products.every((p: { actualCount: number }) => p.actualCount === 0)).toBe(true);
  await expect(page.getByRole('button', { name: '确认计入本单', exact: true })).toBeDisabled();
  const group = page.getByRole('region', { name: '发货代码 003', exact: true });
  await group.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(group.locator('.pc-candidate-row')).toHaveCount(3); await expect(page.locator('.pc-candidate-group')).toHaveCount(3);
  for (const i of [0, 1, 8, 9, 16, 17]) await addPc(page, f.barcodes[i]!);
  await expect(page.locator('.pc-total')).toContainText('1803.006 kg');
  await page.getByRole('button', { name: '结单核对', exact: true }).click(); await confirmPcDialog(page, '', true);
  await expect(page.locator('.pc-result')).toContainText('后台已确认出库');
  expect((await workView(page, f)).submission.receipt.inventoryDeducted).toBe(true);
  await expect(page.locator('.pc-records .el-loading-mask:visible')).toHaveCount(0);
  await page.screenshot({ path: '../.local/pc-result-completed.png', animations: 'disabled' });
  await page.getByRole('button', { name: '返回订单详情', exact: true }).click(); await expect(page).toHaveURL(/page=2/);
  await page.getByRole('button', { name: '返回订单列表', exact: true }).click(); await expect(page).toHaveURL(/search=.*page=2/);
});

test('short shipment requires approval and viewer can inspect only', async ({ page, browser }) => {
  const f = pcFixture(1); await pcLogin(page, f); await startPc(page, f); await addPc(page, f.barcodes[0]!);
  await page.getByRole('button', { name: '结单核对', exact: true }).click();
  await expect(page.getByRole('button', { name: '确认结单并提交校验', exact: true })).toBeDisabled();
  await confirmPcDialog(page, '本车实物不足，申请短装', true);
  await expect(page.locator('.pc-result')).toContainText('待主管审核');
  const other = await browser.newContext(); const viewer = await other.newPage();
  await pcLogin(viewer, f, true); await viewer.goto(pcPath(f)); await expect(viewer.locator('.pc-records')).toContainText(f.barcodes[0]!);
  await expect(viewer.getByRole('button', { name: '核对并批准出库' })).toHaveCount(0); await other.close();
  await page.getByRole('button', { name: '核对并批准出库', exact: true }).click(); await confirmPcDialog(page, '已核对实物及车号，批准短装');
  await expect(page.locator('.pc-result')).toContainText('后台已确认出库');
  expect((await workView(page, f)).submission.receipt.inventoryDeducted).toBe(false);
});

test('other window invalidates old writer without releasing original packages', async ({ page, context }) => {
  const f = pcFixture(4); await pcLogin(page, f); await startPc(page, f); await addPc(page, f.barcodes[0]!);
  const second = await context.newPage(); await second.goto(pcPath(f));
  await expect(second.locator('.pc-writer-notice')).toContainText('当前窗口只读');
  await second.getByRole('button', { name: '在本窗口继续', exact: true }).click(); await confirmPcDialog(second);
  await page.getByLabel('完整条码或条码片段', { exact: true }).fill(f.barcodes[1]!);
  await page.getByRole('button', { name: '确认计入本单', exact: true }).click();
  await expect(page.getByText(/作业记录已变化/).first()).toBeVisible();
  expect((await workView(page, f)).products[0].actualCount).toBe(1);
  await page.getByRole('button', { name: '核对最新记录', exact: true }).click();
  await expect(page.locator('.pc-writer-notice')).toContainText('当前窗口只读');
  await addPc(second, f.barcodes[1]!); expect((await workView(second, f)).products[0].actualCount).toBe(2);
});

test('withdrawn entry stays in history and rejected submission needs supervised resume', async ({ page }) => {
  const f = pcFixture(10); await pcLogin(page, f); await startPc(page, f); await addPc(page, f.barcodes[0]!);
  await page.getByRole('tab', { name: '本单录入记录', exact: true }).click();
  await page.getByRole('button', { name: '撤销本次录入', exact: true }).click(); await confirmPcDialog(page, '已核对实物未装车，误选撤销');
  await expect(page.locator('.pc-records')).toContainText('已撤销'); await addPc(page, f.barcodes[0]!);
  await page.getByRole('button', { name: '结单核对', exact: true }).click(); await confirmPcDialog(page, '等待补装', true);
  await page.getByRole('button', { name: '退回并保留占用', exact: true }).click(); await confirmPcDialog(page, '核对后补齐');
  await page.getByRole('button', { name: '主管核验恢复作业', exact: true }).click(); await confirmPcDialog(page, '逐包核对，全部原有效包仍在原车，未卸货');
  await page.getByRole('button', { name: '在本窗口继续', exact: true }).click(); await confirmPcDialog(page);
  expect((await workView(page, f)).work.round).toBe(2); expect((await workView(page, f)).products[0].actualCount).toBe(1);
});

test('record pages query the server and keep precise counts and sequence numbers', async ({ page }) => {
  const f = pcFixture(9); await pcLogin(page, f); await page.goto(pcPath(f));
  await page.getByRole('tab', { name: '本单录入记录', exact: true }).click();
  const records = page.locator('.pc-records'); await expect(records).toContainText('后台已保存共 24 条记录');
  await expect(records.locator('.el-table__body tr')).toHaveCount(20);
  await records.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(records.locator('.el-table__body tr')).toHaveCount(4);
  await expect(records.locator('.el-table__body tr').last()).toContainText(f.barcodes[0]!);
  await expect(records.locator('.el-table__body tr').last()).toContainText('300.501');
  await page.getByLabel('查询录入代码', { exact: true }).fill('571');
  await page.getByRole('button', { name: '查询记录', exact: true }).click();
  await expect(records).toContainText('后台已保存共 8 条记录'); await expect(records.locator('.el-table__body tr')).toHaveCount(8);
});
