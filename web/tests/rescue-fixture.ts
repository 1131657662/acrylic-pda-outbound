import { readFileSync } from 'node:fs';
import { z } from 'zod';
import { expect } from '@playwright/test';
import type { Page } from '@playwright/test';

const loginSchema = z.object({ username: z.string(), password: z.string() });
export function rescueInput() {
  const path = process.env.RESCUE_WEB_INPUT; if (!path) throw new Error('Fresh native rescue input is required');
  return z.object({ purpose: z.literal('SYNTHETIC_NATIVE_RESCUE_WEB'), supervisor: loginSchema, original: loginSchema,
    low: loginSchema, requestId: z.uuid(), warehouseId: z.uuid(), otherWarehouseId: z.uuid() })
    .parse(JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, '')));
}
export async function rescueLogin(page: Page, actor: z.infer<typeof loginSchema>): Promise<void> {
  await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(actor.username);
  await page.getByLabel('密码', { exact: true }).fill(actor.password); await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '装车订单', exact: true })).toBeVisible();
}
export async function readRescue(page: Page, warehouse?: string): Promise<void> {
  const f = rescueInput(); await page.goto(`/rescue?request=${f.requestId}&warehouse=${warehouse ?? f.warehouseId}`);
  await page.getByRole('button', { name: '读取原救援申请', exact: true }).click();
}
export async function permanentRescue(page: Page): Promise<void> {
  await page.getByText('明确永久只读', { exact: true }).click();
  await expect(page.getByRole('radio', { name: '明确永久只读', exact: true })).toBeChecked();
  await page.getByLabel('救援审批或撤销理由', { exact: true }).fill('Isolated human control: permanent read of native original scope');
}
export async function downloadedRescue(page: Page): Promise<unknown> {
  const downloading = page.waitForEvent('download'); await page.getByRole('button', { name: '下载原授权/撤销签名材料', exact: true }).click();
  const download = await downloading; const stream = await download.createReadStream();
  if (!stream) throw new Error('Downloaded material stream missing');
  const chunks = []; for await (const bytes of stream) chunks.push(bytes);
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}
