// Internal conversion worker. Invoke through python -m tools.pda_rescue.cli --xlsx to reverify the original package.
import fs from 'node:fs/promises';
import path from 'node:path';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';

const [input, output] = process.argv.slice(2);
if (!input || !output) throw new Error('Expected verified.json and a new output.xlsx');
const require = createRequire(process.env.PDA_RESCUE_NODE_MODULES
  ? path.join(process.env.PDA_RESCUE_NODE_MODULES, '../package.json') : import.meta.url);
const { Workbook, SpreadsheetFile } = await import(pathToFileURL(require.resolve('@oai/artifact-tool')).href);
const report = JSON.parse(await fs.readFile(input, 'utf8'));
if (report.manifest?.purpose !== 'PDA_READONLY_RESCUE_V1') throw new Error('Unknown rescue format');
const { order, scans, captures, events, lines, dispositions } = report.facts;
if ([scans, captures, events, lines].some(rows => rows.length > 100000)) throw new Error('Too many rows');
const workbook = Workbook.create();
let tableCount = 0;
const expected = [];

function literal(value) {
  if (typeof value !== 'string') return value;
  const safe = value.replace(/[\x00-\x1f\x7f]/g, char => `\\u${char.charCodeAt(0).toString(16).padStart(4, '0')}`);
  if (safe.length > 32767) throw new Error('Cell text exceeds Excel limit; original JSON remains available');
  return safe.startsWith('=') ? `'${safe}` : safe;
}
function exact(text) {
  if (!/^-?(0|[1-9][0-9]*)$/.test(text)) throw new Error('Invalid integer');
  const value = BigInt(text);
  return value >= -9007199254740991n && value <= 9007199254740991n ? Number(value) : text;
}
function table({name, headings, rows, widths}) {
  const sheet = workbook.worksheets.add(name);
  const last = Math.max(5, rows.length + 4);
  const area = sheet.getRangeByIndexes(0, 0, last, headings.length);
  sheet.showGridLines = false;
  area.format.font = { name: 'Arial', size: 11, color: '#1F2937' };
  area.format.rowHeight = 24; area.format.verticalAlignment = 'center';
  area.setNumberFormat('@');
  sheet.getRange('A2').values = [[name]];
  sheet.getRange('A2').format.font = { name: 'Arial', size: 15, bold: true };
  const header = sheet.getRangeByIndexes(3, 0, 1, headings.length);
  header.values = [headings];
  header.format = { fill: '#294862', font: { name: 'Arial', size: 11, bold: true, color: '#FFFFFF' }, horizontalAlignment: 'center' };
  if (rows.length) sheet.getRangeByIndexes(4, 0, rows.length, headings.length).values = rows.map(row => row.map(literal));
  expected.push({ name, rows: rows.map(row => row.map(value => typeof value === 'string'
    ? value.replace(/[\x00-\x1f\x7f]/g, char => `\\u${char.charCodeAt(0).toString(16).padStart(4, '0')}`) : value)) });
  rows[0]?.forEach((value, col) => { if (typeof value === 'number') sheet.getRangeByIndexes(4, col, rows.length, 1).setNumberFormat('0'); });
  widths.forEach((width, col) => sheet.getRangeByIndexes(0, col, last, 1).format.columnWidth = width);
  sheet.freezePanes.freezeRows(4);
  sheet.tables.add(`A4:${String.fromCharCode(64 + headings.length)}${last}`, true, `RescueTable${++tableCount}`);
  return sheet;
}
const summary = report.manifest.summary;
const disposed = new Map(dispositions.map(row => [row.captureId, row.action]));
const source = report.sourceTrusted ? '已按包外登记公钥和范围核验' : '仅完整性通过，未核验登记来源';
const summaryRows = [
  ['原订单', order.orderId], ['本机订单', order.localOrderId], ['车号', order.vehicleNo ?? ''],
  ['来源', order.source], ['本机状态', order.state], ['来源核验', source],
  ['业务确认', '未在此工具核验，不等于出库确认或放行'],
  ['有效包数', exact(summary.active)], ['撤销包数', exact(summary.void)], ['原处置包数', exact(summary.disposed)], ['未决输入', exact(summary.pending)],
  ['失败输入', exact(summary.failed)], ['清单有效整数克', exact(summary.weightGrams)],
  ['导出时间', '本机导出时刻未获服务器证实'], ['原设备', order.deviceId], ['原操作员', order.operatorId], ['仓库', order.warehouseId],
];
if (order.source === 'INDEPENDENT') {
  const original = report.facts.independent.source;
  summaryRows.splice(6, 0, ['对账状态', '独立采集，待主管核对原授权及实物并人工关联'], ['服务端采集授权', '尚未核验，不能自动提交或记账']);
  summaryRows.push(['原采集凭据ID', original.credential.credentialId], ['原采集凭据摘要', original.credential.credentialSha256],
    ['原SKU摘要', original.task.skuSha256], ['原采集期限起', original.credential.issuedAt], ['原采集期限止', original.credential.expiresAt]);
}
table({name: '核对汇总', headings: ['项目', '原快照事实'], rows: summaryRows, widths: [25, 80]});
const scanSheet = table({name: '扫码原顺序', headings: ['原扫码序号', '采集ID', '条码', '产品代码', '型号', '品名', '原重量', '整数克', '状态', '保存时间', '原处置'],
  rows: scans.map(row => [row.orderScanSeq, row.captureId, row.barcode, row.deliveryCode, row.model, row.name,
    row.rawWeight, exact(row.weightGrams), row.status, row.savedAt, disposed.get(row.captureId) ?? '']), widths: [24, 42, 20, 14, 24, 35, 16, 22, 14, 29, 20]});
