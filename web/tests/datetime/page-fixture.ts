import type { Page } from '@playwright/test';
import { envelope } from '../pda-download-fixture';

export const stamp = '2026-09-20T04:37:05.987Z';
export const id = '11111111-1111-4111-8111-111111111111';
const secondId = '22222222-2222-4222-8222-222222222222';
const midnight = '2026-09-19T16:00:00Z';

function order(overrides: Record<string, unknown> = {}) {
  return { id, orderNo: 'TIME-001', source: 'WEB', status: 'CONTINUING', revision: 1, rowVersion: '1',
    vehicleNo: '展示验证', containerNo: '', plannedTotal: '1', createdBy: '测试人员',
    createdAt: stamp, updatedAt: stamp, ...overrides };
}

function continuation() {
  const common = { continuationId: id, previousSubmissionId: id, revision: 1, workRound: '2',
    nextOrderScanSeq: '2', retainedManifestSha256: 'a'.repeat(64), recoveryPackageId: id };
  return { ...common, changeRequestId: id, type: 'REOPEN_WORK', orderId: id, status: 'APPROVED',
    requestedDeviceId: id, dispositions: [], approval: { ...common, status: 'APPROVED',
      assignmentId: id, assignmentEpoch: '1', issuedRecoveryEpoch: '1', expiresAt: stamp,
      mayResumeScanning: false, stockReleased: false, signedReceipt: 'synthetic-display-only' } };
}

function orderResponses(): Record<string, unknown> {
  const recovery = { recoveryPackageId: id, payloadSha256: 'a'.repeat(64), actualCount: '0',
    actualWeightGrams: '0', captureHighWatermark: '1', auditThroughSeq: '1', issues: [] };
  return {
    '/orders': { items: [order(), order({ id: secondId, orderNo: 'TIME-002', createdAt: midnight })],
      total: 2, page: 1, pageSize: 20 },
    [`/orders/${id}`]: { order: order(), plan: { id, revision: 1, inventorySnapshotId: id,
      mappingRevision: '1', vehicleNo: '展示验证', containerNo: '', notes: '', plannedTotal: '1',
      reason: '合成展示数据', createdBy: '测试人员', createdAt: stamp }, lines: [] },
    [`/orders/${id}/pc`]: { work: null, products: [], submission: null },
    [`/orders/${id}/submission`]: { submission: null },
    [`/orders/${id}/publications`]: { items: [], nextCursor: null, hasMore: false },
    [`/orders/${id}/events`]: { items: [], nextCursor: null, hasMore: false },
    [`/orders/${id}/revisions`]: { items: [], nextCursor: null, hasMore: false },
    [`/orders/${id}/change-requests`]: { request: { changeRequestId: id, orderId: id, assignmentId: id,
      revision: 1, serverRecoveryEpoch: '1', rowVersion: '1', status: 'STOPPED', deviceId: id,
      reason: '时间展示测试', recovery, continuation: continuation() } },
    [`/recovery-packages/${id}`]: { recovery, captureSummary: {}, dispositions: [], scans: [] },
  };
}

function responses(): Record<string, unknown> {
  return {
    ...orderResponses(),
    '/me': { id, username: 'display', displayName: '时间展示测试',
      permissions: ['order:read', 'inventory:read', 'device:manage', 'submission:review'],
      warehouses: [{ id, code: 'DISPLAY', name: '隔离展示测试仓库' }] },
    '/inventory/imports': { items: [{ id, warehouseId: id, originalFilename: 'synthetic.txt',
      fileSha256: 'a'.repeat(64), parserVersion: '1', status: 'READY_FOR_REVIEW', totalRows: 1,
      validRows: 1, errorRows: 0, duplicateRows: 0, ignoredRows: 0, productCount: 1,
      totalWeightGrams: '1000', createdAt: stamp, publishedSnapshotId: null }], nextCursor: null, hasMore: false },
    '/devices': { items: [{ id, warehouseId: id, deviceCode: 'TIME-PDA', name: '展示测试设备',
      publicKey: 'synthetic', keySha256: 'a'.repeat(64), status: 'ACTIVE', rowVersion: '1',
      createdAt: stamp, lastSeenAt: stamp, temporaryOrderEnabled: false }], nextCursor: null },
    '/review-reminders': { items: [{ id, submissionId: id, orderId: id, orderNo: 'TIME-001',
      vehicleNo: '展示验证', submittedAt: midnight, dueAt: stamp, remindedAt: stamp }], nextCursor: null, hasMore: false },
  };
}

export async function mockPages(page: Page): Promise<string[]> {
  const unexpected: string[] = []; const data = responses();
  await page.route('**/api/v1/**', async route => {
    const request = route.request(); const path = new URL(request.url()).pathname.slice('/api/v1'.length);
    if (request.method() !== 'GET' || !Object.hasOwn(data, path)) {
      unexpected.push(`${request.method()} ${path}`); await route.abort(); return;
    }
    await route.fulfill({ json: envelope(data[path]) });
  });
  return unexpected;
}
