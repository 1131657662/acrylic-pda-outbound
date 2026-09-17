// Desktop verification workbook only; never runs on the PDA.
import fs from 'node:fs/promises';
import path from 'node:path';
import { Workbook, SpreadsheetFile } from '@oai/artifact-tool';

const [input, output] = process.argv.slice(2);
if (!input || !output) throw new Error('Usage: node export_xlsx.mjs EXPORT_DIR OUTPUT.xlsx');
const readRows = async name => (await fs.readFile(path.join(input, name), 'utf8'))
  .split(/\r?\n/).filter(Boolean).map(line => JSON.parse(line));
const scans = await readRows('scans.ndjson');
const audit = await readRows('audit.ndjson');
const manifest = JSON.parse(await fs.readFile(path.join(input, 'manifest.json'), 'utf8'));
if (manifest.purpose !== 'DEMO_EVIDENCE_NOT_CLOUD_SUBMISSION') throw new Error('Unrecognized input');
for (const scan of scans) {
  if (![scan.orderScanSeq, scan.deviceCaptureSeq, Number(scan.weightGrams)].every(Number.isSafeInteger)) {
    throw new RangeError('Value exceeds exact numeric export range; no rounded workbook is generated.');
  }
}
if (scans.reduce((sum, scan) => sum + BigInt(scan.weightGrams), 0n) > BigInt(Number.MAX_SAFE_INTEGER)) {
  throw new RangeError('Total grams exceeds exact numeric export range.');
}
const workbook = Workbook.create();
const literal = value => typeof value === 'string' && value.startsWith('=') ? `'${value}` : value;
const serialDate = ms => ms / 86400000 + 25569 + 8 / 24;

function frame(name, title, headings, matrix, tableName) {
  const sheet = workbook.worksheets.add(name);
  const end = Math.max(6, matrix.length + 5);
  sheet.showGridLines = false;
  const all = sheet.getRangeByIndexes(0, 0, end, headings.length);
  all.format.font = { name: 'Arial', size: 11, color: '#1F2937' };
  all.format.rowHeight = 22;
  all.format.columnWidth = 18;
  sheet.getRange('A2').values = [[title]];
  sheet.getRange('A2').format.font = { name: 'Arial', size: 15, bold: true };
  sheet.getRangeByIndexes(4, 0, 1, headings.length).values = [headings];
  if (matrix.length) sheet.getRangeByIndexes(5, 0, matrix.length, headings.length).values = matrix.map(r => r.map(literal));
  const header = sheet.getRangeByIndexes(4, 0, 1, headings.length);
  header.format = { fill: '#294862', font: { name: 'Arial', color: '#FFFFFF', bold: true }, horizontalAlignment: 'center' };
  sheet.freezePanes.freezeRows(5);
  sheet.freezePanes.freezeColumns(2);
  sheet.tables.add(`A5:${String.fromCharCode(64 + headings.length)}${end}`, true, tableName);
  return { sheet, end };
}

function scanSheet(name, records, tableName) {
  const data = records.map(s => [s.orderScanSeq, s.barcode, s.deliveryCode, s.rawWeight,
    Number(s.weightGrams) / 1000, Number(s.weightGrams), s.originDeviceId, s.deviceCaptureSeq,
    serialDate(s.receivedAt), s.expectedDispatchCycle, s.inputSource, s.status, s.confirmationPolicy ?? 'MANUAL']);
  const { sheet, end } = frame(name, `${name}（Demo 测试数据）`,
    ['扫码序号', '条码', '产品代码', 'sku原始重量', '净重(kg)', '净重(g)', '原设备', '设备序号', '接收时间(北京时间)', '出库周期', '输入来源', '记录状态', '确认策略'], data, tableName);
  sheet.getRange('A3:D3').values = [['记录数', null, '净重(kg)', null]];
  sheet.getRange('B3').formulas = [[`=COUNT(A6:A${end})`]];
  sheet.getRange('D3').formulas = [[`=SUM(F6:F${end})/1000`]];
  const totalGrams = records.reduce((sum, s) => sum + BigInt(s.weightGrams), 0n);
  sheet.getRange('D3').setNumberFormat(totalGrams % 1000n === 0n ? '#,##0' : '#,##0.###');
  sheet.getRange(`B6:D${end}`).setNumberFormat('@');
  sheet.getRange(`C6:C${end}`).setNumberFormat('000');
  sheet.getRange(`A6:A${end}`).format.horizontalAlignment = 'center';
  sheet.getRange('B3').format.horizontalAlignment = 'center';
  if (records.length) sheet.getRange(`E6:E${end}`).format.numberFormat =
    records.map(s => [BigInt(s.weightGrams) % 1000n === 0n ? '0' : '0.###']);
  sheet.getRange(`F6:H${end}`).setNumberFormat('0');
  sheet.getRange(`I6:I${end}`).setNumberFormat('yyyy-mm-dd hh:mm:ss.000');
  sheet.getRange(`A1:A${end}`).format.columnWidth = 12;
  sheet.getRange(`B1:B${end}`).format.columnWidth = 20;
  sheet.getRange(`G1:G${end}`).format.columnWidth = 42;
  sheet.getRange(`I1:I${end}`).format.columnWidth = 29;
  sheet.getRange(`M1:M${end}`).format.columnWidth = 30;
  return sheet;
}

const ordered = [...scans].sort((a, b) => a.orderScanSeq - b.orderScanSeq);
scanSheet('扫码明细', ordered.filter(s => s.status === 'ACTIVE'), 'ActiveScans');
scanSheet('原始事实', ordered, 'AllScans');
const { sheet: auditSheet, end: auditEnd } = frame('审计日志', '审计日志（Demo 测试数据）',
  ['原设备', '审计序号', '候选ID', '操作', '原因', '时间(北京时间)'],
  audit.map(a => [a.originDeviceId, a.localSeq, a.captureId, a.type, a.reason, serialDate(a.wallMs)]), 'AuditEvents');
auditSheet.getRange(`A1:A${auditEnd}`).format.columnWidth = 42;
auditSheet.getRange(`C1:C${auditEnd}`).format.columnWidth = 42;
auditSheet.getRange(`D1:D${auditEnd}`).format.columnWidth = 25;
auditSheet.getRange(`E1:E${auditEnd}`).format.columnWidth = 70;
auditSheet.getRange(`F1:F${auditEnd}`).format.columnWidth = 29;
auditSheet.getRange(`F6:F${auditEnd}`).setNumberFormat('yyyy-mm-dd hh:mm:ss.000');
auditSheet.getRange('A3').values = [[`证据订单：${manifest.orderId}`]];
workbook.recalculate();
console.log((await workbook.inspect({ kind: 'table', range: '扫码明细!A3:F8', include: 'values,formulas', maxChars: 2500 })).ndjson);
console.log((await workbook.inspect({ kind: 'match', searchTerm: '#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!', options: { useRegex: true, maxResults: 10 } })).ndjson);
await fs.mkdir(path.dirname(output), { recursive: true });
for (const name of ['扫码明细', '原始事实', '审计日志']) {
  const preview = await workbook.render({ sheetName: name, range: name === '审计日志' ? 'A1:F10' : 'A1:F12', scale: 1.5, format: 'png' });
  await fs.writeFile(`${output}.${name}.png`, new Uint8Array(await preview.arrayBuffer()));
}
await (await SpreadsheetFile.exportXlsx(workbook)).save(output);
console.log(`Exported ${output}; validate using verify_xlsx.py before accepting the result.`);
