import { test, expect } from '@playwright/test';
import { z } from 'zod';
import { candidateLogin, candidateOrder, checkCandidateCount } from './temporary-candidates-fixture';

test.skip(!process.env.TEMP_CANDIDATE_PRIVATE, 'Requires a protected independent MySQL/HTTP fixture');

test('actual candidates paginate, preserve warning on failure and reject mismatched context', async ({ page }, info) => {
  await candidateLogin(page); await candidateOrder(page);
  const panel = page.getByRole('region', { name: '临时单重复候选核对' });
  await expect(panel.locator('.el-table__body tr')).toHaveCount(20);
  await expect(panel.locator('.el-table__body tr').first()).toContainText('后台已确认出库');
  await panel.getByRole('listitem', { name: '第 2 页', exact: true }).click();
  await expect(panel.locator('.el-table__body tr')).toHaveCount(1);
  await expect(panel).toContainText('待主管处理');
  await panel.getByRole('listitem', { name: '第 1 页', exact: true }).click();
  await expect(panel.locator('.el-table__body tr').first()).toContainText('后台已确认出库');
  await page.screenshot({ path: info.outputPath('actual-success-candidate.png'), fullPage: true });
  await page.route('**/temporary-candidates?**', route => route.abort('failed'));
  await panel.getByRole('button', { name: '重新核对候选' }).click();
  await expect(panel).toContainText('候选暂未核对');await expect(panel).not.toContainText('暂无候选');
  await page.unroute('**/temporary-candidates?**');
  await panel.getByRole('button', { name: '重新核对候选' }).click();await checkCandidateCount(page);
  await page.route('**/temporary-candidates?**', async route => {
    const response = await route.fetch(); const body = z.object({ data: z.object({ orderVersion: z.string() }).passthrough() }).passthrough().parse(await response.json());
    body.data.orderVersion = '999999'; await route.fulfill({ response, json: body });
  });
  await panel.getByRole('button', { name: '重新核对候选' }).click();
  await expect(panel).toContainText('正式订单已变化');await expect(panel.locator('.el-table__body tr')).toHaveCount(0);
});

test('READY revision refreshes candidates and receipt hints survive actual publish', async ({ page }, info) => {
  await candidateLogin(page);await candidateOrder(page);
  await page.getByRole('button', { name: '修订并重新发布', exact: true }).click();
  await page.getByLabel('车号', { exact: true }).fill('浏览器修订候选车');
  await page.getByLabel('修订原因', { exact: true }).fill('隔离验证提示刷新');
  await page.getByRole('button', { name: '保存并重新发布', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();await checkCandidateCount(page);
  await expect(page.getByRole('region', { name: '临时单重复候选核对' })).toContainText('重叠产品');
  await page.screenshot({ path: info.outputPath('ready-revised-candidates.png'), fullPage: true });
});

test('new formal order and PC start display current candidates', async ({ page }, info) => {
  await candidateLogin(page);await candidateOrder(page);
  await page.getByRole('button', { name: '返回订单列表', exact: true }).click();
  await page.getByRole('button', { name: '新建订单', exact: true }).click();
  await page.getByLabel('车号', { exact: true }).fill('浏览器新正式车');
  await page.getByRole('combobox', { name: '添加产品' }).fill('003');
  await page.getByRole('option').filter({ hasText: '003' }).first().click();
  await page.getByRole('button', { name: '保存草稿', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();await checkCandidateCount(page);
  await page.getByRole('button', { name: '发布订单', exact: true }).click();
  await page.getByRole('dialog').getByLabel('发布原因', { exact: true }).fill('隔离浏览器发布验证');
  await page.getByRole('dialog').getByRole('button', { name: '确认发布', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeHidden();await checkCandidateCount(page);
  await page.getByRole('button', { name: 'PC 手工出库', exact: true }).click();
  await page.getByRole('button', { name: '确认开始PC录入', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('操作原因', { exact: true }).fill('隔离验证PC开始提示');
  await dialog.getByText('已核对本单、实物及上述操作影响', { exact: true }).click();
  await expect(dialog.getByRole('checkbox')).toBeChecked();
  await dialog.getByRole('button', { name: '确认此操作', exact: true }).click();
  await expect(dialog).toBeHidden();await checkCandidateCount(page);
  await expect(page.getByRole('region', { name: '临时单重复候选核对' })).toContainText('已有作业或出库证据');
  await page.screenshot({ path: info.outputPath('pc-start-candidates.png'), fullPage: true });
});