scanSheet.getRangeByIndexes(4, 0, Math.max(1, scans.length), 7).setNumberFormat('@');
scanSheet.getRangeByIndexes(4, 7, Math.max(1, scans.length), 1).setNumberFormat('0');
scanSheet.getRangeByIndexes(4, 9, Math.max(1, scans.length), 1).setNumberFormat('@');
const scansByCode = new Map();
for (const row of scans) {
  if (!scansByCode.has(row.deliveryCode)) scansByCode.set(row.deliveryCode, []);
  scansByCode.get(row.deliveryCode).push(row);
}
const productRows = lines.map(line => {
  const rows = scansByCode.get(line.deliveryCode) ?? [];
  const active = rows.filter(row => row.status === 'ACTIVE' && !disposed.has(row.captureId));
  const grams = active.reduce((sum, row) => sum + BigInt(row.weightGrams), 0n);
  return [line.deliveryCode, exact(line.plannedCount), active.length, rows.filter(row => row.status === 'VOID').length,
    rows.filter(row => disposed.has(row.captureId)).length,
    exact((BigInt(line.plannedCount) - BigInt(active.length)).toString()), exact(grams.toString())];
});
table({name: '产品核对', headings: ['产品代码', '计划包数', '有效包数', '撤销包数', '处置包数', '差额', '有效整数克'], rows: productRows, widths: [18, 20, 20, 20, 20, 20, 25]});
const pending = captures.filter(row => !['COMMITTED', 'SAVED', 'VOID'].includes(row.state));
table({name: '采集未决与失败', headings: ['原扫码序号', '采集ID', '状态', '原输入', '原因'],
  rows: pending.map(row => [row.orderScanSeq, row.id, row.state, row.rawInput ?? '', row.error ?? '']), widths: [24, 42, 25, 40, 80]});
table({name: '原审计', headings: ['原位置', '审计ID', '类型', '原因'],
  rows: events.map(row => [row.orderPosition, row.id, row.type, row.detail ?? '']), widths: [24, 42, 35, 80]});
workbook.recalculate();
const check = await workbook.inspect({ kind: 'table', range: '扫码原顺序!A4:J7', include: 'values,formulas', maxChars: 2200 });
await fs.writeFile(`${output}.inspection.ndjson`, check.ndjson);
for (const [name, range] of [['核对汇总', `A1:B${summaryRows.length + 4}`], ['扫码原顺序', 'A1:J7'], ['产品核对', 'A1:F7'], ['采集未决与失败', 'A1:E7'], ['原审计', 'A1:D8']]) {
  const preview = await workbook.render({ sheetName: name, range, scale: 1.25, format: 'png' });
  await fs.writeFile(`${output}.${name}.png`, new Uint8Array(await preview.arrayBuffer()));
}
try { await fs.stat(output); throw new Error('Output already exists'); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
await (await SpreadsheetFile.exportXlsx(workbook)).save(output);
await fs.writeFile(`${output}.expected.json`, JSON.stringify(expected), { flag: 'wx' });
