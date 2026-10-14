import { test, expect, type Page, type BrowserContext } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { z } from 'zod';
const inputSchema = z.object({ username: z.string(), password: z.string(), relayId: z.uuid(), memberRelayId: z.uuid(), warehouseId: z.uuid(),
  targetOrderId: z.uuid(), physicalCheckId: z.uuid(), memberPhysicalCheckId: z.uuid(), originalShortfall: z.string(), targetShortfall: z.string(), evidence: z.string() });
interface Command { key: string | undefined; body: string | null; epoch: string | undefined; status: number }
function input(): z.infer<typeof inputSchema> {
  const path = process.env.INDEPENDENT_ASSOCIATION_WEB_INPUT;
  if (!path) throw new Error('Requires fresh isolated real HTTP multitask fixture');
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
async function openReview(page: Page, relayId = input().relayId): Promise<void> {
  const f = input(); await page.goto(`/independent-reviews?relay=${relayId}&warehouse=${f.warehouseId}`);
  await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click();
  await expect(page.getByRole('heading', { name: '主管人工关联', exact: true })).toBeVisible();
}
async function login(page: Page): Promise<void> {
  const f = input(); await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(f.username);
  await page.getByLabel('密码', { exact: true }).fill(f.password);
  await page.getByRole('button', { name: '登录', exact: true }).click(); await expect(page).not.toHaveURL(/\/login/); await openReview(page);
}
async function prepareMembers(page: Page): Promise<void> {
  const f = input(); await page.getByRole('button', { name: '载入全部候选并人工选择', exact: true }).click();
  await page.getByLabel('关联实物回执编号', { exact: true }).fill(f.physicalCheckId);
  await page.getByLabel('人工关联说明', { exact: true }).fill('隔离Chrome显式两任务决定；原件分别保留，不冒充现场');
  await page.getByLabel('额外任务原件编号', { exact: true }).fill(f.relayId);
  await page.getByRole('button', { name: '载入并选择额外任务', exact: true }).click();
  await expect(page.getByText('原件已在本组中。')).toBeVisible();
  await page.getByLabel('额外任务原件编号', { exact: true }).fill(f.memberRelayId);
  await page.getByRole('button', { name: '载入并选择额外任务', exact: true }).click();
  await expect(page.getByLabel('额外任务 1 实物回执编号', { exact: true })).toBeVisible();
  await page.getByLabel('额外任务 1 实物回执编号', { exact: true }).fill(f.memberPhysicalCheckId);
  const target = page.locator('.association-candidate').filter({ has: page.locator(`input[value="${f.targetOrderId}"]`) });
  await target.locator('.el-radio').click(); await expect(target.getByRole('radio')).toBeChecked();
  for (const field of await page.getByRole('textbox', { name: /^(排除候选 |额外任务 1 排除候选 )/ }).all()) await field.fill('逐项核对：不是本组实物对应正式单');
}
async function approve(page: Page): Promise<void> {
  const f = input(); await expect(page.getByText(`原任务短装 ${f.originalShortfall}包；目标计划短装 ${f.targetShortfall}包。`)).toBeVisible();
  await page.getByRole('button', { name: '保存人工关联依据', exact: true }).click();
  await expect(page.getByText('须显式确认原任务与目标两种短装差额，并填写主管批准说明。')).toBeVisible();
  await page.locator('.shortfall-approval .el-checkbox').click();
  await page.getByLabel('关联短装批准说明', { exact: true }).fill('隔离Chrome主管明确批准全组原短装及目标差额');
}
async function expectRecorded(page: Page): Promise<void> {
  const f = input(); await expect(page.getByText(/人工关联已登记，仍待对账；未记账、未放行。回执/)).toBeVisible();
  await expect(page.getByText(`共同关联原件 ${f.memberRelayId} · 实物回执 ${f.memberPhysicalCheckId}`, { exact: true })).toBeVisible();
  await expect(page.getByText(/主原件 .*共 2 个任务/)).toBeVisible();
}
test('Chrome explicitly aggregates originals and recovers one intent across tabs and member readback', async ({ page, context }) => {
  test.setTimeout(120000); const f = input(); const commands: Command[] = [];
  await routeActual(context, commands); await login(page); await page.setViewportSize({ width: 390, height: 844 });
  await prepareMembers(page); await approve(page); expect(commands.length).toBe(0);
  await page.screenshot({ path: `${f.evidence}/390-selection.png`, fullPage: true });
  await page.locator('.association-panel').screenshot({ path: `${f.evidence}/390-selection-panel.png` });
  await page.getByRole('button', { name: '保存人工关联依据', exact: true }).click();
  await expect(page.getByText('原关联结果未明。已保存原请求，刷新后仍按原键恢复。')).toBeVisible();
  await expect(page.getByText(`原请求共同关联原件 ${f.memberRelayId} · 实物回执 ${f.memberPhysicalCheckId}`, { exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: '读取完整核对依据', exact: true }).click();
  const other = await context.newPage(); await openReview(other);
  await Promise.all([page, other].map(tab => tab.getByRole('button', { name: '按原键恢复关联结果', exact: true }).click()));
  for (const tab of [page, other]) await expectRecorded(tab);
  expect(commands.length).toBe(3); expect(new Set(commands.map(row => row.key)).size).toBe(1);
  expect(new Set(commands.map(row => row.body)).size).toBe(1); expect(new Set(commands.map(row => row.epoch)).size).toBe(1);
  expect(commands.every(row => row.status === 200)).toBe(true);
  const saved = await page.evaluate(() => Object.entries(localStorage).find(([key]) => key.startsWith('acrylic.independent-association.v1:'))?.[1]);
  if (!saved) throw new Error('Original group intent disappeared'); writeFileSync(`${f.evidence}/browser-original-command.json`, saved);
  await openReview(page, f.memberRelayId); await expectRecorded(page);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: `${f.evidence}/390-member-recovered.png`, fullPage: true });
  await page.locator('.association-panel').screenshot({ path: `${f.evidence}/390-member-panel.png` });
  await page.evaluate(() => { for (const key of Object.keys(localStorage)) if (key.startsWith('acrylic.independent-association.v1:')) localStorage.removeItem(key); });
  await openReview(page); await expectRecorded(page); await openReview(page, f.memberRelayId); await expectRecorded(page);
  await expect(page.getByRole('button', { name: '载入全部候选并人工选择', exact: true })).toHaveCount(0);
  await context.unrouteAll({ behavior: 'wait' });
  writeFileSync(`${f.evidence}/browser-key-recovery.json`, JSON.stringify({ commands, syntheticDecision: true, actualHttp: true, paginatedAt: 1, width: 390, mainAndMemberWithoutLocalCommand: true }));
});
