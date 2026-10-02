import { createRenderer } from 'vue';
import { randomUUID, createHash } from 'node:crypto';

const renderer = createRenderer({ patchProp() {}, insert() {}, remove() {}, createElement: () => ({}),
  createText: () => ({}), createComment: () => ({}), setText() {}, setElementText() {},
  parentNode: () => null, nextSibling: () => null });

export function facts() {
  const submissionId = randomUUID();
  return {
    warehouseId: randomUUID(), submissionId, workRound: '1', authorizationId: randomUUID(),
    freezeDeviceId: randomUUID(), sourceRecoveryEpoch: '1',
    source: { kind: 'TEMP', creatorDeviceId: randomUUID(), clientOrderId: randomUUID() },
    expected: { canonicalOrderId: randomUUID(), mappingVersion: '1', currentSubmissionId: submissionId,
      scopeRound: '1', slotVersion: '1', decisionVersion: '0', receivedPayloadSha256: 'a'.repeat(64),
      declaredUploadSha256: 'a'.repeat(64), planSha256: 'b'.repeat(64), targetRevision: '1', userAuthVersion: '1',
      candidateContextSha256: null, evidenceSetId: null, evidenceSetSha256: null, verificationId: null,
      verificationVersion: null, verificationAlgorithmVersion: null, dispositionManifestSha256: null,
      dispositionManifestVersion: null, retainedManifestSha256: null, retainedManifestVersion: null },
  };
}

function storage(options, events, entries) {
  return {
    get length() { return entries.size; }, key: index => [...entries.keys()][index] ?? null,
    getItem(key) { if (options.blockedRead) throw new Error('Synthetic read denied'); return entries.get(key) ?? null; },
    setItem(key, value) {
      events.push({ action: 'setItem', key, blocked: !!options.blockedStorage });
      if (options.blockedStorage) throw new Error('Synthetic quota denied');
      if (!options.silentWrite) entries.set(key, String(value));
    },
    removeItem(key) { if (options.blockedRemove) throw new Error('Synthetic removal denied'); entries.delete(key); },
  };
}

function locks() {
  const queues = new Map();
  return { async request(key, action) {
    const previous = queues.get(key) ?? Promise.resolve();
    let release; const next = new Promise(resolve => { release = resolve; }); queues.set(key, next);
    await previous;
    try { return await action(); } finally { release(); if (queues.get(key) === next) queues.delete(key); }
  } };
}

function receipt(body, options) {
  return { status: 'RECORDED', commandId: options.mismatchedReceipt ? randomUUID() : body.commandId,
    commandType: body.commandType, capabilities: { writeConfirmationAvailable: false, releaseAllowed: false,
      mayResumeScanning: false, stockReleased: false, localDeletionAllowed: false } };
}

export function boundary(options = {}) {
  const data = facts(); const calls = []; const storageEvents = []; const entries = new Map();
  let epoch = '1'; let userId = randomUUID(); let gets = 0;
  const previous = ['localStorage', 'navigator'].map(key => [key, Object.getOwnPropertyDescriptor(globalThis, key)]);
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: storage(options, storageEvents, entries) });
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { locks: options.noLocks ? null : locks() } });
  globalThis.temporaryRecoveryBoundary = {
    epoch: () => epoch,
    async request(url, schema) {
      if (!url.startsWith('/temporary-commands/facts?')) throw new Error('Unexpected GET ' + url);
      gets++; options.beforeFacts?.(); return schema.parse(structuredClone(data));
    },
    async postRaw(path, saved, schema) {
      const body = JSON.parse(saved.json);
      calls.push({ path, key: body.commandId, body: saved.json, epoch: saved.recoveryEpoch,
        sha256: createHash('sha256').update(saved.json).digest('hex'),
        persistedBeforePost: [...entries.values()].some(json => JSON.parse(json).json === saved.json) });
      if (!options.success) throw new TypeError('Synthetic response lost');
      options.beforeReceipt?.(); return schema.parse(receipt(body, options));
    },
  };
  return { data, calls, entries, storageEvents, options, get gets() { return gets; },
    owner: () => ({ userId, submissionId: data.submissionId, warehouseId: data.warehouseId }),
    setUser: value => { userId = value; }, setEpoch: value => { epoch = value; },
    restore() {
      for (const [key, descriptor] of previous) {
        if (descriptor) Object.defineProperty(globalThis, key, descriptor); else delete globalThis[key];
      }
      delete globalThis.temporaryRecoveryBoundary;
    } };
}

export function mountHook(useTemporaryCommand, owner) {
  let state;
  const app = renderer.createApp({ setup() { state = useTemporaryCommand(owner); return () => null; } });
  app.mount({});
  return { state, stop: () => app.unmount() };
}
