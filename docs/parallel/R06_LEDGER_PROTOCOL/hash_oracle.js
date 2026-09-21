// Independent Node canonical encoding and receipt-domain hash, never Java output.
const fs = require('node:fs');
const crypto = require('node:crypto');
const short = new Map([[8, '\\b'], [9, '\\t'], [10, '\\n'], [12, '\\f'], [13, '\\r']]);

function quote(value) {
  let output = '"';
  for (const ch of value) {
    const code = ch.codePointAt(0);
    if (ch === '"' || ch === '\\') output += '\\' + ch;
    else if (code < 32) output += short.get(code) ?? '\\u' + code.toString(16).toUpperCase().padStart(4, '0');
    else output += ch;
  }
  return output + '"';
}

function canonical(value) {
  if (value === null) return 'null';
  if (typeof value === 'string') return quote(value);
  if (typeof value === 'boolean') return String(value);
  if (typeof value === 'number' && Number.isSafeInteger(value)) return String(value);
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
  if (typeof value !== 'object') throw new Error('UNSUPPORTED_VALUE');
  return '{' + Object.keys(value).sort().map(k => quote(k) + ':' + canonical(value[k])).join(',') + '}';
}

function receipt(sample) {
  const text = canonical(sample.unsigned);
  const hash = crypto.createHash('sha256').update('ACRYLIC-R06/RECEIPT/1\n').update(text, 'utf8').digest('hex');
  return {id: sample.id, unsignedReceiptUtf8: text, unsignedReceiptSha256: hash};
}

const samples = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
process.stdout.write(JSON.stringify(samples.map(receipt)));
