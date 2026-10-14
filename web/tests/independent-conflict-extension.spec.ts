import { test, expect } from '@playwright/test';
import type { Page, BrowserContext } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { z } from 'zod';
const inputSchema = z.object({ username: z.string(), password: z.string(), relayId: z.uuid(), warehouseId: z.uuid(),
  body: z.object({ physicalCheckId: z.uuid(), predecessorDecisionId: z.uuid().optional() }),
  peers: z.array(z.object({ relayId: z.uuid(), physicalCheckId: z.uuid(), taskId: z.uuid() })), evidence: z.string() });
type Input = z.infer<typeof inputSchema>;
interface Command { key: string | undefined; body: string | null; epoch: string | undefined; status: number }
function input(name = 'initial-private.json'): Input {
  const directory = process.env.INDEPENDENT_EXTENSION_WEB_INPUT;
  if (!directory) throw new Error('Requires fresh isolated actual HTTP fixture');
  return inputSchema.parse(JSON.parse(readFileSync(`${directory}/${name}`, 'utf8')));
}
async function routeActual(context: BrowserContext, commands: Command[]): Promise<void> {
  let drop = true;
  await context.route('**/api/**', async route => {
    const request = route.request(); const url = new URL(request.url()); url.port = '19436';
    if (url.pathname.endsWith('/independent-reconciliation') && url.searchParams.get('section') === 'peers') url.searchParams.set('limit', '1');
    const response = await route.fetch({ url: url.toString(), maxRedirects: 0 });
    if (request.method() === 'POST' && url.pathname.endsWith('/conflict-decisions')) {
      const headers = request.headers(); commands.push({ key: headers['idempotency-key'], body: request.postData(), epoch: headers['x-server-recovery-epoch'], status: response.status() });
      writeFileSync(`${input().evidence}/actual-http-${commands.length}.json`, await response.body());
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
async function fillIntent(page: Page, f: Input): Promise<void> {
  await page.getByLabel('冲突选定方实物回执编号').fill(f.body.physicalCheckId);
  await page.getByLabel('冲突决定说明').fill('隔离Chrome增量合成实物决定；全部原件保留，不冒充现场');
  for (const peer of f.peers) {
    const article = page.locator('.conflict-peer').filter({ has: page.getByRole('combobox', { name: `冲突原件 ${peer.taskId}`, exact: true }) });
    await article.getByText('显式选择该任务原件', { exact: true }).click();
    await page.getByRole('option', { name: peer.relayId, exact: true }).click();
    await page.getByLabel(`冲突实物回执 ${peer.taskId}`, { exact: true }).fill(peer.physicalCheckId);
  }
}
async function assertResult(page: Page): Promise<void> {
  await expect(page.getByText(/冲突决定已登记，仍待对账；未关联、未记账、未放行。决定/)).toBeVisible();
}
async function saveCommand(page: Page, name: string): Promise<string> {
  const saved = await page.evaluate(() => Object.entries(localStorage).find(([key]) => key.startsWith('acrylic.independent-conflict.v1:'))?.[1]);
  if (!saved) throw new Error('Original intent disappeared'); writeFileSync(`${input().evidence}/${name}`, saved); return saved;
}
async function firstDecision(page: Page): Promise<void> {
  await page.getByRole('button', { name: '载入全部冲突任务并核对', exact: true }).click();
  await fillIntent(page, input()); await page.getByRole('button', { name: '保存完整冲突决定', exact: true }).click();
  await assertResult(page); await saveCommand(page, 'first-command.json');
}
async function append(page: Page): Promise<void> {
  execFileSync('C:/Python314/python.exe', ['D:/acrylic-pda-outbound/.local/goal-20261014/e06-extension-web-1536.py', 'extend01']);
  await page.reload(); await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click(); await assertResult(page);
  await page.getByRole('button', { name: '依据变化，追加完整核对', exact: true }).click();
  await expect(page.getByText('已保留历史原请求 1 条及其原键、说明和回执。')).toBeVisible();
  await fillIntent(page, input('extension-private.json'));
}
async function restoreAcrossTabs(page: Page, context: BrowserContext): Promise<void> {
  await page.getByRole('button', { name: '保存完整冲突决定', exact: true }).click();
  await expect(page.getByText('原冲突结果未明。已保存原请求，刷新后仍按原键恢复。')).toBeVisible();
  await page.screenshot({ path: `${input().evidence}/390-extension-pending.png`, fullPage: true });
  await page.reload(); await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click();
  const other = await context.newPage(); await openReview(other);
  await Promise.all([page, other].map(tab => tab.getByRole('button', { name: '按原键恢复冲突结果', exact: true }).click()));
  for (const tab of [page, other]) await assertResult(tab);
  await other.close();
}
async function inspectHistory(page: Page): Promise<void> {
  await page.locator('.conflict-panel summary').click();
  await expect(page.getByText(/原键 .* · 决定/)).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: `${input().evidence}/390-extension-history.png`, fullPage: true });
  await saveCommand(page, 'final-command.json');
  await page.reload(); await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click(); await assertResult(page);
}
function checkCommands(commands: Command[]): void {
  expect(commands.length).toBe(4); expect(commands.every(row => row.status === 200)).toBe(true);
  const [first, ...extensions] = commands; if (!first) throw new Error('Original request missing');
  expect(new Set(extensions.map(row => row.key)).size).toBe(1);
  expect(new Set(extensions.map(row => row.body)).size).toBe(1);
  expect(new Set(extensions.map(row => row.epoch)).size).toBe(1);
  expect(extensions[0]?.key).not.toBe(first.key);
  writeFileSync(`${input().evidence}/browser-recovery.json`, JSON.stringify({ commands, width: 390, actualHttp: true, syntheticFacts: true }));
}
test('Chrome appends changed full context while retaining history and exact pending key across tabs', async ({ page, context }) => {
  test.setTimeout(240000); await page.setViewportSize({ width: 390, height: 844 });
  const firstCommands: Command[] = [];
  // The first decision succeeds normally; only the append response is dropped.
  await context.route('**/api/**', async route => {
    const request = route.request(); const url = new URL(request.url()); url.port = '19436';
    const response = await route.fetch({ url: url.toString() });
    if (request.method() === 'POST' && url.pathname.endsWith('/conflict-decisions')) {
      const headers = request.headers(); firstCommands.push({ key: headers['idempotency-key'], body: request.postData(), epoch: headers['x-server-recovery-epoch'], status: response.status() });
      writeFileSync(`${input().evidence}/actual-http-1.json`, await response.body());
    }
    await route.fulfill({ response });
  });
  await login(page); await firstDecision(page); await context.unrouteAll({ behavior: 'wait' });
  await routeActual(context, firstCommands); await append(page); await restoreAcrossTabs(page, context);
  await inspectHistory(page); checkCommands(firstCommands); await context.unrouteAll({ behavior: 'wait' });
});
