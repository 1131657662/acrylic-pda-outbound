import { expect, type Page, type Locator } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { z } from 'zod';
import { receiptSchema } from '../src/reconciliation/contracts';
const schema=z.object({ warehouseId:z.uuid(),baleId:z.uuid(),makerUsername:z.string(),checkerUsername:z.string(),password:z.string() });
export function quarantineInput(): z.infer<typeof schema> {
  if(!process.env.QUARANTINE_WEB_INPUT) throw new Error('Preserved isolated browser input required');
  return schema.parse(JSON.parse(readFileSync(process.env.QUARANTINE_WEB_INPUT,'utf8').replace(/^\uFEFF/,'')));
}
export function quarantineEvidence(name:string,bytes:string|Buffer):void {
  if(!process.env.QUARANTINE_WEB_EVIDENCE) throw new Error('Evidence directory required'); writeFileSync(`${process.env.QUARANTINE_WEB_EVIDENCE}/${name}`,bytes);
}
export async function quarantineOpen(page:Page,role:'maker'|'checker'):Promise<void> {
  const input=quarantineInput(); await page.goto('/login');
  await page.getByLabel('账号',{exact:true}).fill(role==='maker'?input.makerUsername:input.checkerUsername);
  await page.getByLabel('密码',{exact:true}).fill(input.password); await page.getByRole('button',{name:'登录',exact:true}).click();
  await expect(page.getByRole('button',{name:'登录',exact:true})).toHaveCount(0);
  await page.goto(`/inventory-reconciliation?warehouse=${input.warehouseId}`); await page.getByText('实物解隔离',{exact:true}).click();
  await expect(page.getByRole('region',{name:'实物解隔离核验'})).toBeVisible(); await expect(page.getByRole('button',{name:'刷新隔离与原事实',exact:true})).toBeEnabled();
}
export function quarantineCase(page:Page,id:string):Locator { return page.locator(`[data-quarantine-case="${id}"]`); }
export async function quarantineDialog(page:Page,button:Locator,reason:string):Promise<void> {
  await button.click(); await expect(page.getByRole('dialog')).toBeVisible(); await page.getByLabel('差异处理原因',{exact:true}).fill(reason);
}
export async function quarantineSubmit(page:Page,label:string):Promise<z.infer<typeof receiptSchema>> {
  const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/corrections')&&r.request().method()==='POST');
  await page.getByRole('dialog').getByRole('button',{name:label,exact:true}).click(); const result=await response; const bytes=await result.body();
  expect(result.status(),bytes.toString()).toBe(200); const receipt=receiptSchema.parse(JSON.parse(bytes.toString()));
  quarantineEvidence(`${receipt.commandId}.command.json`,result.request().postData()??''); quarantineEvidence(`${receipt.commandId}.receipt.json`,bytes);
  await expect(page.getByRole('dialog')).toHaveCount(0); return receipt;
}
export function quarantineSourceHash(receipt:z.infer<typeof receiptSchema>):string {
  const source=receipt.context?.facts;
  if (!source || typeof source !== 'object' || !('origin' in source)) throw new Error('Original source missing');
  const origin=source.origin;
  if (!origin || typeof origin !== 'object' || !('originalReceiptSha256' in origin) || typeof origin.originalReceiptSha256 !== 'string') throw new Error('Original digest missing');
  return origin.originalReceiptSha256;
}
