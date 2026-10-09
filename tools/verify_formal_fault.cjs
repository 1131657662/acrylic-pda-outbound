const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');

const run = process.argv[2];
assert.match(run, /^[1-9][0-9]{0,3}$/);
const root = path.resolve(__dirname, `../.local/e2a-cross-20260925-${run}`);
const bytes = name => fs.readFileSync(path.join(root, name));
const read = name => JSON.parse(bytes(name));
const key = { key: bytes('private/server-public.der'), format: 'der', type: 'spki' };
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
function canonical(value) {
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
  if (value && typeof value === 'object') return '{' + Object.keys(value).sort().map(k => JSON.stringify(k) + ':' + canonical(value[k])).join(',') + '}';
  return JSON.stringify(value);
}
function signed(receipt, audience) {
  const [header, payload, signature] = receipt.signedReceipt.split('.');
  assert.equal(JSON.parse(Buffer.from(header, 'base64url')).alg, 'RS256');
  const text = Buffer.from(header + '.' + payload);
  assert.equal(crypto.verify('RSA-SHA256', text, key, Buffer.from(signature, 'base64url')), true);
  assert.equal(crypto.verify('RSA-SHA256', Buffer.concat([text, Buffer.from('x')]), key, Buffer.from(signature, 'base64url')), false);
  const claims = JSON.parse(Buffer.from(payload, 'base64url'));
  assert.equal(claims.iss, audience === 'acrylic-pda-receipt' ? 'acrylic-outbound' : 'acrylic-outbound-server');
  assert.equal(claims.aud, audience);
  const unsigned = { ...receipt }; delete unsigned.signedReceipt;
  assert.deepEqual(claims.receipt, unsigned);
  for (const value of Object.values(receipt.capabilities || {})) assert.equal(value, false);
}
const fence = read('approval-workflow/formal-fault-receipt.json');
signed(fence, 'acrylic-outbound-handover-receipt');
const command = read('approval-workflow/formal-fault-command.json');
assert.equal(bytes('approval-workflow/formal-fault-command.json').toString(), canonical(command));
assert.equal(hash(canonical(command)), fence.commandSha256);
assert.equal(command.commandId, fence.commandId);
assert.deepEqual(command.physicalControl, fence.businessDecision.physicalControl);
const decision = fence.businessDecision;
assert.equal(decision.oldDeviceAcknowledged, false);
assert.equal(fence.caseContext.stopState, 'REQUESTED');
assert.equal(decision.stopKind, 'FAULT_FENCE');
assert.equal(decision.archivedEvidence.sourceKind, 'FORMAL');
assert.notEqual(fence.actor.principalId, decision.source.operatorId);
assert.notEqual(fence.actor.principalId, fence.caseContext.requestedBy);
assert.equal(fence.actor.deviceId, null); assert.equal(fence.actor.sessionId, null);
assert.ok(fence.actor.permissionSnapshot.effectivePermissions.includes('order:handover'));
assert.ok(Date.parse(decision.physicalControl.controlledAt) <= Date.parse(fence.recordedAt));
const original = bytes(`storage/submissions/${decision.payloadSha256}.json`);
assert.equal(hash(original), decision.payloadSha256);
const body = JSON.parse(original);
assert.equal(original.toString(), canonical(body));
for (const name of ['changeRequestId', 'recoveryPackageId', 'captureHighWatermark', 'assignmentId', 'workRound'])
  assert.equal(body[name], decision.archivedEvidence[name]);
