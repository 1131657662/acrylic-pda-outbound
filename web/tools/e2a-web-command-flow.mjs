// A3 browser driver: performs the temporary command chain from a real browser session against an already
// running server. Cookies and CSRF are handled by the browser exactly as the SPA does it, because every call
// runs inside the page context. Usage:
//   node e2a-web-command-flow.mjs <username> <password> <warehouseId> <submissionId> <outputJson>
import { writeFileSync } from 'node:fs';
import { chromium } from '@playwright/test';

const [username, password, warehouseId, submissionId, output] = process.argv.slice(2);
const base = process.env.OUTBOUND_WEB_URL ?? 'http://127.0.0.1:19434';
const result = { base, steps: [], receipts: {} };

const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  const context = await browser.newContext({ baseURL: base });
  const page = await context.newPage();
  // Any same-origin JSON endpoint works as the carrier page; the SPA is not required.
  await page.goto('/api/v1/auth/csrf');
  const flow = await page.evaluate(async ({ username, password, warehouseId, submissionId }) => {
    const log = [];
    const uuid = () => (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`);
    const json = async (response) => {
      const text = await response.text();
      try { return JSON.parse(text); } catch { return { raw: text }; }
    };
    // Authentication rotates the CSRF token, so every POST after login must use the token that is
    // current at that moment: re-read it instead of reusing the pre-login pair (a stale token is
    // rejected as ACCESS_DENIED "security credential invalid" before any business logic runs).
    let csrfHeader, csrfToken, epoch;
    const readCsrf = async (label) => {
      const csrfResponse = await json(await fetch('/api/v1/auth/csrf'));
      csrfHeader = csrfResponse.data.headerName;
      csrfToken = csrfResponse.data.token;
      epoch = csrfResponse.serverRecoveryEpoch;
      log.push(`csrf${label} ${csrfHeader} epoch=${epoch}`);
    };
    await readCsrf('');
    const login = await fetch('/api/v1/auth/web/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', [csrfHeader]: csrfToken },
      body: new URLSearchParams({ username, password, _csrf: csrfToken }).toString(),
    });
    const loginBody = await json(login);
    log.push(`login ${login.status} authenticated=${loginBody?.data?.authenticated}`);
    if (login.status !== 200) return { log, failed: 'login', loginStatus: login.status, loginBody };
    await readCsrf(' (after login)');

    const factsUrl = `/api/v1/temporary-commands/facts?warehouseId=${warehouseId}&submissionId=${submissionId}`;
    const readFacts = async (label) => {
      const response = await fetch(factsUrl);
      const body = await json(response);
      log.push(`${label} ${response.status}`);
      return { status: response.status, data: body.data, body };
    };
    const candidateDigest = async () => {
      const response = await fetch(`/api/v1/temporary-reviews/${submissionId}/duplicate-candidates?warehouseId=${warehouseId}&limit=1`);
      const body = await json(response);
      log.push(`candidates ${response.status}`);
      return body.data.contextSha256;
    };
    const command = (kind, facts, digest, reason) => ({
      commandVersion: 1, commandType: kind, commandId: uuid(), transportRecoveryEpoch: epoch, reason,
      warehouseId: facts.warehouseId, submissionId: facts.submissionId, workRound: facts.workRound,
      authorizationId: facts.authorizationId, freezeDeviceId: facts.freezeDeviceId,
      sourceRecoveryEpoch: facts.sourceRecoveryEpoch, source: { ...facts.source },
      expected: { ...facts.expected, candidateContextSha256: digest },
    });
    const post = async (label, body) => {
      const response = await fetch('/api/v1/temporary-commands', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', [csrfHeader]: csrfToken, 'X-Server-Recovery-Epoch': epoch },
        body: JSON.stringify(body),
      });
      const receipt = await json(response);
      log.push(`${label} ${response.status} ${receipt.status ?? receipt.code}`);
      return { status: response.status, receipt, body };
    };

    const first = await readFacts('facts#1');
    if (first.status !== 200) return { log, failed: 'facts', first };
    const factsKeys = Object.keys(first.data ?? {}).sort();
    const expectedKeys = Object.keys(first.data?.expected ?? {}).sort();
    const link = await post('LINK', command('LINK', first.data, await candidateDigest(), '人工核对候选后确认受控关联'));
    if (link.status !== 200) return { log, factsKeys, expectedKeys, failed: 'link', link };
    const second = await readFacts('facts#2');
    if (second.status !== 200) return { log, factsKeys, expectedKeys, failed: 'facts#2', second };
    const confirm = await post('NORMAL_CONFIRM', command('NORMAL_CONFIRM', second.data, await candidateDigest(), '人工核对无误后确认临时出库'));
    return { log, factsKeys, expectedKeys, mappingVersionAfterLink: second.data?.expected?.mappingVersion,
      canonicalOrderAfterLink: second.data?.expected?.canonicalOrderId, link, confirm,
      failed: confirm.status === 200 ? null : 'confirm' };
  }, { username, password, warehouseId, submissionId });
  result.steps = flow.log ?? [];
  result.failed = flow.failed ?? null;
  result.factsKeys = flow.factsKeys; result.expectedKeys = flow.expectedKeys;
  result.mappingVersionAfterLink = flow.mappingVersionAfterLink;
  result.canonicalOrderAfterLink = flow.canonicalOrderAfterLink;
  result.receipts.link = flow.link?.receipt ?? flow.link?.body ?? null;
  result.receipts.confirm = flow.confirm?.receipt ?? null;
  result.linkStatus = flow.link?.status ?? null;
  result.confirmStatus = flow.confirm?.status ?? null;
  writeFileSync(output, JSON.stringify(result, null, 2));
  process.exitCode = flow.failed ? 1 : 0;
} finally {
  await browser.close();
}
