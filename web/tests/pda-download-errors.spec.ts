import { test, expect } from '@playwright/test';
import { releaseFixture, mockRelease } from './pda-download-fixture';

const rejectedReleases = [
  { label: 'first install disabled', data: { firstInstallAllowed: false }, heading: '当前版本暂不适合首次安装' },
  { label: 'legacy manifest has no first install approval', data: { firstInstallAllowed: undefined }, heading: '当前版本暂不适合首次安装' },
  { label: 'wrong server contract', data: { serverCompatibility: '0.0.0' }, heading: '当前版本暂不适合首次安装' },
  { label: 'debug release', data: { channel: 'debug' }, heading: '发布信息或安装包校验失败' },
  { label: 'wrong package', data: { packageName: 'com.other.app' }, heading: '发布信息或安装包校验失败' },
  { label: 'wrong certificate', data: { certificateSha256: 'b'.repeat(64) }, heading: '发布信息或安装包校验失败' },
  { label: 'external APK address', data: { apkPath: 'https://untrusted.invalid/a.apk' }, heading: '发布信息或安装包校验失败' },
  { label: 'traversal APK address', data: { apkPath: 'updates/staging/../../a.apk' }, heading: '发布信息或安装包校验失败' },
  { label: 'oversized APK', data: { sizeBytes: 65 * 1024 * 1024 }, heading: '发布信息或安装包校验失败' },
];
for (const scenario of rejectedReleases) {
  test(`rejects ${scenario.label}`, async ({ page }) => {
    await mockRelease(page, releaseFixture(scenario.data));
    await page.goto('/pda-download');
    await expect(page.getByRole('heading', { name: scenario.heading })).toBeVisible();
    await expect(page.getByRole('button', { name: '下载安装包', exact: true })).toHaveCount(0);
  });
}

for (const scenario of [
  { label: 'no release', status: 404, heading: '尚无可下载版本' },
  { label: 'paused release', status: 410, heading: '已暂停分发' },
  { label: 'server unavailable', status: 503, heading: '无法获取下载信息' },
]) {
  test(`reports ${scenario.label} and can recover`, async ({ page }) => {
    await mockRelease(page);
    await page.route('**/manifest.json', route => route.fulfill({ status: scenario.status }));
    await page.goto('/pda-download');
    await expect(page.getByRole('heading', { name: scenario.heading })).toBeVisible();
    await page.unroute('**/manifest.json');
    await page.getByRole('button', { name: '重新检查', exact: true }).click();
    await expect(page.getByRole('button', { name: '下载安装包', exact: true })).toBeVisible();
  });
}

test('rejects a signature from a different key', async ({ page }) => {
  const fixture = releaseFixture(); fixture.manifest.signature = releaseFixture().manifest.signature;
  await mockRelease(page, fixture); await page.goto('/pda-download');
  await expect(page.getByRole('heading', { name: '发布信息或安装包校验失败' })).toBeVisible();
});

test('rejects invalid deployment configuration', async ({ page }) => {
  const fixture = releaseFixture(); fixture.config.origin = 'https://other.invalid';
  await mockRelease(page, fixture); await page.goto('/pda-download');
  await expect(page.getByRole('heading', { name: '下载入口尚未启用' })).toBeVisible();
});

test('an interrupted manifest response reports a network error', async ({ page }) => {
  await mockRelease(page);
  await page.addInitScript(() => {
    const original = window.fetch;
    window.fetch = async (...args) => {
      if (String(args[0]).endsWith('/manifest.json')) {
        return new Response(new ReadableStream({
          start(controller) { controller.enqueue(new TextEncoder().encode('{')); },
          pull(controller) { controller.error(new TypeError('network connection lost')); },
        }));
      }
      return original(...args);
    };
  });
  await page.goto('/pda-download');
  await expect(page.getByRole('heading', { name: '无法获取下载信息' })).toBeVisible();
  await expect(page.getByRole('button', { name: '下载安装包', exact: true })).toHaveCount(0);
});

test('rejects Android below the declared minimum', async ({ browser }) => {
  const context = await browser.newContext({ ignoreHTTPSErrors: true, userAgent: 'Mozilla/5.0 (Linux; Android 8.1.0)' });
  try {
    const page = await context.newPage(); await mockRelease(page);
    await page.goto('https://127.0.0.1:5189/pda-download');
    await expect(page.getByRole('heading', { name: '当前版本暂不适合首次安装' })).toBeVisible();
  } finally { await context.close(); }
});

for (const kind of ['digest', 'short', 'oversized', 'interrupted'] as const) {
  test(`never saves an APK with ${kind} failure`, async ({ page }) => {
    const fixture = releaseFixture(); await mockRelease(page, fixture);
    await page.route('**/*.apk', route => kind === 'interrupted' ? route.abort('connectionreset') : route.fulfill({
      body: kind === 'digest' ? Buffer.alloc(fixture.apk.length) : kind === 'short' ? fixture.apk.subarray(1) : Buffer.concat([fixture.apk, Buffer.from('x')]),
    }));
    const saved: string[] = []; page.on('download', item => saved.push(item.url()));
    await page.goto('/pda-download'); await page.getByRole('button', { name: '下载安装包', exact: true }).click();
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByRole('button', { name: '下载安装包', exact: true })).toHaveCount(0);
    expect(saved).toEqual([]);
  });
}

test('a new publication during transfer prevents saving the previous APK', async ({ page }) => {
  const fixture = releaseFixture(); await mockRelease(page, fixture);
  await page.route('**/*.apk', async route => {
    await mockRelease(page, releaseFixture({ versionCode: 21, versionName: '下载中发布的新版本' }));
    await route.fulfill({ body: fixture.apk });
  });
  const saved: string[] = []; page.on('download', item => saved.push(item.url()));
  await page.goto('/pda-download'); await page.getByRole('button', { name: '下载安装包', exact: true }).click();
  await expect(page.getByText('下载中发布的新版本（21）')).toBeVisible();
  await expect(page.getByRole('status')).toContainText('发布信息已更新'); expect(saved).toEqual([]);
});
