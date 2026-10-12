import { test, expect } from '@playwright/test';
import { reconciliationInput, reconciliationOpen, reconciliationCase, reconciliationDialog, reconciliationSubmit, reconciliationEvidence } from './reconciliation-fixture';
test.skip(!process.env.P06_RECONCILE_WEB_INPUT,'Requires preserved isolated HTTP/MySQL missing-posting facts');

test('durable storage failure prevents discrepancy discovery submission',async({page})=>{
  await reconciliationOpen(page,'maker');let posts=0;
  page.on('request',r=>{if(r.method()==='POST'&&r.url().endsWith('/api/v1/corrections'))posts++;});
  await reconciliationDialog(page,page.locator(`[data-dispatch="${reconciliationInput().dispatch}"]`).getByRole('button',{name:'登记缺账差异',exact:true}),'isolated quota');
  await page.evaluate(()=>{const original=Storage.prototype.setItem;Storage.prototype.setItem=function(key,value){
    if(key.startsWith('acrylic.reconciliation.'))throw new Error('Isolated quota');original.call(this,key,value);};});
  await page.getByRole('dialog').getByRole('button',{name:'确认登记缺账差异',exact:true}).click();
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('操作未确认');expect(posts).toBe(0);
});

test('actual bytes and independent approval close the page and recover lost response using original key',async({page,browser})=>{
  await reconciliationOpen(page,'maker');
  await reconciliationDialog(page,page.locator(`[data-dispatch="${reconciliationInput().dispatch}"]`).getByRole('button',{name:'登记缺账差异',exact:true}),'isolated browser discovery');
  const discovered=await reconciliationSubmit(page,'确认登记缺账差异');const id=discovered.businessDecision.caseId;
  await reconciliationDialog(page,reconciliationCase(page,id).getByRole('button',{name:'提交核验凭据',exact:true}),'isolated physical observation');
  await page.getByLabel('实物核验地点',{exact:true}).fill('ISOLATED browser dispatch bay');
  await page.getByLabel('实物核验依据',{exact:true}).fill('ISOLATED actual signed delivery paper');
  const bytes=Buffer.from('ISOLATED original physical evidence with 实物出库');
  await page.getByLabel('差异原始凭据',{exact:true}).setInputFiles({name:'physical.bin',mimeType:'application/octet-stream',buffer:bytes});
  await expect(page.getByRole('dialog')).toContainText('已取得 1 份凭据');
  await reconciliationSubmit(page,'确认提交核验凭据');
  await expect(reconciliationCase(page,id).getByRole('button',{name:'主管批准补扣',exact:true})).toHaveCount(0);
  const checkerContext=await browser.newContext({baseURL:process.env.OUTBOUND_WEB_URL??'http://127.0.0.1:5174'});
  const checker=await checkerContext.newPage();await reconciliationOpen(checker,'checker');
  await reconciliationDialog(checker,reconciliationCase(checker,id).getByRole('button',{name:'主管批准补扣',exact:true}),'isolated independent approval');
  const original=checker.waitForResponse(r=>r.url().includes('/attachments/')&&r.url().includes('/review?'));
  await checker.getByRole('button',{name:'读取原始凭据 1',exact:true}).click();expect(await(await original).body()).toEqual(bytes);
  let committed:Buffer|null=null;let posts=0;
  await checker.route('**/api/v1/corrections',async route=>{posts++;const response=await route.fetch();committed=await response.body();
    expect(response.status(),committed.toString()).toBe(200);reconciliationEvidence('lost-approval.command.json',route.request().postData()??'');
    reconciliationEvidence('lost-approval.receipt.json',committed);await route.abort('failed');});
  await checker.getByRole('dialog').getByRole('button',{name:'确认主管批准补扣',exact:true}).click();
  await expect(checker.getByRole('button',{name:'核对或重试原差异请求',exact:true})).toBeVisible();
  await checker.getByRole('dialog').getByRole('button',{name:'返回核对',exact:true}).click();await checker.reload();
  const recovered=checker.waitForResponse(r=>r.url().includes('/corrections/commands/')&&r.status()===200);
  await checker.getByRole('button',{name:'核对或重试原差异请求',exact:true}).click();expect(await(await recovered).body()).toEqual(committed);
  expect(posts).toBe(1);await expect(reconciliationCase(checker,id)).toContainText('已补扣');
  await expect(checker.getByRole('button',{name:'核对或重试原差异请求',exact:true})).toHaveCount(0);
  reconciliationEvidence('physical-original.bin',bytes);await checker.screenshot({path:`${process.env.P06_RECONCILE_WEB_EVIDENCE}/resolved-page.png`});
  await checkerContext.close();
});
