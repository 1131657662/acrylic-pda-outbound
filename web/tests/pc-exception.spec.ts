import { test, expect } from '@playwright/test';
import { addPc, pcPath, confirmPcDialog, pendingCount } from './pc-fixture';
import { exceptionFixture, exceptionLogin, exceptionPath, confirmException, stopException, fillExceptionPlan, exceptionView } from './pc-exception-fixture';

test('PC exception transfers retained work to another operator and applies release and quarantine', async ({ page, browser }) => {
  const f = exceptionFixture(0); await exceptionLogin(page, f); await stopException(page);
  await fillExceptionPlan(page, f); await page.getByRole('button', { name: '提交处置方案', exact: true }).click();
  await expect(page.locator('.pc-exception-plan')).toContainText('待主管审核');
  await page.getByRole('button', { name: '批准处置方案', exact: true }).click(); await confirmException(page, '批准处置方案');
  await expect(page.locator('.pc-exception-plan')).toContainText('已批准，待本人交接');
  expect((await exceptionView(page, f)).work.status).toBe('STOPPED');
  await expect(page.getByRole('button', { name: '本人确认实物交接', exact: true })).toHaveCount(0);
  const other = await browser.newContext(); const target = await other.newPage(); await exceptionLogin(target, f, f.target);
  await target.getByRole('button', { name: '本人确认实物交接', exact: true }).click(); await confirmException(target, '确认实物交接');
  await expect(target.locator('.pc-exception-plan')).toContainText('交接已生效');
  await target.screenshot({ path: '../.local/pc-exception-applied.png', animations: 'disabled', fullPage: true });
  await target.getByRole('button', { name: '返回PC作业', exact: true }).click(); await expect(target).toHaveURL(/page=2/);
  await target.getByRole('button', { name: '在本窗口继续', exact: true }).click(); await confirmPcDialog(target);
  await addPc(target, f.barcodes[1]!); await target.getByRole('tab', { name: '本单录入记录', exact: true }).click();
  await expect(target.locator('.pc-records')).toContainText('已卸货释放'); await expect(target.locator('.pc-records')).toContainText('已实物隔离');
  await page.goto(pcPath(f)); await expect(page.getByRole('button', { name: '在本窗口继续', exact: true })).toHaveCount(0); await other.close();
});

test('PC exception stop plan review and apply recover committed responses after refresh', async ({ page }) => {
  const f = exceptionFixture(1); await exceptionLogin(page, f); const writes: Record<string, number> = {};
  await page.route(`**/orders/${f.order}/pc/exception/*?**`, async route => {
    if (route.request().method() !== 'POST') return route.continue();
    const action = new URL(route.request().url()).pathname.split('/').at(-1)!; writes[action] = (writes[action] ?? 0) + 1;
    expect((await route.fetch()).status()).toBe(200); await route.abort('failed');
  });
  await page.getByRole('button', { name: '停止并核验实物', exact: true }).click(); await confirmException(page, '确认停止PC作业');
  await recoverAfterRefresh(page); await fillExceptionPlan(page, f, f.username);
  await page.getByRole('button', { name: '提交处置方案', exact: true }).click(); await recoverAfterRefresh(page);
  await page.getByRole('button', { name: '批准处置方案', exact: true }).click(); await confirmException(page, '批准处置方案'); await recoverAfterRefresh(page);
  await page.getByRole('button', { name: '本人确认实物交接', exact: true }).click(); await confirmException(page, '确认实物交接'); await recoverAfterRefresh(page);
  expect(writes).toEqual({ stop: 1, plan: 1, review: 1, apply: 1 }); expect((await exceptionView(page, f)).work.round).toBe(2);
});

test('PC exception approval withdrawal retains prior plan and permits reviewed replacement', async ({ page }) => {
  const f = exceptionFixture(2); await exceptionLogin(page, f); await stopException(page); await fillExceptionPlan(page, f, f.username);
  await page.getByRole('button', { name: '提交处置方案', exact: true }).click();
  await page.getByRole('button', { name: '批准处置方案', exact: true }).click(); await confirmException(page, '批准处置方案');
  await page.getByRole('button', { name: '撤回方案批准', exact: true }).click(); await confirmException(page, '撤回方案批准');
  await expect(page.locator('.pc-exception-planner')).toBeVisible(); await expect(page.locator('.pc-exception-audit')).toContainText('退回或撤回批准');
  await fillExceptionPlan(page, f, f.username, ['卸货并释放', '卸货并释放', '卸货并隔离']);
  await page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '交接后安排', exact: true }) }).click(); await page.getByRole('option', { name: '全部卸下，结束本单', exact: true }).click();
  await page.getByRole('button', { name: '提交处置方案', exact: true }).click();
  await page.getByRole('button', { name: '批准处置方案', exact: true }).click(); await confirmException(page, '批准处置方案');
  await page.getByRole('button', { name: '本人确认实物交接', exact: true }).click(); await confirmException(page, '确认实物交接');
  await expect(page.locator('.page-title')).toContainText('已核验结束，未出库'); expect((await exceptionView(page, f)).plans).toHaveLength(2);
});

test('PC exception viewer is read only and storage failure prevents stopping', async ({ page, browser }) => {
  const f = exceptionFixture(3); await exceptionLogin(page, f, f.viewer);
  await expect(page.getByRole('button', { name: '停止并核验实物', exact: true })).toHaveCount(0);
  const other = await browser.newContext(); const operator = await other.newPage(); await exceptionLogin(operator, f); let writes = 0;
  operator.on('request', req => { if (req.method() === 'POST' && req.url().includes('/pc/exception/')) writes++; });
  await operator.evaluate(() => { const original = Storage.prototype.setItem; Storage.prototype.setItem = function (key, value) {
    if (key.startsWith('outbound.pc.request.v1:')) throw new DOMException('test quota', 'QuotaExceededError'); original.call(this, key, value);
  }; });
  await operator.getByRole('button', { name: '停止并核验实物', exact: true }).click(); await confirmException(operator, '确认停止PC作业');
  await expect(operator.getByRole('dialog').getByText('浏览器无法保存恢复资料，本次请求未发送。请恢复浏览器存储后重试。')).toBeVisible();
  expect(writes).toBe(0); expect((await exceptionView(operator, f)).work.status).toBe('ACTIVE'); await other.close();
});

test('PC exception read failure preserves last evidence and disables mutation', async ({ page }) => {
  const f = exceptionFixture(4); await exceptionLogin(page, f); await stopException(page);
  await page.route('**/pc/exception?**', route => route.abort('failed'));
  await page.getByRole('button', { name: '核对最新记录', exact: true }).click();
  await expect(page.getByText(/当前显示上次资料，已暂停操作/)).toBeVisible(); await expect(page.getByLabel('接续账号', { exact: true })).toBeDisabled();
  await page.unroute('**/pc/exception?**'); await page.getByRole('button', { name: '核对最新记录', exact: true }).click();
  await expect(page.getByLabel('接续账号', { exact: true })).toBeEnabled(); await page.goto(exceptionPath(f));
});

async function recoverAfterRefresh(page: import('@playwright/test').Page): Promise<void> {
  await expect.poll(() => pendingCount(page)).toBe(1); await page.reload();
  await page.getByRole('button', { name: '恢复原PC请求', exact: true }).click(); await expect.poll(() => pendingCount(page)).toBe(0);
  await expect(page.getByRole('button', { name: '核对最新记录', exact: true })).toBeEnabled();
}
