import { expect, type Page, type Locator } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { z } from 'zod';
import { receiptSchema } from '../src/reconciliation/contracts';
const account=z.object({username:z.string(),password:z.string()});
const schema=z.object({warehouse:z.uuid(),order:z.uuid(),dispatch:z.uuid(),maker:account,checker:account});
export function reconciliationInput(): z.infer<typeof schema> {
  if(!process.env.P06_RECONCILE_WEB_INPUT) throw new Error('Preserved isolated browser input required');
  return schema.parse(JSON.parse(readFileSync(process.env.P06_RECONCILE_WEB_INPUT,'utf8').replace(/^\uFEFF/,'')));
}
export function reconciliationEvidence(name:string,bytes:string|Buffer):void {
  if(!process.env.P06_RECONCILE_WEB_EVIDENCE) throw new Error('Evidence directory required');
  writeFileSync(`${process.env.P06_RECONCILE_WEB_EVIDENCE}/${name}`,bytes);
}
export async function reconciliationOpen(page:Page,role:'maker'|'checker'):Promise<void> {
  const input=reconciliationInput();const actor=input[role];
  await page.goto('/login');await page.getByLabel('账号',{exact:true}).fill(actor.username);
  await page.getByLabel('密码',{exact:true}).fill(actor.password);await page.getByRole('button',{name:'登录',exact:true}).click();
  await expect(page.getByRole('button',{name:'登录',exact:true})).toHaveCount(0);
  await page.goto(`/inventory-reconciliation?warehouse=${input.warehouse}`);
  await expect(page.getByRole('region',{name:'库存账务差异处理'})).toBeVisible();
  await expect(page.getByRole('button',{name:'刷新差异与原事实',exact:true})).toBeEnabled();
}
export function reconciliationCase(page:Page,id:string):Locator {return page.locator(`[data-case="${id}"]`);}
export async function reconciliationDialog(page:Page,button:Locator,reason:string):Promise<void> {
  await button.click();await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByLabel('差异处理原因',{exact:true}).fill(reason);
}
export async function reconciliationSubmit(page:Page,label:string):Promise<z.infer<typeof receiptSchema>> {
  const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/corrections')&&r.request().method()==='POST');
  await page.getByRole('dialog').getByRole('button',{name:label,exact:true}).click();
  const result=await response;const bytes=await result.body();expect(result.status(),bytes.toString()).toBe(200);
  const receipt=receiptSchema.parse(JSON.parse(bytes.toString()));
  reconciliationEvidence(`${receipt.commandId}.command.json`,result.request().postData()??'');
  reconciliationEvidence(`${receipt.commandId}.receipt.json`,bytes);await expect(page.getByRole('dialog')).toHaveCount(0);return receipt;
}
