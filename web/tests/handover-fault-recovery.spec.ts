import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { faultBrowser, openFault, fillFault, stored } from './handover-fault-browser-fixture';

test.skip(process.env.HANDOVER_FAULT_BROWSER_BOUNDARY !== 'true', 'Explicit synthetic browser boundary run required');

test('pure supervisor login redirect reaches handover while ordinary orders stay denied', async ({ page }) => {
  const f = await faultBrowser(page);
  await page.goto(`/login?redirect=${encodeURIComponent(f.url)}`);
  await page.getByLabel('账号', { exact: true }).fill('synthetic');
  await page.getByLabel('密码', { exact: true }).fill('synthetic');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/handovers/${f.facts.context.caseId}`));
  await expect(page.getByRole('button', { name: '主管确认故障围栏', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '返回订单', exact: true })).toHaveCount(0);
  await page.goto(`/orders/${f.facts.context.canonicalOrderId}`);
  await expect(page.getByText('没有此页面的访问权限', { exact: true })).toBeVisible();
  expect(f.posts).toEqual([]);
});

test('resume-only permission reads handover without fault command', async ({ page }) => {
  const f = await faultBrowser(page); f.controls.permissions = ['order:resume']; f.facts.fault.entryAvailable = false;
  await page.goto(f.url);
  await expect(page.getByRole('region', { name: '交接详情', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '主管确认故障围栏', exact: true })).toHaveCount(0);
  expect(f.posts).toEqual([]);
});

test('storage write failure blocks POST and allows exact saved request after recovery', async ({ page }) => {
  const f = await faultBrowser(page); await page.goto(f.url); await openFault(page); await fillFault(page);
  await page.evaluate(() => { Storage.prototype.setItem = () => { throw new Error('Synthetic quota refusal'); }; });
  await page.getByRole('button', { name: '确认提交故障围栏', exact: true }).click();
  await expect(page.getByRole('dialog').getByText(/无法保存原故障请求或回执/)).toBeVisible();
  expect(f.posts).toEqual([]); expect(await stored(page, f.key)).toBeNull();
  await page.reload(); await openFault(page); await fillFault(page);
  await page.getByRole('button', { name: '确认提交故障围栏', exact: true }).click();
  await expect(page.getByRole('button', { name: '重试原请求', exact: true })).toBeEnabled();
  await expect.poll(() => f.posts.length).toBe(1);
  const saved = await stored(page, f.key); expect(saved).not.toBeNull();
  await page.reload(); await openFault(page);
  await page.getByRole('button', { name: '重试原请求', exact: true }).click();
  await expect.poll(() => f.posts.length).toBe(2);
  expect(f.posts[1]).toBe(f.posts[0]); expect(await stored(page, f.key)).toBe(saved);
});

test('corrupt saved request refuses a new key and keeps original bytes', async ({ page }) => {
  const f = await faultBrowser(page); await page.goto(f.url);
  await page.evaluate(name => localStorage.setItem(name, '{broken'), f.key); await page.reload();
  await expect(page.getByText(/无法读取原故障请求/)).toBeVisible();
  await expect(page.getByRole('button', { name: '主管确认故障围栏', exact: true })).toBeDisabled();
  expect(f.posts).toEqual([]); expect(await stored(page, f.key)).toBe('{broken');
});

test('unknown server outcome cannot be cleared or replaced with a fresh key', async ({ page }) => {
  const f = await faultBrowser(page);
  f.controls.reply = async route => { await route.fulfill({ status: 409, json: {
    code: 'HANDOVER_RESULT_UNKNOWN', message: '原结果未核明', data: null, requestId: randomUUID(),
    serverTime: new Date().toISOString(), serverRecoveryEpoch: '1', serviceMode: 'NORMAL' } }); };
  await page.goto(f.url); await openFault(page); await fillFault(page);
  await page.getByRole('button', { name: '确认提交故障围栏', exact: true }).click();
  await expect(page.getByRole('dialog').getByText(/原结果未核明/)).toBeVisible();
  await expect(page.getByRole('button', { name: '重新核对资料', exact: true })).toHaveCount(0);
  const saved = await stored(page, f.key); expect(saved).not.toBeNull();
  await page.reload(); await openFault(page);
  await expect(page.getByRole('button', { name: '重新核对资料', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '重试原请求', exact: true }).click();
  await expect.poll(() => f.posts.length).toBe(2);
  expect(f.posts[1]).toBe(f.posts[0]); expect(await stored(page, f.key)).toBe(saved);
});

test('older saved unknown refusal remains unresolved after reload', async ({ page }) => {
  const f = await faultBrowser(page); await page.goto(f.url); await openFault(page); await fillFault(page);
  await page.getByRole('button', { name: '确认提交故障围栏', exact: true }).click();
  await expect(page.getByRole('button', { name: '重试原请求', exact: true })).toBeEnabled();
  await page.evaluate(name => {
    const value = JSON.parse(localStorage.getItem(name) ?? 'null');
    value.refusal = { code: 'HANDOVER_RESULT_UNKNOWN', message: 'Old saved unknown result' };
    localStorage.setItem(name, JSON.stringify(value));
  }, f.key);
  const saved = await stored(page, f.key); await page.reload(); await openFault(page);
  await expect(page.getByRole('button', { name: '重新核对资料', exact: true })).toHaveCount(0);
  await expect(page.getByLabel('设备保管位置', { exact: true })).toBeDisabled();
  expect(await stored(page, f.key)).toBe(saved);
});

test('two tabs serialize different form inputs into the same durable original request', async ({ page, context }) => {
  const f = await faultBrowser(page); const other = await context.newPage();
  for (const tab of [page, other]) { await tab.goto(f.url); await openFault(tab); await fillFault(tab); }
  let release = () => {}; const held = new Promise<void>(resolve => { release = resolve; });
  f.controls.reply = async route => { if (f.posts.length === 1) await held; await route.abort('failed'); };
  await page.getByRole('button', { name: '确认提交故障围栏', exact: true }).click();
  await expect.poll(() => f.posts.length).toBe(1);
  await other.getByRole('button', { name: '确认提交故障围栏', exact: true }).click();
  await expect(other.getByRole('button', { name: '确认提交故障围栏', exact: true })).toBeDisabled();
  expect(f.posts).toHaveLength(1); release();
  await expect.poll(() => f.posts.length).toBe(2);
  await expect(other.getByRole('button', { name: '重试原请求', exact: true })).toBeEnabled();
  expect(f.posts[1]).toBe(f.posts[0]);
  expect(await stored(page, f.key)).toBe(await stored(other, f.key));
});

test('identity change during CSRF blocks POST and preserves former owner request', async ({ page }) => {
  const f = await faultBrowser(page); await page.goto(f.url); await openFault(page); await fillFault(page);
  let release = () => {}; f.controls.csrf = new Promise<void>(resolve => { release = resolve; });
  await page.getByRole('button', { name: '确认提交故障围栏', exact: true }).click();
  await expect.poll(() => stored(page, f.key)).not.toBeNull(); const saved = await stored(page, f.key);
  f.controls.user = randomUUID();
  await page.evaluate(async () => { const { useSessionStore } = await import('/src/session.ts'); await useSessionStore().load(); });
  await expect(page.getByRole('dialog')).toHaveCount(0); release();
  await expect(page.getByRole('button', { name: '主管确认故障围栏', exact: true })).toBeVisible();
  expect(f.posts).toEqual([]); expect(await stored(page, f.key)).toBe(saved);
});

test('late reply after identity switch cannot save into either owner or show success', async ({ page }) => {
  const f = await faultBrowser(page); await page.goto(f.url); await openFault(page); await fillFault(page);
  let release = () => {}; const held = new Promise<void>(resolve => { release = resolve; });
  let delivered = () => {}; const replied = new Promise<void>(resolve => { delivered = resolve; });
  f.controls.reply = async route => {
    const json = await f.response(route.request().postData() ?? '');
    await held; await route.fulfill({ json }).catch(() => {}); delivered();
  };
  await page.getByRole('button', { name: '确认提交故障围栏', exact: true }).click();
  await expect.poll(() => f.posts.length).toBe(1); const saved = await stored(page, f.key);
  f.controls.user = randomUUID();
  await page.evaluate(async () => { const { useSessionStore } = await import('/src/session.ts'); await useSessionStore().load(); });
  await expect(page.getByRole('dialog')).toHaveCount(0); release(); await replied;
  await expect(page.getByRole('heading', { name: '主管故障围栏凭据', exact: true })).toHaveCount(0);
  expect(await stored(page, f.key)).toBe(saved);
  expect(await stored(page, f.key.replace(f.user, f.controls.user))).toBeNull();
});

test('mismatched verified fault context does not show another case evidence', async ({ page }) => {
  const f = await faultBrowser(page); f.facts.context.caseId = randomUUID(); await page.goto(f.url);
  await expect(page.getByText('交接详情待核对', { exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: '交接详情', exact: true })).toHaveCount(0);
  expect(f.posts).toEqual([]);
});
