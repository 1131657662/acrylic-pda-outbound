import { expect, test } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { faultBrowser, openFault, stored } from './handover-fault-browser-fixture';

async function fillFormal(page: import('@playwright/test').Page): Promise<void> {
  await page.getByLabel('断电收回保管时间', { exact: true }).fill('2026-10-09T00:00');
  await page.getByLabel('设备保管位置', { exact: true }).fill('开发隔离柜');
  await page.getByLabel('货物位置', { exact: true }).fill('开发隔离货位');
  for (const label of ['原召回编号', '原恢复包编号']) await page.getByLabel(label, { exact: true }).fill(randomUUID());
  await page.getByLabel('归档原文摘要', { exact: true }).fill('a'.repeat(64));
  for (const label of ['采集截至', '审计截至', '扫描截至']) await page.getByLabel(label, { exact: true }).fill('1');
  await page.getByRole('checkbox').check();
}

test('FORMAL unknown outcome retains exact archive and original key after reload', async ({ page }) => {
  const f = await faultBrowser(page); await page.goto(f.url); await openFault(page); await fillFormal(page);
  await expect(page.getByLabel('归档集合编号', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '确认提交故障围栏', exact: true }).click();
  await expect.poll(() => f.posts.length).toBe(1);
  const original = await stored(page, f.key);
  expect(JSON.parse(f.posts[0] ?? '').archivedEvidence.sourceKind).toBe('FORMAL');
  await page.reload(); await openFault(page);
  await expect(page.getByLabel('原恢复包编号', { exact: true })).toBeDisabled();
  await page.getByRole('button', { name: '重试原请求', exact: true }).click();
  await expect.poll(() => f.posts.length).toBe(2);
  expect(f.posts[1]).toBe(f.posts[0]); expect(await stored(page, f.key)).toBe(original);
});

test('FORMAL confirmed receipt displays original recall and recovery references', async ({ page }) => {
  const f = await faultBrowser(page);
  f.controls.reply = async route => { await route.fulfill({ json: await f.response(route.request().postData() ?? '') }); };
  await page.goto(f.url); await openFault(page); await fillFormal(page);
  await page.getByRole('button', { name: '确认提交故障围栏', exact: true }).click();
  await expect.poll(() => f.posts.length).toBe(1);
  const saved = JSON.parse((await stored(page, f.key)) ?? '{}');
  f.facts.fault.receipt = saved.receipt; f.facts.context.stopState = 'FAULT_FENCED'; f.facts.fault.entryAvailable = false;
  await page.reload();
  const evidence = page.getByRole('region', { name: '主管故障围栏凭据', exact: true });
  await expect(evidence).toBeVisible(); await evidence.getByText('可信归档截点', { exact: true }).click();
  await expect(evidence.getByText(`原召回 ${saved.receipt.businessDecision.archivedEvidence.changeRequestId}`, { exact: true })).toBeVisible();
  await expect(evidence.getByText(/原恢复包 /)).toBeVisible();
});

for (const width of [390, 720]) test(`FORMAL ${width} form keeps both actions visible`, async ({ page }) => {
  await page.setViewportSize({ width, height: 780 });
  const f = await faultBrowser(page); await page.goto(f.url); await openFault(page); await fillFormal(page);
  for (const name of ['返回核对', '确认提交故障围栏']) {
    const bounds = await page.getByRole('button', { name, exact: true }).boundingBox();
    expect(bounds).not.toBeNull(); expect(bounds!.x).toBeGreaterThanOrEqual(0);
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(width); expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(780);
  }
  await page.screenshot({ path: `${process.env.HANDOVER_FAULT_BOUNDARY_OUTPUT}/formal-form-${width}.png` });
});
