import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import { openFault, fillFault, stored } from './handover-fault-browser-fixture';
import { attachmentBrowser, file, identityChanged } from './support/handover-attachment-boundary';

test.skip(process.env.HANDOVER_FAULT_BROWSER_BOUNDARY !== 'true', 'Explicit synthetic attachment boundary run required');

test('invalid file capacity permits corrected selection without reload', async ({ page }) => {
  const f = await attachmentBrowser(page); await f.open(); f.uploads.reply = f.success;
  await page.getByLabel('故障凭据附件', { exact: true }).setInputFiles(file('empty.bin', ''));
  await expect(page.getByText('单个附件须为 1 字节至 10MiB。', { exact: true })).toBeVisible();
  await expect(page.getByLabel('故障凭据附件', { exact: true })).toBeEnabled();
  await page.getByLabel('故障凭据附件', { exact: true }).setInputFiles(file());
  await expect(page.getByText(/原件已保存/)).toBeVisible(); expect(f.calls).toHaveLength(1);
});

test('failed durable upload key write prevents sending any bytes', async ({ page }) => {
  const f = await attachmentBrowser(page); await f.open();
  await page.evaluate(() => { Storage.prototype.setItem = () => { throw new Error('Synthetic quota'); }; });
  await page.getByLabel('故障凭据附件', { exact: true }).setInputFiles(file());
  await expect(page.getByText(/无法保存原附件上传键/)).toBeVisible();
  expect(f.calls).toEqual([]); expect(await stored(page, f.attachmentKey)).toBeNull();
});

test('lost upload reply reloads and reselects the same original key and bytes', async ({ page }) => {
  const f = await attachmentBrowser(page); await f.open();
  await page.getByLabel('故障凭据附件', { exact: true }).setInputFiles(file());
  await expect.poll(() => f.calls.length).toBe(1); const original = await stored(page, f.attachmentKey);
  await expect(page.getByText(/请重选此原文件继续上传/)).toBeVisible();
  await page.reload(); await openFault(page); f.uploads.reply = f.success;
  await page.getByLabel('故障凭据附件', { exact: true }).setInputFiles(file());
  await expect(page.getByText(/原件已保存/)).toBeVisible();
  expect(f.calls[1]).toEqual(f.calls[0]); expect(original).not.toBeNull();
  expect(JSON.parse((await stored(page, f.attachmentKey))!).uploads[0].key).toBe(JSON.parse(original!).uploads[0].key);
});

test('corrupt upload state preserves bytes and prevents upload and fault POST', async ({ page }) => {
  const f = await attachmentBrowser(page); await page.goto(f.url);
  await page.evaluate(key => localStorage.setItem(key, '{broken'), f.attachmentKey); await openFault(page);
  await expect(page.getByText(/无法读取原附件上传键/)).toBeVisible();
  await page.getByLabel('故障凭据附件', { exact: true }).setInputFiles(file());
  await expect(page.getByText(/无法读取原附件上传键/)).toBeVisible();
  await fillFault(page); await expect(page.getByRole('button', { name: '确认提交故障围栏', exact: true })).toBeDisabled();
  expect(f.calls).toEqual([]); expect(f.posts).toEqual([]); expect(await stored(page, f.attachmentKey)).toBe('{broken');
});

test('identity switch during upload CSRF leaves the original key without a PUT', async ({ page }) => {
  const f = await attachmentBrowser(page); await f.open();
  let release = () => {}; f.controls.csrf = new Promise<void>(resolve => { release = resolve; });
  await page.getByLabel('故障凭据附件', { exact: true }).setInputFiles(file());
  await expect.poll(() => stored(page, f.attachmentKey)).not.toBeNull(); const original = await stored(page, f.attachmentKey);
  f.controls.user = randomUUID(); await identityChanged(page); await expect(page.getByRole('dialog')).toHaveCount(0); release();
  await expect(page.getByRole('button', { name: '主管确认故障围栏', exact: true })).toBeVisible();
  expect(f.calls).toEqual([]); expect(await stored(page, f.attachmentKey)).toBe(original);
});

test('late upload response after identity switch cannot save either owner', async ({ page }) => {
  const f = await attachmentBrowser(page); await f.open();
  let release = () => {}; const held = new Promise<void>(resolve => { release = resolve; });
  let delivered = () => {}; const done = new Promise<void>(resolve => { delivered = resolve; });
  f.uploads.reply = async route => { await held; await f.success(route).catch(() => {}); delivered(); };
  await page.getByLabel('故障凭据附件', { exact: true }).setInputFiles(file());
  await expect.poll(() => f.calls.length).toBe(1); const original = await stored(page, f.attachmentKey);
  f.controls.user = randomUUID(); await identityChanged(page); await expect(page.getByRole('dialog')).toHaveCount(0);
  release(); await done;
  expect(await stored(page, f.attachmentKey)).toBe(original);
  expect(await stored(page, f.attachmentKey.replace(f.user, f.controls.user))).toBeNull();
});

