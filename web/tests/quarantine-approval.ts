import { expect, type Page } from '@playwright/test';
import { quarantineEvidence } from './quarantine-fixture';
import { createHash } from 'node:crypto';
export async function quarantineReview(page:Page,bytes:Buffer,origin:{hash:string;kind:'TEMP'|'PC'|'FORMAL'}):Promise<void> {
  await expect(page.getByText('原隔离与关闭依据',{exact:true})).toBeVisible();
  const source=page.waitForResponse(r=>r.url().includes('/quarantine/original-receipt?'));
  await page.getByRole('button',{name:origin.kind==='PC'?'下载原PC审批依据':origin.kind==='FORMAL'?'下载原FORMAL处置依据':'下载原隔离签名回执',exact:true}).click(); const sourceResponse=await source;
  expect(sourceResponse.status()).toBe(200); const sourceBytes=await sourceResponse.body();
  expect(createHash('sha256').update(sourceBytes).digest('hex')).toBe(origin.hash);
  if(origin.kind==='PC') {
    await expect(page.getByText('原数据库审批、停止快照和应用记录',{exact:true})).toBeVisible();
    expect(JSON.parse(sourceBytes.toString()).proofType).toBe('PC_DATABASE_ORIGINALS');
    expect(JSON.parse(sourceBytes.toString()).signedReceipt).toBeUndefined();
  }
  if(origin.kind==='FORMAL') {
    await expect(page.getByText('原实物批准、原机停止签名和应用提交',{exact:true})).toBeVisible();
    const proof=JSON.parse(sourceBytes.toString());expect(proof.proofType).toBe('FORMAL_RECOVERY_APPLICATION_ORIGINALS');
    expect(proof.signedReceipt).toBeUndefined();expect(proof.recovery.stop.signedReceipt).toBeTruthy();expect(proof.application.receipt.signedReceipt).toBeTruthy();
  }
  quarantineEvidence(origin.kind==='PC'?'original-pc-proof.json':origin.kind==='FORMAL'?'original-formal-proof.json':'original-quarantine-receipt.json',sourceBytes);
  await page.screenshot({path:`${process.env.QUARANTINE_WEB_EVIDENCE}/mobile-source.png`});
  const original=page.waitForResponse(r=>r.url().includes('/attachments/')&&r.url().includes('/review?'));
  await page.getByRole('button',{name:'读取原始凭据 1',exact:true}).click(); expect(await(await original).body()).toEqual(bytes);
  await page.getByLabel('差异处理原因',{exact:true}).scrollIntoViewIfNeeded();
  await expect(page.getByRole('dialog').getByRole('button',{name:'确认主管批准解隔离',exact:true})).toBeInViewport();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth>document.documentElement.clientWidth)).toBe(false);
  await page.screenshot({path:`${process.env.QUARANTINE_WEB_EVIDENCE}/mobile-approval.png`});
}
export async function quarantineLostApproval(page:Page):Promise<void> {
  let committed:Buffer|null=null;let posts=0;
  await page.route('**/api/v1/corrections',async route=>{posts++;const response=await route.fetch();committed=await response.body();
    expect(response.status(),committed.toString()).toBe(200);quarantineEvidence('lost-approval.command.json',route.request().postData()??'');
    quarantineEvidence('lost-approval.receipt.json',committed);await route.abort('failed');});
  await page.getByRole('dialog').getByRole('button',{name:'确认主管批准解隔离',exact:true}).click();
  await expect(page.getByRole('button',{name:'核对或重试原差异请求',exact:true})).toBeVisible();
  await page.getByRole('dialog').getByRole('button',{name:'返回核对',exact:true}).click();await page.reload();
  await page.getByText('实物解隔离',{exact:true}).click();
  const recovered=page.waitForResponse(r=>r.url().includes('/corrections/commands/')&&r.status()===200);
  await page.getByRole('button',{name:'核对或重试原差异请求',exact:true}).click();expect(await(await recovered).body()).toEqual(committed);
  expect(posts).toBe(1);
}
