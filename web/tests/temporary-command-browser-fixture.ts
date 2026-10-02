import { randomUUID, createHash } from 'node:crypto';
import { expect, type BrowserContext, type Page, type Route } from '@playwright/test';
import { commandFactsSchema, type CommandKind } from '../src/temporary-review/command-contracts';

interface Controls { epoch: string; success: boolean; beforeCsrf: (() => Promise<void>) | null }
interface CapturedPost { json: string; epoch: string; hash: string }

function browserFacts() {
  const submissionId = randomUUID();
  return commandFactsSchema.parse({ warehouseId: randomUUID(), submissionId, workRound: '1',
    authorizationId: randomUUID(), freezeDeviceId: randomUUID(), sourceRecoveryEpoch: '1',
    source: { kind: 'TEMP', creatorDeviceId: randomUUID(), clientOrderId: randomUUID() },
    expected: { canonicalOrderId: randomUUID(), mappingVersion: '1', currentSubmissionId: submissionId,
      scopeRound: '1', slotVersion: '1', decisionVersion: '0', receivedPayloadSha256: 'a'.repeat(64),
      declaredUploadSha256: 'a'.repeat(64), planSha256: 'b'.repeat(64), targetRevision: '1', userAuthVersion: '1',
      candidateContextSha256: null, evidenceSetId: null, evidenceSetSha256: null, verificationId: null,
      verificationVersion: null, verificationAlgorithmVersion: null, dispositionManifestSha256: null,
      dispositionManifestVersion: null, retainedManifestSha256: null, retainedManifestVersion: null } });
}

function envelope(value: unknown, controls: Controls) {
  return { code: 'OK', message: 'synthetic boundary', data: value, requestId: randomUUID(),
    serverTime: new Date().toISOString(), serverRecoveryEpoch: controls.epoch, serviceMode: 'NORMAL' };
}

async function respondToCommand(route: Route, controls: Controls, posts: CapturedPost[]): Promise<void> {
  const json = route.request().postData() ?? '';
  posts.push({ json, epoch: route.request().headers()['x-server-recovery-epoch'] ?? '',
    hash: createHash('sha256').update(json).digest('hex') });
  if (!controls.success) { await route.abort('failed'); return; }
  const body = JSON.parse(json);
  await route.fulfill({ json: { commandType: body.commandType, commandId: body.commandId, status: 'RECORDED',
    capabilities: { writeConfirmationAvailable: false, releaseAllowed: false, mayResumeScanning: false,
      stockReleased: false, localDeletionAllowed: false } } });
}

export async function browserRecovery(context: BrowserContext, kind: CommandKind) {
  const data = browserFacts(); const user = randomUUID();
  const controls: Controls = { epoch: '1', success: false, beforeCsrf: null };
  const posts: CapturedPost[] = [];
  await context.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/facts')) { await route.fulfill({ json: envelope(data, controls) }); return; }
    if (path.endsWith('/auth/csrf')) {
      await controls.beforeCsrf?.();
      await route.fulfill({ json: envelope({ headerName: 'X-CSRF-TOKEN', token: 'synthetic-token' }, controls) }); return;
    }
    if (path.endsWith('/temporary-commands') && route.request().method() === 'POST') {
      await respondToCommand(route, controls, posts); return;
    }
    throw new Error('Unexpected synthetic API request ' + path);
  });
  const query = new URLSearchParams({ user, submission: data.submissionId, warehouse: data.warehouseId, kind });
  return { controls, data, posts, url: `/tools/temporary-command-browser.html?${query}` };
}

export async function openCommand(page: Page, reason = 'Original browser reason'): Promise<void> {
  await page.getByRole('button', { name: '隔离命令', exact: true }).click();
  const input = page.getByRole('dialog').getByRole('textbox');
  if (await input.isEnabled()) await input.fill(reason);
}
export async function sendCommand(page: Page): Promise<void> {
  await page.getByRole('dialog').getByRole('button', { name: /确认提交|重试原请求/ }).click();
}
export async function lostResponse(page: Page): Promise<void> {
  await expect(page.locator('.command-action > .el-alert')).toContainText('连接失败');
}
