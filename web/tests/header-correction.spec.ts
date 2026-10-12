import { test, expect, type Page } from '@playwright/test';
import { headerOpen, headerDialog, headerSubmit, headerDocument, headerEvidence } from './header-correction-fixture';
import { receiptSchema } from '../src/corrections/header-contracts';

test.skip(!process.env.P06_WEB_INPUT, 'Requires preserved isolated HTTP/MySQL header facts');

test('header storage failure prevents CREATE submission', async ({ page }) => {
  await headerOpen(page, 'maker'); let posts = 0;
  page.on('request', r => { if (r.method() === 'POST' && r.url().endsWith('/api/v1/corrections')) posts++; });
  await headerDialog(page, { button: page.getByRole('button', { name: '申请抬头更正', exact: true }),
    reason: 'isolated storage failure', vehicle: 'P06-quota' });
  await page.evaluate(() => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
      if (key.startsWith('acrylic.header-correction.')) throw new Error('Isolated storage quota failure');
      original.call(this, key, value);
    };
  });
  await page.getByRole('dialog').getByRole('button', { name: '确认提交抬头更正', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('本次操作未发送');
  expect(posts).toBe(0);
});

async function approved(maker: Page, checker: Page): Promise<string> {
  await headerDialog(maker, { button: maker.getByRole('button', { name: '申请抬头更正', exact: true }),
    reason: 'isolated browser header correction', vehicle: 'P06-browser-vehicle' });
  const created = await headerSubmit(maker, '确认提交抬头更正');
  await expect(headerDocument(maker, created.documentId).getByRole('button', { name: '审批抬头更正', exact: true })).toHaveCount(0);
  await headerOpen(checker, 'checker');
  await headerDialog(checker, { button: headerDocument(checker, created.documentId).getByRole('button', { name: '审批抬头更正', exact: true }),
    reason: 'isolated independent supervisor approval' });
  const receipt = await headerSubmit(checker, '确认批准抬头更正');
  expect(receipt.businessDecision.status).toBe('EFFECTIVE');
  return created.documentId;
}

async function withdraw(checker: Page, id: string): Promise<void> {
  await headerDialog(checker, { button: headerDocument(checker, id).getByRole('button', { name: '发起撤销补偿', exact: true }),
    reason: 'isolated original approver withdrawal' });
  const receipt = await headerSubmit(checker, '确认发起撤销补偿');
  expect(receipt.businessDecision.status).toBe('WITHDRAW_PENDING');
  await expect(headerDocument(checker, id).getByRole('button', { name: '确认撤销补偿', exact: true })).toHaveCount(0);
}

async function lostConfirmation(maker: Page, id: string): Promise<void> {
  let committed: Buffer | null = null; let posts = 0;
  await maker.reload();
  await headerDialog(maker, { button: headerDocument(maker, id).getByRole('button', { name: '确认撤销补偿', exact: true }),
    reason: 'isolated original applicant compensation' });
  await maker.route('**/api/v1/corrections', async route => {
    posts++; const response = await route.fetch(); committed = await response.body();
    expect(response.status(), committed.toString('utf8')).toBe(200);
    headerEvidence('lost-confirmation.command.json', route.request().postData() ?? '');
    headerEvidence('lost-confirmation.receipt.json', committed); await route.abort('failed');
  });
  await maker.getByRole('dialog').getByRole('button', { name: '确认撤销补偿', exact: true }).click();
  await expect(maker.getByRole('dialog').getByRole('alert')).toContainText('原请求已保留');
  await maker.getByRole('dialog').getByRole('button', { name: '返回核对', exact: true }).click();
  await expect(maker.getByRole('button', { name: '核对或重试原请求', exact: true })).toBeVisible();
  await maker.reload();
  const recovered = maker.waitForResponse(r => r.url().includes('/corrections/commands/') && r.status() === 200);
  await maker.getByRole('button', { name: '核对或重试原请求', exact: true }).click();
  expect(await (await recovered).body()).toEqual(committed); expect(posts).toBe(1);
  await expect(headerDocument(maker, id)).toContainText('已补偿撤销');
  await expect(maker.getByRole('button', { name: '核对或重试原请求', exact: true })).toHaveCount(0);
}

test('separated browser actors compensate and recover the original committed response', async ({ page, browser }) => {
  test.setTimeout(90000); const context = await browser.newContext({ baseURL: process.env.OUTBOUND_WEB_URL });
  const checker = await context.newPage();
  try {
    await headerOpen(page, 'maker'); const id = await approved(page, checker);
    await withdraw(checker, id); await lostConfirmation(page, id);
    const bytes = await page.evaluate(() => Object.values(localStorage).filter(value => value.includes('headerRestoration')));
    expect(bytes.length).toBeGreaterThan(0);
    const receipts = bytes.map(value => receiptSchema.parse(JSON.parse(value).receipt));
    expect(receipts.some(receipt => receipt.businessDecision.headerRestoration?.restoredFields.includes('vehicle'))).toBe(true);
    await page.screenshot({ path: `${process.env.P06_WEB_EVIDENCE}/compensated-page.png`, fullPage: true });
  } finally { await context.close(); }
});
