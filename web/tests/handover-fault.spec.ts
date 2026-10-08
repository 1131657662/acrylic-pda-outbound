import { test, expect, type Page } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { z } from 'zod';
import { faultCommandSchema, faultReceiptSchema } from '../src/handover/fault-contracts';
import { testFaultAttachments, attachmentFixtureSchema } from './support/handover-fault-attachments';

const fixtureSchema = z.object({ username: z.string(), password: z.string(), command: faultCommandSchema,
  attachmentFixture: attachmentFixtureSchema.optional(), attachmentDirectory: z.string().optional() });
test.skip(process.env.HANDOVER_FAULT_VERIFY !== 'true', 'Requires a fresh explicitly enabled isolated source');
function fixture() { return fixtureSchema.parse(JSON.parse(readFileSync(process.env.HANDOVER_FAULT_FIXTURE!, 'utf8'))); }

async function login(page: Page): Promise<void> {
  const data = fixture(); await page.goto('/login');
  await page.getByLabel('账号', { exact: true }).fill(data.username);
  await page.getByLabel('密码', { exact: true }).fill(data.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.locator('.workspace')).toBeVisible();
}
async function fill(page: Page): Promise<void> {
  const body = fixture().command; const date = new Date(body.physicalControl.controlledAt);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 23);
  await page.getByLabel('断电收回保管时间', { exact: true }).fill(local);
  await page.getByLabel('设备保管位置', { exact: true }).fill(body.physicalControl.deviceLocation);
  await page.getByLabel('货物位置', { exact: true }).fill(body.physicalControl.goodsLocation);
  for (const [label, field] of [['归档集合编号', 'sourceEvidenceSetId'], ['归档验证编号', 'verificationId'],
    ['归档原文摘要', 'setSha256'], ['采集截至', 'captureHighWatermark'], ['审计截至', 'auditThroughSeq'], ['扫描截至', 'orderScanHighWatermark']] as const)
    await page.getByLabel(label, { exact: true }).fill(body.archivedEvidence[field]);
  await page.getByRole('checkbox', { name: '确认原机已断电收回保管、货物位置已核对，且没有未核明的输入' }).check();
}
async function layouts(page: Page, name: string): Promise<void> {
  for (const [width, height] of [[1440, 900], [390, 844], [720, 450]] as const) {
    await page.setViewportSize({ width, height });
    if (name === 'form') {
      const custody = page.getByRole('checkbox', { name: '确认原机已断电收回保管、货物位置已核对，且没有未核明的输入' });
      await custody.scrollIntoViewIfNeeded(); await expect(custody).toBeInViewport(); await expect(custody).toBeChecked();
      await expect(page.getByRole('button', { name: '返回核对', exact: true })).toBeInViewport({ ratio: 1 });
      await expect(page.getByRole('button', { name: '确认提交故障围栏', exact: true })).toBeInViewport({ ratio: 1 });
    } else {
      await expect(page.getByRole('button', { name: '刷新交接记录', exact: true })).toBeEnabled();
      await expect(page.getByRole('heading', { name: '主管故障围栏凭据', exact: true })).toBeVisible();
      await page.locator('.fault-evidence').scrollIntoViewIfNeeded();
      await page.locator('.fault-evidence').screenshot({ path: `${process.env.HANDOVER_FAULT_OUTPUT}/evidence-${width}.png` });
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: `${process.env.HANDOVER_FAULT_OUTPUT}/${name}-${width}.png`, fullPage: true });
  }
}
async function openCase(page: Page): Promise<void> {
  const data = fixture();
  await login(page);
  const context = await page.request.get(`/api/v1/handovers/${data.command.caseId}/context?warehouseId=${data.command.warehouseId}&commandType=FAULT_FENCE`);
  expect(context.status()).toBe(200);
  const facts = z.object({ context: z.object({ canonicalOrderId: z.uuid() }) }).parse(await context.json());
  await page.goto(`/orders/${facts.context.canonicalOrderId}/handovers/${data.command.caseId}?warehouse=${data.command.warehouseId}`);
}
async function replies(page: Page) {
  const state = { bodies: [] as string[], firstReceipt: '' };
  await page.route('**/api/v1/handover-commands', async route => {
    state.bodies.push(route.request().postData() ?? '');
    const response = await route.fetch(); expect(response.status()).toBe(200);
    const bytes = await response.text();
    if (state.bodies.length === 1) { state.firstReceipt = bytes; await route.abort('failed'); }
    else { expect(bytes).toBe(state.firstReceipt); await route.fulfill({ response, body: bytes }); }
  });
  return state;
}
async function received(page: Page, state: { bodies: string[]; firstReceipt: string }): Promise<void> {
  await expect.poll(() => state.bodies.length).toBe(2);
  const { bodies, firstReceipt } = state;
  expect(bodies).toHaveLength(2); expect(bodies[1]).toBe(bodies[0]);
  const command = faultCommandSchema.parse(JSON.parse(bodies[0]!));
  const receipt = faultReceiptSchema.parse(JSON.parse(firstReceipt));
  await expect.poll(() => page.evaluate(id => Object.keys(localStorage).some(key => {
    if (!key.startsWith('acrylic.handover-fault.v1:')) return false;
    const saved: unknown = JSON.parse(localStorage.getItem(key) ?? 'null');
    return typeof saved === 'object' && saved !== null && 'receipt' in saved
      && typeof saved.receipt === 'object' && saved.receipt !== null && 'commandId' in saved.receipt && saved.receipt.commandId === id;
  }), command.commandId)).toBe(true);
  const identity = await page.request.get('/api/v1/me');
  const me = z.object({ data: z.object({ id: z.uuid(), permissions: z.array(z.string()) }) }).parse(await identity.json());
  expect(me.data.permissions).toContain('order:handover'); expect(me.data.permissions).not.toContain('order:read');
  expect(receipt.commandId).toBe(command.commandId); expect(receipt.actor.principalId).toBe(me.data.id);
  await layouts(page, 'receipt');
  writeFileSync(`${process.env.HANDOVER_FAULT_OUTPUT}/browser-command.json`, bodies[0]!);
  writeFileSync(`${process.env.HANDOVER_FAULT_OUTPUT}/browser-receipt.json`, firstReceipt);
  writeFileSync(`${process.env.HANDOVER_FAULT_OUTPUT}/browser-checks.json`, JSON.stringify({ sameOriginalBytes: true,
    actualHttp: true, syntheticIdentity: true, refusedThenRechecked: true, stableCustodyAndReceiptScreens: true,
    lostResponse: true, reload: true, originalKey: command.commandId }, null, 2));
}
async function refusal(page: Page): Promise<void> {
  await page.getByLabel('采集截至', { exact: true }).fill('999');
  await page.getByRole('button', { name: '确认提交故障围栏', exact: true }).click();
  await expect(page.getByText(/^原请求已拒绝：/)).toBeVisible();
  await expect(page.getByLabel('设备保管位置', { exact: true })).toBeDisabled();
  await page.getByRole('button', { name: '重新核对资料', exact: true }).click();
  await expect(page.getByRole('button', { name: '确认提交故障围栏', exact: true })).toBeDisabled();
  await fill(page);
}
test('actual supervisor form, lost reply, reload and exact original retry to verified fault detail', async ({ page }) => {
  await openCase(page);
  await page.getByRole('button', { name: '主管确认故障围栏', exact: true }).click();
  await expect(page.getByRole('button', { name: '确认提交故障围栏', exact: true })).toBeDisabled();
  await fill(page); await refusal(page);
  await testFaultAttachments(page, fixture());
  await layouts(page, 'form');
  const state = await replies(page);
  await page.getByRole('button', { name: '确认提交故障围栏', exact: true }).click();
  await expect(page.getByRole('button', { name: '重试原请求', exact: true })).toBeVisible();
  await expect(page.getByText('原结果未确认，请重试同一原请求；原键和内容已保留。', { exact: true })).toBeVisible();
  await expect.poll(() => state.firstReceipt.length).toBeGreaterThan(0);
  await expect(page.getByRole('button', { name: '重试原请求', exact: true })).toBeEnabled();
  await page.reload();
  await page.getByRole('button', { name: '核对原故障请求', exact: true }).click();
  await expect(page.getByLabel('设备保管位置', { exact: true })).toBeDisabled();
  await page.getByRole('button', { name: '重试原请求', exact: true }).click();
  await expect(page.getByRole('heading', { name: '主管故障围栏凭据', exact: true })).toBeVisible();
  await expect(page.getByText('原机没有提交本次停止回执；以下记录由独立主管确认，原设备已禁用。', { exact: true })).toBeVisible();
  await received(page, state);
});
