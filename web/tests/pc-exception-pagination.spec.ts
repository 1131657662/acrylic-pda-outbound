import { test, expect, type Page } from '@playwright/test';
import { exceptionFixture, exceptionLogin, stopException, exceptionView } from './pc-exception-fixture';

test('PC exception paged manifest preserves edits and submits all 27 original packages', async ({ page }) => {
  test.setTimeout(90000); const f = exceptionFixture(7); await exceptionLogin(page, f);
  await page.goto(`/orders/${f.order}?warehouse=${f.warehouse}&search=${f.query}&page=2&pageSize=20&tab=exceptions`);
  await page.getByRole('button', { name: '查看PC异常处置与交接', exact: true }).click(); await expect(page).toHaveURL(/page=2/);
  await stopException(page); const planner = page.locator('.pc-exception-planner');
  await page.getByLabel('接续账号', { exact: true }).fill(f.target); await choose(page, f.barcodes[0]!);
  await planner.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(planner.locator('.el-table__body tbody tr')).toHaveCount(7); await choose(page, f.barcodes[20]!);
  await planner.getByRole('button', { name: '上一页', exact: true }).click();
  await expect(planner.locator('.el-select').filter({ has: page.getByRole('combobox', { name: `处置 ${f.barcodes[0]}`, exact: true }) })).toContainText('本车保留');
  await page.getByRole('button', { name: '放弃未提交方案', exact: true }).click();
  await expect(page.getByLabel('接续账号', { exact: true })).toHaveValue('');
  await expect(planner.locator('.el-select').filter({ has: page.getByRole('combobox', { name: `处置 ${f.barcodes[0]}`, exact: true }) })).toContainText('逐包选择');
  await page.getByLabel('接续账号', { exact: true }).fill(f.target);
  await page.getByLabel('交接及处置原因', { exact: true }).fill('分页逐包核对，全部保留交接');
  await page.getByText('已逐包核对全部原有效包、实物去向和接续账号', { exact: true }).click();
  for (const barcode of f.barcodes.slice(0, 20)) await choose(page, barcode);
  await expect(page.getByRole('button', { name: '提交处置方案', exact: true })).toBeDisabled();
  await planner.getByRole('button', { name: '下一页', exact: true }).click();
  for (const barcode of f.barcodes.slice(20)) await choose(page, barcode);
  await expect(planner).toContainText('已核对 27 / 27 包');
  await page.screenshot({ path: '../.local/pc-exception-manifest-page2.png', animations: 'disabled', fullPage: true });
  await page.getByRole('button', { name: '提交处置方案', exact: true }).click();
  await expect(page.locator('.pc-exception-plan')).toContainText('待主管审核');
  const lines = (await exceptionView(page, f)).plans[0]!.body.lines;
  expect(lines).toHaveLength(27); expect(new Set(lines.map(line => line.entryId)).size).toBe(27);
});

async function choose(page: Page, barcode: string): Promise<void> {
  await page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: `处置 ${barcode}`, exact: true }) }).click();
  await page.getByRole('listbox', { name: `处置 ${barcode}`, exact: true }).getByRole('option', { name: '本车保留', exact: true }).click();
}
