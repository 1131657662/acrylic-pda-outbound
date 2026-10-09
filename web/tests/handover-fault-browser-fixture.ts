import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { expect, type Page, type Route } from '@playwright/test';
import { faultCommandSchema, faultReceiptSchema } from '../src/handover/fault-contracts';
import { faultDigest } from '../src/handover/pending-fault';
import { caseContextSchema } from '../src/handover/contracts';

interface Controls { user: string; permissions: string[]; csrf: Promise<void> | null; reply: ((route: Route) => Promise<void>) | null }

function reference() {
  const run = process.env.HANDOVER_FAULT_REFERENCE_RUN;
  if (!run || !/^[1-9]\d*$/.test(run)) throw new Error('Preserved fault reference run required');
  const referencePath = process.env.HANDOVER_FAULT_REFERENCE_KIND === 'FORMAL'
    ? `../.local/e2a-cross-20260925-${run}/approval-workflow/formal-fault-receipt.json`
    : `../.local/e2a-cross-20260925-${run}/temporary-handover-stop/fault-fence/fault-receipt.json`;
  const receipt = faultReceiptSchema.parse(JSON.parse(readFileSync(referencePath, 'utf8')));
  const c = receipt.caseContext;
  const facts = caseContextSchema.parse({ context: { ...c, caseVersion: receipt.expected.caseVersion, status: 'EVIDENCE_PENDING',
    stopState: 'REQUESTED', integrityState: 'UNKNOWN', originalResultState: 'ABORTED', physicalState: 'UNCHECKED',
    targetDeviceId: null, targetUserId: null, approvedBy: null },
    originalCommand: { commandType: 'REQUEST', commandId: randomUUID(), warehouseId: c.warehouseId, scopeId: c.scopeId, reason: 'Synthetic browser boundary' },
    capabilities: receipt.capabilities, expected: receipt.expected,
    fault: { entryAvailable: true, sourceDeviceId: receipt.businessDecision.sourceDeviceId, receipt: null } });
  return { facts, receipt };
}
function envelope(data: unknown) {
  return { code: 'OK', message: 'Synthetic HTTP boundary', data, requestId: randomUUID(),
    serverTime: new Date().toISOString(), serverRecoveryEpoch: '1', serviceMode: 'NORMAL' };
}
export async function faultBrowser(page: Page) {
  const { facts, receipt } = reference(); const user = randomUUID(); const otherWarehouse = randomUUID();
  const controls: Controls = { user, permissions: ['order:handover'], csrf: null, reply: null };
  const posts: string[] = [];
  const url = `/orders/${facts.context.canonicalOrderId}/handovers/${facts.context.caseId}?warehouse=${facts.context.warehouseId}`;
  const key = `acrylic.handover-fault.v1:${user}:${facts.context.warehouseId}:${facts.context.caseId}`;
  await page.context().route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/me')) {
      await route.fulfill({ json: envelope({ id: controls.user, username: 'boundary', displayName: '隔离边界人员', permissions: controls.permissions,
        warehouses: [{ id: facts.context.warehouseId, code: 'TEST', name: '测试仓' }, { id: otherWarehouse, code: 'OTHER', name: '切换仓' }] }) }); return;
    }
    if (path.endsWith('/auth/csrf')) {
      await controls.csrf; await route.fulfill({ json: envelope({ headerName: 'X-CSRF-TOKEN', token: 'synthetic-only' }) }).catch(() => {}); return;
    }
    if (path.endsWith('/auth/web/login')) { await route.fulfill({ json: envelope({ authenticated: true }) }); return; }
    if (path.endsWith('/handovers/cases')) {
      await route.fulfill({ json: { warehouseId: facts.context.warehouseId, orderId: facts.context.canonicalOrderId, orderNo: '边界验证',
        items: [{ caseId: facts.context.caseId, status: facts.context.status, fromWorkRound: 1, toWorkRound: 2,
          requestedAt: '2026-10-09T00:00:00', caseVersion: facts.context.caseVersion }], hasMore: false, nextCursor: null } }); return;
    }
    if (path.endsWith('/context')) { await route.fulfill({ json: facts }); return; }
    if (path.endsWith('/handover-commands')) {
      posts.push(route.request().postData() ?? '');
      if (controls.reply) await controls.reply(route); else await route.abort('failed'); return;
    }
    await route.fulfill({ status: 404, json: envelope(null) });
  });
  async function response(json: string) {
    const command = faultCommandSchema.parse(JSON.parse(json));
    return faultReceiptSchema.parse({ ...receipt, commandId: command.commandId, commandSha256: await faultDigest(command),
      recordedAt: new Date().toISOString(), expected: command.expected, actor: { ...receipt.actor, principalId: user },
      businessDecision: { ...receipt.businessDecision, physicalControl: command.physicalControl,
        attachments: command.attachments,
        archivedEvidence: { ...receipt.businessDecision.archivedEvidence, ...command.archivedEvidence } } });
  }
  return { controls, facts, posts, url, key, user, otherWarehouse, response, receipt };
}
export async function openFault(page: Page): Promise<void> {
  await page.getByRole('button', { name: /主管确认故障围栏|核对原故障请求/, exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
}
export async function fillFault(page: Page): Promise<void> {
  await page.getByLabel('断电收回保管时间', { exact: true }).fill('2026-10-09T00:00');
  await page.getByLabel('设备保管位置', { exact: true }).fill('隔离柜');
  await page.getByLabel('货物位置', { exact: true }).fill('隔离货区');
  for (const label of ['归档集合编号', '归档验证编号']) await page.getByLabel(label, { exact: true }).fill(randomUUID());
  await page.getByLabel('归档原文摘要', { exact: true }).fill('a'.repeat(64));
  for (const label of ['采集截至', '审计截至', '扫描截至']) await page.getByLabel(label, { exact: true }).fill('1');
  await page.getByRole('checkbox').check();
}
export async function stored(page: Page, key: string): Promise<string | null> {
  return page.evaluate(name => localStorage.getItem(name), key);
}
