import type { QuarantineHistory } from './quarantine-history-contracts';
export async function historyReceiptBytes(input: { item: QuarantineHistory; receipt: 'return' | 'restoration'; sourceQuery: string }): Promise<Uint8Array<ArrayBuffer>> {
  const response = await fetch(`/api/v1/corrections/quarantine/original-receipt?${input.sourceQuery}&historyDispatchId=${input.item.dispatch.dispatchId}&historyReceiptType=${input.receipt}`,
    { credentials: 'same-origin', cache: 'no-store' });
  if (!response.ok) throw new Error('历史原批准回执读取失败，请保留依据重新核对。');
  const expected = input.receipt === 'return' ? input.item.returnReceiptSha256 : input.item.stockRestorationReceiptSha256;
  const bytes = new Uint8Array(await response.arrayBuffer());
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  if (Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('') !== expected)
    throw new Error('历史原批准回执字节不可核验，请重新读取原事实。');
  return bytes;
}
export async function downloadHistoryReceipt(input: { item: QuarantineHistory; receipt: 'return' | 'restoration'; sourceQuery: string; assertCurrent: () => void }): Promise<void> {
  input.assertCurrent(); const bytes = await historyReceiptBytes(input); input.assertCurrent();
  const original = input.receipt === 'return' ? input.item.reopening.approvalReceipt : input.item.stockRestorationReceipt;
  const url = URL.createObjectURL(new Blob([bytes], { type: 'application/json' }));
  const link = document.createElement('a'); link.href = url; link.download = `${original.receiptId}.json`;
  try { input.assertCurrent(); link.click(); } finally { URL.revokeObjectURL(url); }
}
