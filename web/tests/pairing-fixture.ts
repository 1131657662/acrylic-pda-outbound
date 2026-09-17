import { generateKeyPairSync, randomBytes, randomUUID, createHash, sign, type KeyObject } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { z } from 'zod';
import { expect, type APIRequestContext, type Page } from '@playwright/test';
import { pairingSchema, type Pairing } from '../src/devices/pairing-contracts';

const fixtureSchema = z.object({ username: z.string(), password: z.string(), warehouseId: z.string().uuid(), baseUrl: z.string().url() });
export const pairingFixture = (): z.infer<typeof fixtureSchema> => fixtureSchema.parse(JSON.parse(readFileSync('../server/build/pairing-browser-fixture.json', 'utf8')));
const sha = (value: string): string => createHash('sha256').update(value).digest('hex');
const challengeSchema = z.object({ challengeId: z.string(), signedText: z.string(), expiresAt: z.string() });
const envelopeSchema = z.object({ code: z.string(), data: z.unknown() });
interface PairingPayload { applicationId: string; credential: string; publicKey: string; model: string; approvalSha256: string }

export class PairingSimulator {
  readonly key: KeyObject;
  readonly payload: PairingPayload;
  constructor(private readonly request: APIRequestContext) {
    const pair = generateKeyPairSync('ec', { namedCurve: 'prime256v1' }); this.key = pair.privateKey;
    this.payload = { applicationId: randomUUID(), credential: randomBytes(32).toString('base64url'),
      publicKey: pair.publicKey.export({ type: 'spki', format: 'der' }).toString('base64'), model: '浏览器联调 PDA', approvalSha256: '' };
  }
  async call(action: 'CREATE' | 'STATUS' | 'CONFIRM' | 'CANCEL'): Promise<Pairing> {
    const p = this.payload;
    const binding = [p.applicationId, p.publicKey, p.credential, p.model, p.approvalSha256].join('\n');
    const challenge = challengeSchema.parse(await this.post('pda/pairing/challenges', { applicationId: p.applicationId,
      publicKey: p.publicKey, purpose: action, bindingSha256: sha(binding) }));
    const signature = sign('sha256', Buffer.from(challenge.signedText), this.key).toString('base64');
    return pairingSchema.parse(await this.post(`pda/pairing/${action === 'CREATE' ? 'applications' : action.toLowerCase()}`,
      { payload: p, challengeId: challenge.challengeId, signature }));
  }
  async login(deviceId: string): Promise<void> {
    const f = pairingFixture(); const challenge = challengeSchema.parse(await this.post('pda/auth/challenges',
      { deviceId, purpose: 'LOGIN', bindingSha256: sha(f.username) }));
    const session = z.object({ deviceId: z.string(), accessToken: z.string() }).parse(await this.post('pda/auth/login',
      { deviceId, username: f.username, password: f.password, challengeId: challenge.challengeId,
        signature: sign('sha256', Buffer.from(challenge.signedText), this.key).toString('base64') }));
    expect(session.deviceId).toBe(deviceId);
  }
  private async post(path: string, data: unknown): Promise<unknown> {
    const response = await this.request.post(pairingFixture().baseUrl + path, { data });
    expect(response.status()).toBe(200); const result = envelopeSchema.parse(await response.json()); expect(result.code).toBe('OK'); return result.data;
  }
}
export async function pairingLogin(page: Page): Promise<void> {
  const f = pairingFixture(); await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(f.username);
  await page.getByLabel('密码', { exact: true }).fill(f.password); await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '装车订单', exact: true })).toBeVisible(); await page.goto('/devices');
}
export async function reviewPairing(page: Page, code: string): Promise<string> {
  await page.getByLabel('PDA登记码', { exact: true }).fill(`${code.slice(0, 4)} ${code.slice(4)}`);
  await page.getByRole('button', { name: '查找登记申请', exact: true }).click();
  await expect(page.getByText('等待管理员确认', { exact: true })).toBeVisible();
  const deviceCode = `WEB-${randomUUID().slice(0, 12)}`;
  await page.getByLabel('配对设备编号', { exact: true }).fill(deviceCode);
  await page.getByLabel('配对设备名称', { exact: true }).fill('北侧装车 PDA');
  await page.getByText('已核对登记码、设备型号和当前仓库', { exact: true }).click();
  await page.getByRole('button', { name: '确认登记意图', exact: true }).click();
  return deviceCode;
}
