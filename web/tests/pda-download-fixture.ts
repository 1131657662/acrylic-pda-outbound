import { createHash, generateKeyPairSync, sign } from 'node:crypto';
import type { Page, BrowserContext } from '@playwright/test';

export function releaseFixture(overrides: Record<string, unknown> = {}) {
  const apk = Buffer.from('synthetic bytes for browser HTTP boundary tests');
  const keys = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const release = { channel: 'staging', packageName: 'com.acrylic.outbound.pda.staging',
    versionName: '1.0.0-首次安装测试', versionCode: 20, minSupportedVersionCode: 12, maxSupportedVersionCode: null,
    releaseId: 'test-20', apkPath: 'updates/staging/test-20.apk', sizeBytes: apk.length,
    sha256: createHash('sha256').update(apk).digest('hex'), certificateSha256: 'a'.repeat(64),
    serverCompatibility: '0.20.0', publishedAt: '2026-09-19T00:00:00Z', minAndroidApi: 28,
    releaseNotes: '仅供隔离测试。首次安装与覆盖升级分别验证。', firstInstallAllowed: true, ...overrides };
  const raw = Buffer.from(JSON.stringify(release));
  return { apk, release, config: { channel: 'staging', environmentName: '隔离测试环境',
    origin: 'https://127.0.0.1:5189', certificateSha256: 'a'.repeat(64), serverCompatibility: ['0.20.0'],
    publicKeyBase64: keys.publicKey.export({ type: 'spki', format: 'der' }).toString('base64') },
    manifest: { payload: raw.toString('base64'), signature: sign('sha256', raw, keys.privateKey).toString('base64') } };
}
export async function mockRelease(target: Page | BrowserContext, fixture = releaseFixture()): Promise<void> {
  await target.route('**/pda-download-config.json', route => route.fulfill({ json: fixture.config }));
  await target.route('**/updates/staging/manifest.json', route => route.fulfill({ json: fixture.manifest }));
  await target.route('**/*.apk', route => route.fulfill({ body: fixture.apk, contentType: 'application/vnd.android.package-archive' }));
}
export function envelope(data: unknown) {
  return { code: 'OK', message: 'ok', data, requestId: 'fixture', serverTime: '2026-09-19T00:00:00Z', serverRecoveryEpoch: 'epoch', serviceMode: 'NORMAL' };
}