test('another tab cannot omit an upload and reopening reloads every original reference', async ({ page, context }) => {
  const f = await attachmentBrowser(page); const other = await context.newPage(); await f.open();
  await other.goto(f.url); await openFault(other); await fillFault(other); f.uploads.reply = f.success;
  await page.getByLabel('故障凭据附件', { exact: true }).setInputFiles(file());
  await expect(page.getByText(/原件已保存/)).toBeVisible();
  await other.getByRole('button', { name: '确认提交故障围栏', exact: true }).click();
  await expect(other.getByRole('dialog').getByText(/原附件上传状态已变化/)).toBeVisible(); expect(f.posts).toEqual([]);
  await other.getByRole('button', { name: '返回核对', exact: true }).click(); await openFault(other);
  await expect(other.getByText(/原件已保存/)).toBeVisible();
  await other.getByRole('button', { name: '确认提交故障围栏', exact: true }).click();
  await expect.poll(() => f.posts.length).toBe(1);
  expect(JSON.parse(f.posts[0]!).attachments).toEqual(JSON.parse((await stored(page, f.attachmentKey))!).uploads.map((row: { reference: unknown }) => row.reference));
});

test('all five verified attachment links remain reachable in narrow scroll layouts', async ({ page }) => {
  const f = await attachmentBrowser(page); f.facts.fault.entryAvailable = false; f.facts.fault.receipt = f.receipt;
  f.facts.context.stopState = 'FAULT_FENCED'; await page.goto(f.url);
  const attachments = f.receipt.businessDecision.attachments; expect(attachments).toHaveLength(5);
  const output = process.env.HANDOVER_FAULT_BOUNDARY_OUTPUT!; mkdirSync(output, { recursive: true });
  for (const [width, height] of [[390, 844], [720, 450], [1440, 900]]) {
    await page.setViewportSize({ width, height });
    for (const [index, reference] of attachments!.entries()) {
      const link = page.getByRole('link', { name: `下载原附件 ${index + 1}`, exact: true });
      await link.scrollIntoViewIfNeeded(); await expect(link).toBeInViewport();
      await expect(link).toHaveAttribute('href', `/api/v1/handovers/${f.receipt.businessDecision.caseId}/attachments/${reference.attachmentId}?warehouseId=${f.receipt.actor.warehouseId}`);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      await page.screenshot({ path: `${output}/attachments-${width}-${index + 1}.png` });
    }
  }
  expect(f.posts).toEqual([]);
});

test('reopened form exposes all saved original filenames and hashes above the action footer', async ({ page }) => {
  const f = await attachmentBrowser(page); await page.goto(f.url);
  const uploads = f.receipt.businessDecision.attachments!.map(reference => ({ key: randomUUID(),
    name: `attachment-${reference.attachmentId}.bin`, sha: reference.evidenceSha256,
    size: Number(reference.sizeBytes), epoch: '1', reference }));
  await page.evaluate(({ key, uploads, userId, warehouseId, caseId }) => localStorage.setItem(key,
    JSON.stringify({ version: 1, userId, warehouseId, caseId, uploads })),
  { key: f.attachmentKey, uploads, userId: f.user, warehouseId: f.facts.context.warehouseId, caseId: f.facts.context.caseId });
  await openFault(page);
  const rows = page.locator('.fault-attachments li'); await expect(rows).toHaveCount(5);
  for (const [width, height] of [[390, 844], [720, 450], [1440, 900]]) {
    await page.setViewportSize({ width, height });
    for (const [index, upload] of uploads.entries()) {
      const row = rows.nth(index); await row.scrollIntoViewIfNeeded(); await expect(row).toBeInViewport();
      await expect(row).toContainText(upload.name); await expect(row).toContainText(upload.sha);
      await expect(page.getByRole('button', { name: '返回核对', exact: true })).toBeInViewport({ ratio: 1 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      await page.screenshot({ path: `${process.env.HANDOVER_FAULT_BOUNDARY_OUTPUT}/form-originals-${width}-${index + 1}.png` });
    }
  }
  expect(f.calls).toEqual([]); expect(f.posts).toEqual([]);
});
