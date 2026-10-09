const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');

const run = process.argv[2];
assert.match(run, /^[1-9][0-9]{0,3}$/);
const root = path.resolve(__dirname, `../.local/e2a-cross-20260925-${run}`);
const bytes = name => fs.readFileSync(path.join(root, name));
const read = name => JSON.parse(bytes(name));
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
const uuid = value => value.replaceAll('-', '');
function rows(snapshot, name) {
  const table = snapshot[name];
  if (name === 'handover_attachment' && Array.isArray(table)) {
    const columns = ['id', 'case_id', 'warehouse_id', 'uploaded_by', 'upload_key', 'content_sha256',
      'size_bytes', 'created_at', 'actual_bytes', 'actual_sha256'];
    return table.map(row => Object.fromEntries(columns.map((column, i) => [column, Buffer.from(row[i], 'base64')])));
  }
  return table.rows.map(row => Object.fromEntries(table.columns.map((column, i) =>
    [column, row[i] === null ? null : Buffer.from(row[i], 'base64')])));
}

const before = read('formal-migration-before.json'), after = read('formal-migration-after.json');
for (const [name, table] of Object.entries(before.tables)) assert.deepEqual(after.tables[name], table);
assert.deepEqual(after.files, before.files);
const temp = rows(before.tables, 'handover_attachment');
assert.equal(temp.length, 1);
assert.equal(hash(temp[0].content_bytes), temp[0].content_sha256.toString());
assert.equal(BigInt(temp[0].size_bytes.toString()), BigInt(temp[0].content_bytes.length));
assert.equal(rows(before.tables, 'handover_attachment_use').length, 1);
const snapshot = read('formal-restart-accounting.json');
const command = read('approval-workflow/formal-fault-command.json');
const receipt = read('approval-workflow/formal-fault-receipt.json');
assert.deepEqual(receipt.businessDecision.attachments, command.attachments);
assert.equal(command.attachments.length, 1);
const ref = command.attachments[0];
const stored = rows(snapshot, 'handover_attachment').filter(row => row.id.toString('hex') === uuid(ref.attachmentId));
assert.equal(stored.length, 1);
const original = bytes('approval-workflow/formal-attachment.bin');
assert.equal(hash(original), ref.evidenceSha256);
assert.equal(stored[0].content_sha256.toString(), ref.evidenceSha256);
assert.equal(stored[0].actual_sha256.toString(), ref.evidenceSha256);
assert.equal(BigInt(ref.sizeBytes), BigInt(original.length));
assert.equal(stored[0].actual_bytes.toString(), ref.sizeBytes);
assert.equal(stored[0].size_bytes.toString(), ref.sizeBytes);
assert.equal(stored[0].case_id.toString('hex'), uuid(command.caseId));
assert.equal(stored[0].uploaded_by.toString('hex'), uuid(receipt.actor.principalId));
const uses = rows(snapshot, 'handover_attachment_use').filter(row => row.case_id.toString('hex') === uuid(command.caseId));
assert.equal(uses.length, 1);
assert.equal(uses[0].attachment_id.toString('hex'), uuid(ref.attachmentId));
assert.equal(uses[0].ordinal.toString(), '1');
const request = rows(snapshot, 'api_idempotency_request').filter(row => row.id.equals(uses[0].request_id));
assert.equal(request.length, 1);
assert.equal(request[0].resource_id.toString('hex'), uuid(receipt.receiptId));
assert.equal(request[0].idempotency_key.toString(), command.commandId);
const route = `/api/v1/handovers/${command.caseId}/attachments/${ref.attachmentId}`;
const downloads = bytes('approval-workflow/approval-http.tsv').toString().trim().split('\n')
  .map(line => line.replace(/\r$/, '').split('\t')).filter(row => row[0] === 'GET' && row[1] === route && row[2] === '200');
assert.ok(downloads.length >= 3);
for (const row of downloads) assert.deepEqual(bytes(`approval-workflow/${row[3]}`), original);
assert.ok(fs.existsSync(path.join(root, 'approval-workflow/formal-attachment-restart.marker')));
assert.ok(fs.existsSync(path.join(root, 'approval-workflow/formal-binding.marker')));
assert.ok(fs.existsSync(path.join(root, 'test-completed.marker')));
console.log(JSON.stringify({ run, consumer: 'Independent Node binary/SQL reference/HTTP download',
  nonemptyTempAttachmentMigration: true, formalOriginalSha256: hash(original),
  bytes: original.length, orderedSignedReference: true, successfulDownloads: downloads.length,
  afterRestart: true, syntheticIdentities: true, independentToolNotPerson: true }, null, 2));
