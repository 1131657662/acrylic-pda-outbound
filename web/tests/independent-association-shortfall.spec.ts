import { test, expect } from '@playwright/test';
import type { Page, BrowserContext } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { z } from 'zod';
const inputSchema = z.object({ username: z.string(), password: z.string(), relayId: z.uuid(), warehouseId: z.uuid(),
  targetOrderId: z.uuid(), physicalCheckId: z.uuid(), conflictDecisionId: z.uuid().optional(), evidence: z.string() });
interface Command { key: string | undefined; body: string | null; epoch: string | undefined; status: number }
function input(): z.infer<typeof inputSchema> {
  const path = process.env.INDEPENDENT_ASSOCIATION_WEB_INPUT;
  if (!path) throw new Error('Requires fresh isolated real HTTP association fixture');
  return inputSchema.parse(JSON.parse(readFileSync(path, 'utf8')));
}
async function routeActual(context: BrowserContext, commands: Command[]): Promise<void> {
  let drop = true;
  await context.route('**/api/**', async route => {
    const request = route.request(); const url = new URL(request.url()); url.port = '19436';
    if (url.pathname.endsWith('/independent-reconciliation') && url.searchParams.get('section') === 'candidates') url.searchParams.set('limit', '1');
    const response = await route.fetch({ url: url.toString(), maxRedirects: 0 });
    if (request.method() === 'POST' && url.pathname.endsWith('/associations')) {
      const headers = request.headers(); commands.push({ key: headers['idempotency-key'], body: request.postData(), epoch: headers['x-server-recovery-epoch'], status: response.status() });
      writeFileSync(`${input().evidence}/association-http-${commands.length}.json`, await response.body());
      if (drop) { drop = false; expect(response.ok()).toBe(true); await route.abort('failed'); return; }
    }
    await route.fulfill({ response });
  });
}
async function openReview(page: Page): Promise<void> {
  const f = input(); await page.goto(`/independent-reviews?relay=${f.relayId}&warehouse=${f.warehouseId}`);
  await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click();
  await expect(page.getByRole('heading', { name: '主管人工关联', exact: true })).toBeVisible();
}
async function login(page: Page): Promise<void> {
  const f = input(); await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(f.username);
  await page.getByLabel('密码', { exact: true }).fill(f.password);
  await page.getByRole('button', { name: '登录', exact: true }).click(); await expect(page).not.toHaveURL(/\/login/); await openReview(page);
}
async function prepareIntent(page: Page): Promise<void> {
  const f = input(); await page.getByRole('button', { name: '载入全部候选并人工选择', exact: true }).click();
  await expect(page.getByLabel('关联实物回执编号')).toBeVisible();
  await page.getByLabel('关联实物回执编号').fill(f.physicalCheckId);
  await page.getByLabel('人工关联说明').fill('隔离Chrome合成主管决定；保留原事实，不冒充现场');
  const target = page.locator(`.association-candidate`).filter({ has: page.locator(`input[value="${f.targetOrderId}"]`) });
  await target.locator('.el-radio').click(); await expect(target.getByRole('radio')).toBeChecked();
  await page.getByRole('button', { name: '保存人工关联依据', exact: true }).click();
  if (f.conflictDecisionId) {
    await expect(page.getByText('须显式引用覆盖全部跨任务冲突的原决定。')).toBeVisible();
    await page.getByLabel('关联冲突决定编号').fill(f.conflictDecisionId);
    await page.getByRole('button', { name: '保存人工关联依据', exact: true }).click();
  }
  await prepareApproval(page);
  for (const field of await page.getByRole('textbox', { name: /^排除候选 / }).all()) await field.fill('逐项核对：该候选不是本次实物对应正式单');
}
async function prepareApproval(page: Page): Promise<void> {
  await expect(page.getByText('原任务短装 2包；目标计划短装 2包。')).toBeVisible();
  await expect(page.getByText('须显式确认原任务与目标两种短装差额，并填写主管批准说明。')).toBeVisible();
  await expect(page.getByRole('checkbox', { name: '确认批准原任务与目标两种短装差额', exact: true })).toBeEnabled();
  await page.locator('.shortfall-approval .el-checkbox').click();
  await expect(page.getByRole('checkbox', { name: '确认批准原任务与目标两种短装差额', exact: true })).toBeChecked();
  await page.getByRole('button', { name: '保存人工关联依据', exact: true }).click();
  await expect(page.getByText('须显式确认原任务与目标两种短装差额，并填写主管批准说明。')).toBeVisible();
  await page.getByLabel('关联短装批准说明', { exact: true }).fill('隔离Chrome主管明确批准原任务2包与目标2包差额');
  await page.getByRole('button', { name: '保存人工关联依据', exact: true }).click();
  await expect(page.getByText('请填写有效实物回执、关联说明和其余每个候选的排除理由。')).toBeVisible();
}
test('explicit shortage approval restores the original two differences and intent across Chrome tabs', async ({ page, context }) => {
  test.setTimeout(120000);
  const f = input(); const commands: Command[] = []; await routeActual(context, commands); await login(page);
  await page.setViewportSize({ width: 390, height: 844 }); await prepareIntent(page);
  expect(commands.length).toBe(0);
  await page.getByRole('button', { name: '保存人工关联依据', exact: true }).click();
  await expect(page.getByText('原关联结果未明。已保存原请求，刷新后仍按原键恢复。')).toBeVisible();
  await page.screenshot({ path: `${f.evidence}/390-pending.png`, fullPage: true });
  await page.reload(); await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click();
  const other = await context.newPage(); await openReview(other);
  await Promise.all([page, other].map(tab => tab.getByRole('button', { name: '按原键恢复关联结果', exact: true }).click()));
  for (const tab of [page, other]) await expect(tab.getByText(/人工关联已登记，仍待对账；未记账、未放行。回执/)).toBeVisible();
  if (f.conflictDecisionId) await expect(page.getByText(`原冲突决定 ${f.conflictDecisionId}`, { exact: false })).toBeVisible();
  expect(commands.length).toBe(3); expect(new Set(commands.map(row => row.key)).size).toBe(1);
  expect(new Set(commands.map(row => row.body)).size).toBe(1); expect(new Set(commands.map(row => row.epoch)).size).toBe(1);
  expect(commands.every(row => row.status === 200)).toBe(true);
  const body = z.object({ shortfallApproval: z.object({ originalShortfall: z.string(), targetShortfall: z.string(), reason: z.string() }) }).parse(JSON.parse(commands[0]?.body ?? 'null'));
  expect(body.shortfallApproval).toEqual({ originalShortfall: '2', targetShortfall: '2', reason: '隔离Chrome主管明确批准原任务2包与目标2包差额' });
  await expect(page.getByText(/原短装批准：原任务 2包 · 目标 2包/)).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: `${f.evidence}/390-recovered.png`, fullPage: true });
  const saved = await page.evaluate(() => Object.entries(localStorage).find(([key]) => key.startsWith('acrylic.independent-association.v1:'))?.[1]);
  await expect(page.getByText(/原短装批准：原任务 2包 · 目标 2包/)).toBeVisible();
  if (!saved) throw new Error('Original association intent disappeared'); writeFileSync(`${f.evidence}/browser-original-command.json`, saved);
  await page.reload(); await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click();
  await expect(page.getByText(/人工关联已登记，仍待对账；未记账、未放行。回执/)).toBeVisible();
  const clean = await context.browser()?.newContext({ baseURL: process.env.OUTBOUND_WEB_URL ?? 'http://127.0.0.1:5173', viewport: { width: 390, height: 844 } });
  if (!clean) throw new Error('Chrome context unavailable');
  await routeActual(clean, []); const cleanPage = await clean.newPage(); await login(cleanPage);
  await expect(cleanPage.getByText(/人工关联已登记，仍待对账；未记账、未放行。回执/)).toBeVisible();
  await expect(cleanPage.getByText(/原短装批准：原任务 2包 · 目标 2包/)).toBeVisible();
  await expect(cleanPage.getByRole('button', { name: '载入全部候选并人工选择', exact: true })).toHaveCount(0);
  await clean.close(); await context.unrouteAll({ behavior: 'wait' });
  writeFileSync(`${f.evidence}/browser-key-recovery.json`, JSON.stringify({ commands, syntheticDecision: true, actualHttp: true, paginatedAt: 1, width: 390 }));
});
