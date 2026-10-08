import { randomUUID, createHash } from 'node:crypto';
import { expect, type Page, type Route } from '@playwright/test';
import { faultBrowser, openFault } from '../handover-fault-browser-fixture';

export async function attachmentBrowser(page: Page) {
  const f = await faultBrowser(page); f.facts.fault.attachmentsAvailable = true;
  const key = `acrylic.handover-fault-attachments.v1:${f.user}:${f.facts.context.warehouseId}:${f.facts.context.caseId}`;
  const calls: { url: string; sha: string; size: number; epoch: string | undefined }[] = [];
  const controls: { reply: ((route: Route) => Promise<void>) | null } = { reply: null };
  await page.context().route('**/api/v1/handovers/*/attachments/*?*', async route => {
    const bytes = route.request().postDataBuffer(); expect(bytes).not.toBeNull();
    if (!bytes) throw new Error('Expected opaque upload bytes');
    const sha = createHash('sha256').update(bytes).digest('hex');
    expect(route.request().headers()['x-content-sha256']).toBe(sha);
    calls.push({ url: route.request().url(), sha, size: bytes.length, epoch: route.request().headers()['x-server-recovery-epoch'] });
    if (controls.reply) { await controls.reply(route); return; }
    await route.abort('failed');
  });
  async function success(route: Route): Promise<void> {
    const call = calls.at(-1); if (!call) throw new Error('Missing upload');
    await route.fulfill({ json: { code: 'OK', message: 'Synthetic upload boundary', requestId: randomUUID(),
      data: { attachmentId: randomUUID(), evidenceSha256: call.sha, sizeBytes: String(call.size) } } });
  }
  async function open(): Promise<void> { await page.goto(f.url); await openFault(page); }
  return { ...f, attachmentKey: key, calls, uploads: controls, success, open };
}

export function file(name = 'original.bin', content = 'original evidence bytes') {
  return { name, mimeType: 'application/octet-stream', buffer: Buffer.from(content) };
}
export async function identityChanged(page: Page): Promise<void> {
  await page.evaluate(async () => { const { useSessionStore } = await import('/src/session.ts'); await useSessionStore().load(); });
}
