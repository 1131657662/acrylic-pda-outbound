import { expect, type Page } from '@playwright/test';
import { z } from 'zod';
import { createHash } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { faultCommandSchema } from '../../src/handover/fault-contracts';

export const attachmentFixtureSchema = z.object({ userId: z.uuid(), uploads: z.array(z.object({
  key: z.uuid(), name: z.string(), sha: z.string(), size: z.number(), epoch: z.string(), reference: z.null() })) });
interface Fixture { command: z.infer<typeof faultCommandSchema>; attachmentFixture?: z.infer<typeof attachmentFixtureSchema>; attachmentDirectory?: string }

/** Browser selects original files, loses a processed upload response, reloads, then reuses the exact durable upload key. */
export async function testFaultAttachments(page: Page, fixture: Fixture): Promise<void> {
  if (!fixture.attachmentFixture || !fixture.attachmentDirectory) return;
  const { command, attachmentFixture: data } = fixture;
  const key = `acrylic.handover-fault-attachments.v1:${data.userId}:${command.warehouseId}:${command.caseId}`;
  await page.evaluate(({ key, data, command }) => localStorage.setItem(key, JSON.stringify({ version: 1,
    userId: data.userId, warehouseId: command.warehouseId, caseId: command.caseId, uploads: data.uploads })), { key, data, command });
  const calls: { key: string; sha: string; bodySha: string; id: string }[] = [];
  await page.route('**/api/v1/handovers/*/attachments/*?warehouseId=*', async route => {
    if (route.request().method() !== 'PUT') { await route.continue(); return; }
    const response = await route.fetch(); expect(response.status()).toBe(200);
    const metadata = z.object({ data: z.object({ attachmentId: z.uuid() }) }).parse(await response.json());
    calls.push({ key: new URL(route.request().url()).pathname, sha: route.request().headers()['x-content-sha256'] ?? '',
      bodySha: createHash('sha256').update(route.request().postDataBuffer()!).digest('hex'), id: metadata.data.attachmentId });
    if (calls.length === 1) await route.abort('failed'); else await route.fulfill({ response });
  });
  // Reopen so the mounted form restores the saved keys instead of creating new uploads.
  await page.reload(); await page.getByRole('button', { name: '主管确认故障围栏', exact: true }).click();
  const paths = data.uploads.map(row => join(fixture.attachmentDirectory!, row.name));
  await page.getByLabel('故障凭据附件', { exact: true }).setInputFiles(paths);
  await expect.poll(() => calls.length).toBe(1);
  await expect(page.getByText('连接失败，请检查网络后重试。原提交不会自动重复发送。', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '确认提交故障围栏', exact: true })).toBeDisabled();
  await page.reload(); await page.getByRole('button', { name: '主管确认故障围栏', exact: true }).click();
  await page.getByLabel('故障凭据附件', { exact: true }).setInputFiles(paths);
  await expect.poll(() => calls.length).toBe(6);
  await expect(page.locator('.fault-attachments li').filter({ hasText: '原件已保存' })).toHaveCount(5);
  expect(calls[1]).toEqual(calls[0]);
  for (const call of calls) expect(call.bodySha).toBe(call.sha);
  for (const upload of data.uploads) expect(calls.some(call => call.key.endsWith('/' + upload.key))).toBe(true);
  writeFileSync(`${process.env.HANDOVER_FAULT_OUTPUT}/attachment-upload-calls.json`, JSON.stringify(calls, null, 2));
  await fillRestoredForm(page, fixture);
}

async function fillRestoredForm(page: Page, fixture: Fixture): Promise<void> {
  const body = fixture.command; const date = new Date(body.physicalControl.controlledAt);
  await page.getByLabel('断电收回保管时间', { exact: true }).fill(new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 23));
  await page.getByLabel('设备保管位置', { exact: true }).fill(body.physicalControl.deviceLocation);
  await page.getByLabel('货物位置', { exact: true }).fill(body.physicalControl.goodsLocation);
  for (const [label, field] of [['归档集合编号', 'sourceEvidenceSetId'], ['归档验证编号', 'verificationId'],
    ['归档原文摘要', 'setSha256'], ['采集截至', 'captureHighWatermark'], ['审计截至', 'auditThroughSeq'], ['扫描截至', 'orderScanHighWatermark']] as const)
    await page.getByLabel(label, { exact: true }).fill(body.archivedEvidence[field]);
  await page.getByRole('checkbox', { name: '确认原机已断电收回保管、货物位置已核对，且没有未核明的输入' }).check();
}
