import { ApiError } from '../api';
import type { QuarantinePreparation, QuarantineKind } from './quarantine-contracts';
export async function downloadQuarantineSource(input: { context: QuarantinePreparation; kind: QuarantineKind; assertCurrent: () => void }): Promise<void> {
  input.assertCurrent(); const context = input.context.context;
  const target = input.kind.endsWith('_DISCOVER') ? `baleId=${context.baleId}` : `caseId=${context.case?.caseId}`;
  const response = await fetch(`/api/v1/corrections/quarantine/original-receipt?warehouseId=${context.warehouseId}&commandType=${input.kind}&${target}`,
    { credentials: 'same-origin', cache: 'no-store' });
  if (!response.ok) throw new ApiError(response.status, 'QUARANTINE_SOURCE_READ_FAILED', '原隔离依据读取失败，请保留依据重新核对。');
  const bytes = await response.arrayBuffer(); input.assertCurrent(); const digest = await crypto.subtle.digest('SHA-256', bytes);
  const hash = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
  if (hash !== context.facts.origin.originalReceiptSha256) throw new Error('原隔离回执字节不可核验。');
  const url = URL.createObjectURL(new Blob([bytes], { type: 'application/json' }));
  const link = document.createElement('a'); link.href = url; link.download = `${context.facts.origin.originalReceipt.receiptId}.json`;
  try { input.assertCurrent(); link.click(); } finally { URL.revokeObjectURL(url); }
}