assert.equal(body.freezeDeviceId, decision.sourceDeviceId);
assert.equal(body.auditCoverage[0].throughSeq, decision.archivedEvidence.auditThroughSeq);
const frozen = read('android-frozen-submission.json');
for (const name of ['captures', 'scans', 'events']) assert.deepEqual(frozen[name].slice(0, body[name].length), body[name]);
assert.equal(BigInt(frozen.workRound), BigInt(body.workRound) + 1n);
assert.equal(frozen.captures.at(-1).deviceCaptureSeq, '1');
const receipt = read('formal-final-receipt.json'); signed(receipt, 'acrylic-pda-receipt');
assert.equal(receipt.status, 'SUCCEEDED'); assert.equal(receipt.payloadSha256, hash(canonical(frozen)));
assert.equal(receipt.actualCount, '2'); assert.equal(receipt.actualWeightGrams, '601500');
assert.equal(receipt.inventoryDeducted, true);
const snapshot = read('formal-restart-accounting.json');
const field = (table, row, name) => Buffer.from(row[table.columns.indexOf(name)], 'base64');
const uuid = id => Buffer.from(id.replaceAll('-', ''), 'hex');
const select = (name, column, id) => snapshot[name].rows.filter(row => field(snapshot[name], row, column).equals(uuid(id)));
const formal = select('handover_formal_fault_fence', 'case_id', fence.caseContext.caseId);
assert.equal(formal.length, 1);
assert.equal(field(snapshot.handover_formal_fault_fence, formal[0], 'command_sha256').toString(), fence.commandSha256);
const recall = select('order_change_request', 'id', body.changeRequestId)[0];
assert.equal(field(snapshot.order_change_request, recall, 'status').toString(), 'REQUESTED');
assert.equal(recall[snapshot.order_change_request.columns.indexOf('stop_receipt')], null);
assert.equal(recall[snapshot.order_change_request.columns.indexOf('resolution_receipt')], null);
assert.equal(select('handover_evidence', 'case_id', fence.caseContext.caseId).length, 0);
const dispatches = select('dispatch_record', 'submission_id', frozen.submissionId);
assert.equal(dispatches.length, 2);
const dispatchIds = dispatches.map(row => field(snapshot.dispatch_record, row, 'id').toString('hex'));
const ledger = snapshot.inventory_ledger.rows.filter(row => dispatchIds.includes(field(snapshot.inventory_ledger, row, 'dispatch_id').toString('hex')));
assert.equal(ledger.length, 2);
assert.equal(ledger.reduce((sum, row) => sum + BigInt(field(snapshot.inventory_ledger, row, 'weight_delta_g').toString()), 0n), -601500n);
assert.equal(select('order_result_version', 'order_id', frozen.source.sourceOrderId).length, 1);
assert.equal(select('dispatch_record', 'submission_id', fence.caseContext.submissionId).length, 0);
const oldDevice = select('device', 'id', decision.sourceDeviceId)[0];
assert.equal(field(snapshot.device, oldDevice, 'status').toString(), 'DISABLED');
const oldSessions = select('pda_session', 'device_id', decision.sourceDeviceId);
assert.ok(oldSessions.length > 0);
for (const row of oldSessions) assert.notEqual(row[snapshot.pda_session.columns.indexOf('revoked_at')], null);
const assignment = select('order_assignment', 'id', body.assignmentId)[0];
assert.equal(field(snapshot.order_assignment, assignment, 'status').toString(), 'REVOKED');
const offline = select('offline_authorization', 'assignment_id', body.assignmentId);
assert.ok(offline.length > 0);
for (const row of offline) assert.notEqual(row[snapshot.offline_authorization.columns.indexOf('revoked_at')], null);
const migrationBefore = read('formal-migration-before.json'), migrationAfter = read('formal-migration-after.json');
for (const [name, value] of Object.entries(migrationBefore.tables)) assert.deepEqual(migrationAfter.tables[name], value);
assert.deepEqual(migrationAfter.files, migrationBefore.files);
const factDir = path.join(root, 'approval-workflow/formal-fault-facts');
let comparedPairs = 0;
for (const name of fs.readdirSync(factDir).filter(name => name.endsWith('-before.json'))) {
  const after = name.replace(/-before\.json$/, '-after.json');
  assert.deepEqual(JSON.parse(fs.readFileSync(path.join(factDir, after))), JSON.parse(fs.readFileSync(path.join(factDir, name))));
  comparedPairs++;
}
assert.ok(comparedPairs >= 10);
assert.ok(fs.existsSync(path.join(root, 'test-completed.marker')));
console.log(JSON.stringify({ run, consumer: 'Independent native Node RSA/canonical/SQL snapshot',
  originalSha256: hash(original), frozenSha256: hash(canonical(frozen)), oldDeviceAcknowledged: false,
  ledgerCount: ledger.length, ledgerGrams: '-601500', comparedPairs, nonemptyMigrationPreserved: true,
  oldQualificationsRevoked: true, syntheticIdentities: true, independentToolNotPerson: true }, null, 2));
