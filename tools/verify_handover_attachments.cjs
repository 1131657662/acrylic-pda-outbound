const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const run = Number(process.argv[2]);
assert.ok(Number.isInteger(run) && run >= 1140 && run < 10000);
const root = `D:/acrylic-pda-outbound/.local/e2a-cross-20260925-${run}`;
const read = relative => JSON.parse(fs.readFileSync(path.join(root, relative), 'utf8'));
const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const uuid = buffer => {
  const hex = buffer.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
};
const column = (table, row, name) => Buffer.from(row[table.columns.indexOf(name)], 'base64');
const text = (table, row, name) => column(table, row, name).toString();
const id = (table, row, name) => uuid(column(table, row, name));

function canonical(value) {
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
  if (value && typeof value === 'object')
    return '{' + Object.keys(value).sort().map(key => JSON.stringify(key) + ':' + canonical(value[key])).join(',') + '}';
  return JSON.stringify(value);
}

function verifySignature(receipt) {
  const [header, payload, signature] = receipt.signedReceipt.split('.');
  const key = { key: fs.readFileSync(path.join(root, 'private/server-public.der')), format: 'der', type: 'spki' };
  assert.equal(JSON.parse(Buffer.from(header, 'base64url')).alg, 'RS256');
  assert.equal(crypto.verify('RSA-SHA256', Buffer.from(header + '.' + payload), key, Buffer.from(signature, 'base64url')), true);
  const claims = JSON.parse(Buffer.from(payload, 'base64url'));
  const unsigned = { ...receipt }; delete unsigned.signedReceipt;
  assert.equal(claims.iss, 'acrylic-outbound-server'); assert.equal(claims.aud, 'acrylic-outbound-handover-receipt');
  assert.deepEqual(claims.receipt, unsigned);
  for (const flag of Object.values(receipt.capabilities)) assert.equal(flag, false);
}

function reservation(snapshot, receipt) {
  const table = snapshot.api_idempotency_request;
  const matches = table.rows.filter(row => id(table, row, 'resource_id') === receipt.receiptId);
  assert.equal(matches.length, 1);
  const row = matches[0];
  assert.equal(text(table, row, 'route_scope'), 'r07.handover.v1');
  assert.equal(text(table, row, 'idempotency_key'), receipt.commandId);
  assert.equal(text(table, row, 'request_sha256'), receipt.commandSha256);
  assert.equal(id(table, row, 'principal_id'), receipt.actor.principalId);
  return id(table, row, 'id');
}

function references(snapshot, receipt, requestId) {
  const table = snapshot.handover_attachment_use;
  const uses = table.rows.filter(row => id(table, row, 'request_id') === requestId)
    .sort((a, b) => Number(text(table, a, 'ordinal')) - Number(text(table, b, 'ordinal')));
  assert.equal(uses.length, 5);
  for (const [index, row] of uses.entries()) {
    assert.equal(text(table, row, 'ordinal'), String(index + 1));
    assert.equal(id(table, row, 'attachment_id'), receipt.businessDecision.attachments[index].attachmentId);
    assert.equal(id(table, row, 'case_id'), receipt.businessDecision.caseId);
    assert.equal(id(table, row, 'warehouse_id'), receipt.actor.warehouseId);
    assert.equal(id(table, row, 'uploaded_by'), receipt.actor.principalId);
  }
}

function original(snapshot, receipt, ref) {
  const row = snapshot.handover_attachment.find(row => uuid(Buffer.from(row[0], 'base64')) === ref.attachmentId);
  assert.ok(row);
  const value = index => Buffer.from(row[index], 'base64');
  assert.equal(uuid(value(1)), receipt.businessDecision.caseId);
  assert.equal(uuid(value(2)), receipt.actor.warehouseId);
  assert.equal(uuid(value(3)), receipt.actor.principalId);
  assert.equal(value(5).toString(), ref.evidenceSha256);
  for (const index of [6, 8]) assert.equal(value(index).toString(), ref.sizeBytes);
  assert.equal(value(9).toString(), ref.evidenceSha256);
  for (const file of [`temporary-handover-stop/fault-fence/attachment-${ref.attachmentId}.bin`,
                       `fault-attachment-restart/${ref.attachmentId}.bin`]) {
    const bytes = fs.readFileSync(path.join(root, file));
    assert.equal(bytes.length, Number(ref.sizeBytes)); assert.equal(hash(bytes), ref.evidenceSha256);
  }
}

assert.ok(fs.existsSync(path.join(root, 'test-completed.marker')));
const receipt = read('temporary-handover-stop/fault-fence/fault-receipt.json');
verifySignature(receipt);
const snapshot = read('temporary-handover-stop/fault-fence/fence-protection/before.json');
const table = snapshot.handover_command_receipt;
const row = table.rows.find(row => id(table, row, 'id') === receipt.receiptId);
assert.ok(row);
const bytes = column(table, row, 'canonical_command_bytes'); const command = JSON.parse(bytes);
assert.equal(bytes.toString(), canonical(command)); assert.equal(hash(bytes), receipt.commandSha256);
assert.deepEqual(command.attachments, receipt.businessDecision.attachments);
assert.equal(new Set(command.attachments.map(ref => ref.attachmentId)).size, 5);
assert.equal(command.attachments.reduce((sum, ref) => sum + Number(ref.sizeBytes), 0), 50 * 1024 * 1024);
references(snapshot, receipt, reservation(snapshot, receipt));
for (const ref of command.attachments) original(snapshot, receipt, ref);
console.log(JSON.stringify({ run, consumer: 'independent Node RSA/canonical/reservation/actual attachment bytes',
  references: 5, totalBytes: 50 * 1024 * 1024, syntheticIdentity: true, closedWriteRestartDownloads: true }, null, 2));
