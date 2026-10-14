import { test, expect } from '@playwright/test';
import type { Page, BrowserContext } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { z } from 'zod';
const inputSchema = z.object({ username: z.string(), password: z.string(), relayId: z.uuid(), warehouseId: z.uuid(),
  physicalCheckId: z.uuid(), peerRelayId: z.uuid(), peerTaskId: z.uuid(), peerCheckId: z.uuid(), evidence: z.string() });
interface Command { key: string | undefined; body: string | null; epoch: string | undefined; status: number }
function input(): z.infer<typeof inputSchema> {
  const path = process.env.INDEPENDENT_CONFLICT_WEB_INPUT;
  if (!path) throw new Error('Requires fresh isolated real HTTP conflict fixture');
  return inputSchema.parse(JSON.parse(readFileSync(path, 'utf8')));
}
async function routeActual(context: BrowserContext, commands: Command[]): Promise<void> {
  let drop = true;
  await context.route('**/api/**', async route => {
    const request = route.request(); const url = new URL(request.url()); url.port = '19436';
    if (url.pathname.endsWith('/independent-reconciliation') && url.searchParams.get('section') === 'peers') url.searchParams.set('limit', '1');
    const response = await route.fetch({ url: url.toString(), maxRedirects: 0 });
    if (request.method() === 'POST' && url.pathname.endsWith('/conflict-decisions')) {
      const headers = request.headers(); commands.push({ key: headers['idempotency-key'], body: request.postData(), epoch: headers['x-server-recovery-epoch'], status: response.status() });
      writeFileSync(`${input().evidence}/conflict-http-${commands.length}.json`, await response.body());
      if (drop) { drop = false; expect(response.ok()).toBe(true); await route.abort('failed'); return; }
    }
    await route.fulfill({ response });
  });
}
async function openReview(page: Page): Promise<void> {
  const f = input(); await page.goto(`/independent-reviews?relay=${f.relayId}&warehouse=${f.warehouseId}`);
  await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click();
  await expect(page.getByRole('heading', { name: '跨任务实物冲突决定', exact: true })).toBeVisible();
}
async function login(page: Page): Promise<void> {
  const f = input(); await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(f.username);
  await page.getByLabel('密码', { exact: true }).fill(f.password);
  await page.getByRole('button', { name: '登录', exact: true }).click(); await expect(page).not.toHaveURL(/\/login/); await openReview(page);
}
async function prepareIntent(page: Page): Promise<void> {
  const f = input(); await page.getByRole('button', { name: '载入全部冲突任务并核对', exact: true }).click();
  await expect(page.getByLabel('冲突选定方实物回执编号')).toBeVisible();
  await page.getByLabel('冲突选定方实物回执编号').fill(f.physicalCheckId);
  await page.getByLabel('冲突决定说明').fill('隔离Chrome合成实物决定；原事实保留，不冒充现场');
  await page.getByRole('button', { name: '保存完整冲突决定', exact: true }).click();
  await expect(page.getByText('须选择每个冲突任务的原件并填写全部实物回执与决定说明。')).toBeVisible();
  const peer = page.locator('.conflict-peer').filter({ has: page.getByRole('combobox', { name: `冲突原件 ${f.peerTaskId}`, exact: true }) });
  await peer.getByText('显式选择该任务原件', { exact: true }).click();
  await page.getByRole('option', { name: f.peerRelayId, exact: true }).click();
  await page.getByLabel(`冲突实物回执 ${f.peerTaskId}`, { exact: true }).fill(f.peerCheckId);
}
async function assertResult(page: Page): Promise<void> {
  await expect(page.getByText(/冲突决定已登记，仍待对账；未关联、未记账、未放行。决定/)).toBeVisible();
}
async function readInCleanBrowser(context: BrowserContext, saved: string): Promise<void> {
  const receipt = z.object({ command: z.object({ receipt: z.object({ decisionId: z.uuid() }) }) }).parse(JSON.parse(saved));
  const clean = await context.browser()?.newContext({ baseURL: process.env.OUTBOUND_WEB_URL ?? 'http://127.0.0.1:5173', viewport: { width: 390, height: 844 } });
  if (!clean) throw new Error('Chrome context unavailable');
  await routeActual(clean, []); const cleanPage = await clean.newPage(); await login(cleanPage);
  await cleanPage.getByLabel('读取原冲突决定编号').fill(receipt.command.receipt.decisionId);
  await cleanPage.getByRole('button', { name: '按编号读取原冲突决定', exact: true }).click(); await assertResult(cleanPage);
  await expect(cleanPage.getByRole('button', { name: '载入全部冲突任务并核对', exact: true })).toHaveCount(0);
  await clean.close();
}
test('lost conflict response restores exact original intent across Chrome tabs at 390px', async ({ page, context }) => {
  test.setTimeout(120000);
  const f = input(); const commands: Command[] = []; await routeActual(context, commands); await login(page);
  await page.setViewportSize({ width: 390, height: 844 }); await prepareIntent(page); expect(commands.length).toBe(0);
  await page.getByRole('button', { name: '保存完整冲突决定', exact: true }).click();
  await expect(page.getByText('原冲突结果未明。已保存原请求，刷新后仍按原键恢复。')).toBeVisible();
  await page.screenshot({ path: `${f.evidence}/390-pending.png`, fullPage: true });
  await page.reload(); await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click();
  const other = await context.newPage(); await openReview(other);
  await Promise.all([page, other].map(tab => tab.getByRole('button', { name: '按原键恢复冲突结果', exact: true }).click()));
  for (const tab of [page, other]) await assertResult(tab);
  expect(commands.length).toBe(3); expect(new Set(commands.map(row => row.key)).size).toBe(1);
  expect(new Set(commands.map(row => row.body)).size).toBe(1); expect(new Set(commands.map(row => row.epoch)).size).toBe(1);
  expect(commands.every(row => row.status === 200)).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: `${f.evidence}/390-recovered.png`, fullPage: true });
  const saved = await page.evaluate(() => Object.entries(localStorage).find(([key]) => key.startsWith('acrylic.independent-conflict.v1:'))?.[1]);
  if (!saved) throw new Error('Original conflict intent disappeared'); writeFileSync(`${f.evidence}/browser-original-command.json`, saved);
  await page.reload(); await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click(); await assertResult(page);
  await readInCleanBrowser(context, saved); await context.unrouteAll({ behavior: 'wait' });
  writeFileSync(`${f.evidence}/browser-key-recovery.json`, JSON.stringify({ commands, syntheticDecision: true, actualHttp: true, paginatedAt: 1, width: 390 }));
});
