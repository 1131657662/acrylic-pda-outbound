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

function storage(blocked, events) {
  const entries = new Map();
  return {
    get length() { return entries.size; }, key: index => [...entries.keys()][index] ?? null,
    getItem: key => entries.get(key) ?? null,
    setItem(key, value) {
      events.push({ action: 'setItem', key, blocked });
      if (blocked) throw new Error('Synthetic browser storage quota denied');
      entries.set(key, String(value));
    },
    removeItem: key => entries.delete(key), clear: () => entries.clear(),
  };
}

export function boundary(options = {}) {
  const data = facts(); const calls = []; const storageEvents = []; let epoch = '1';
  const previous = ['localStorage', 'sessionStorage'].map(key => [key, Object.getOwnPropertyDescriptor(globalThis, key)]);
  for (const key of ['localStorage', 'sessionStorage'])
    Object.defineProperty(globalThis, key, { configurable: true, value: storage(options.blockedStorage, storageEvents) });
  const api = {
    epoch: () => epoch,
    async request(url, schema) {
      if (!url.startsWith('/temporary-commands/facts?')) throw new Error('Unexpected GET ' + url);
      return schema.parse(structuredClone(data));
    },
    async postRaw(path, body) {
      const text = JSON.stringify(body);
      calls.push({ path, key: body.commandId, body: text, sha256: createHash('sha256').update(text).digest('hex') });
      throw new TypeError('Synthetic response lost at transport boundary');
    },
  };
  globalThis.temporaryRecoveryBoundary = api;
  return { data, calls, storageEvents, setEpoch: value => { epoch = value; },
    restore() {
      for (const [key, descriptor] of previous) {
        if (descriptor) Object.defineProperty(globalThis, key, descriptor); else delete globalThis[key];
      }
      delete globalThis.temporaryRecoveryBoundary;
    } };
}

export function mountHook(useTemporaryCommand, data) {
  let state;
  const app = renderer.createApp({ setup() {
    state = useTemporaryCommand(() => ({ submissionId: data.submissionId, warehouseId: data.warehouseId }));
    return () => null;
  } });
  app.mount({});
  return { state, stop: () => app.unmount() };
}
