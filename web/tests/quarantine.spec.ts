import { test, expect } from '@playwright/test';
import { quarantineInput,quarantineOpen,quarantineCase,quarantineDialog,quarantineSubmit,quarantineEvidence,quarantineSource } from './quarantine-fixture';
import { quarantineReview,quarantineLostApproval } from './quarantine-approval';
import { quarantineHistoryReview } from './quarantine-history';
test.skip(!process.env.QUARANTINE_WEB_INPUT,'Requires preserved original quarantine source in isolated database');
test('durable storage failure prevents quarantine discovery transmission',async({page})=>{
  await quarantineOpen(page,'maker'); let posts=0;
  page.on('request',r=>{if(r.method()==='POST'&&r.url().endsWith('/api/v1/corrections'))posts++;});
  await quarantineDialog(page,page.locator(`[data-quarantine-bale="${quarantineInput().baleId}"]`).getByRole('button',{name:'登记解隔离核验',exact:true}),'isolated quota');
  await page.evaluate(()=>{const original=Storage.prototype.setItem;Storage.prototype.setItem=function(key,value){
    if(key.startsWith('acrylic.reconciliation.'))throw new Error('Isolated quota');original.call(this,key,value);};});
  await page.getByRole('dialog').getByRole('button',{name:'确认登记解隔离核验',exact:true}).click();
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('操作未确认');expect(posts).toBe(0);
});
test('actual physical attachment and independent clearance recover lost response with original key',async({page,browser})=>{
  await quarantineOpen(page,'maker'); await quarantineDialog(page,page.locator(`[data-quarantine-bale="${quarantineInput().baleId}"]`).getByRole('button',{name:'登记解隔离核验',exact:true}),'isolated browser discovery');
  const discovered=await quarantineSubmit(page,'确认登记解隔离核验'); const id=discovered.businessDecision.caseId;
  await quarantineDialog(page,quarantineCase(page,id).getByRole('button',{name:'提交解隔离凭据',exact:true}),'isolated physical observation');
  await page.getByLabel('实物核验地点',{exact:true}).fill('ISOLATED quarantine bay'); await page.getByLabel('实物核验依据',{exact:true}).fill('ISOLATED actual inspection; quarantine conditions cleared');
  const bytes=Buffer.from('ISOLATED original quarantine inspection 实物在库');
  await page.getByLabel('差异原始凭据',{exact:true}).setInputFiles({name:'inspection.bin',mimeType:'application/octet-stream',buffer:bytes});
  await expect(page.getByRole('dialog')).toContainText('已取得 1 份凭据'); await quarantineSubmit(page,'确认提交解隔离凭据');
  await expect(quarantineCase(page,id).getByRole('button',{name:'主管批准解隔离',exact:true})).toHaveCount(0);
  const checkerContext=await browser.newContext({baseURL:process.env.OUTBOUND_WEB_URL??'http://127.0.0.1:5174',viewport:{width:390,height:844}});
  const checker=await checkerContext.newPage(); await quarantineOpen(checker,'checker');
  await quarantineDialog(checker,quarantineCase(checker,id).getByRole('button',{name:'主管批准解隔离',exact:true}),'isolated independent approval');
  await quarantineHistoryReview(checker);await quarantineReview(checker,bytes,quarantineSource(discovered));await quarantineLostApproval(checker);await expect(quarantineCase(checker,id)).toContainText('原隔离已解除');
  await expect(checker.getByRole('button',{name:'核对或重试原差异请求',exact:true})).toHaveCount(0);
  quarantineEvidence('physical-original.bin',bytes);await checker.screenshot({path:`${process.env.QUARANTINE_WEB_EVIDENCE}/resolved-page.png`});await checkerContext.close();
});
