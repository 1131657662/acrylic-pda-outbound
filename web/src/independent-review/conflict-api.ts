import { request } from '../api';
import { z } from 'zod';
import { reviewSchema, type IndependentReview } from './contracts';
import { conflictResultSchema, verifyConflict, type ConflictResult, type ConflictEvidence } from './conflict-contracts';
import { loadConflict, saveConflict } from './conflict-storage';
import { jsonDigest, physicalResultSchema, type PhysicalOwner } from './physical-contracts';
export type Peer = Extract<IndependentReview, { section: 'peers' }>['items'][number];
function path(owner: PhysicalOwner, suffix = ''): string {
  return `/original-relays/${owner.relayId}/conflict-decisions${suffix}?warehouseId=${owner.warehouseId}`;
}
export async function readConflict(owner: PhysicalOwner, id?: string): Promise<ConflictResult | null> {
  const command = loadConflict(owner).command; const decision = id ?? command?.receipt?.decisionId;
  if (!decision) return null;
  z.uuid().parse(decision);
  const result = await request(path(owner, `/${decision}`), conflictResultSchema);
  await verifyConflict(result, owner, id ? undefined : command?.body);
  if (result.receipt.decisionId !== decision || !id && command?.receipt && JSON.stringify(result.receipt) !== JSON.stringify(command.receipt))
    throw new Error('服务器与原冲突回执不一致。');
  return result;
}
export async function sendConflict(owner: PhysicalOwner, assertCurrent: () => void): Promise<void> {
  const state = loadConflict(owner); const command = state.command;
  if (!command) throw new Error('尚无已保存的原冲突请求。');
  const result = await request(path(owner), conflictResultSchema, { method: 'POST', body: JSON.stringify(command.body),
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key, 'X-Server-Recovery-Epoch': command.epoch } }, assertCurrent);
  await verifyConflict(result, owner, command.body); assertCurrent();
  if (result.receipt.supervisorId !== owner.userId) throw new Error('原冲突主管与发送身份不一致。');
  command.receipt = result.receipt; saveConflict(state);
}
export async function allConflictPeers(data: IndependentReview, assertCurrent: () => void): Promise<Peer[]> {
  const rows: Peer[] = []; const seen = new Set<string>(); let cursor = '';
  do {
    const query = new URLSearchParams({ warehouseId: data.original.warehouseId, section: 'peers', limit: '200', cursor });
    const page = await request(`/original-relays/${data.original.relayId}/independent-reconciliation?${query}`, reviewSchema); assertCurrent();
    if (page.section !== 'peers' || page.contextSha256 !== data.contextSha256 || page.original.relayId !== data.original.relayId ||
        page.original.warehouseId !== data.original.warehouseId || page.total !== data.summary.peerTaskTotal)
      throw new Error('完整冲突依据已变化，请刷新原件核对。');
    rows.push(...page.items); cursor = page.nextCursor ?? '';
    if (rows.length > 200 || page.hasMore !== Boolean(cursor) || cursor && seen.has(cursor)) throw new Error('冲突分页不完整或超过上限。');
    seen.add(cursor);
  } while (cursor);
  if (rows.length !== Number(data.summary.peerTaskTotal) || new Set(rows.map(row => `${row.deviceId}/${row.taskId}`)).size !== rows.length)
    throw new Error('完整冲突任务集合不一致。');
  if (rows.some(row => row.closureDivergence)) throw new Error('结束分歧须单独核对，不得忽略原件。');
  const groups = rows.filter(row => row.crossTaskConflict);
  if (!groups.length || groups.some(row => !row.originals.length || row.originals.some(o => o.sourceTrust !== 'TRUSTED' || !o.completeBoundaryVerified)))
    throw new Error('须有完整可信的全部重叠任务原件。');
  return groups;
}
export async function checkConflictPhysical(input: {
  owner: PhysicalOwner; body: ConflictEvidence; peers: Peer[]; assertCurrent: () => void;
}): Promise<void> {
  const { owner, body, peers, assertCurrent } = input;
  if (body.peerChecks.length !== peers.length) throw new Error('实物引用须覆盖全部重叠任务。');
  const selected = await physical(owner, body.physicalCheckId); assertCurrent();
  if (selected.receipt.contextSha256 !== body.contextSha256) throw new Error('选定方实物截点已变化。');
  observations(selected, peers.flatMap(peer => peer.overlappingBarcodes), 'LOADED');
  const tasks = new Set<string>();
  for (const reference of body.peerChecks) {
    const peer = peers.find(row => row.originals.some(o => o.relayId === reference.relayId));
    if (!peer || tasks.has(peer.taskId + '/' + peer.deviceId)) throw new Error('冲突引用任务重复或不是集合成员。');
    tasks.add(peer.taskId + '/' + peer.deviceId);
    const result = await physical({ ...owner, relayId: reference.relayId }, reference.physicalCheckId); assertCurrent();
    if (result.receipt.taskId !== peer.taskId) throw new Error('其他方实物引用任务不一致。');
    observations(result, peer.overlappingBarcodes, 'UNLOADED');
  }
}
function observations(result: z.infer<typeof physicalResultSchema>, barcodes: string[], status: 'LOADED' | 'UNLOADED'): void {
  const inventory = z.array(z.object({ captureId: z.uuid(), barcode: z.string() })).parse(result.originalContext.inventory);
  for (const barcode of new Set(barcodes)) {
    const capture = inventory.find(row => row.barcode === barcode);
    const observation = result.evidence.packages.find(row => row.captureId === capture?.captureId);
    if (!observation || observation.status !== status || status === 'UNLOADED' && !observation.note.trim())
      throw new Error('重码须选定方已装载、其他各方已卸下并说明，不能用未找到代替。');
  }
}
async function physical(owner: PhysicalOwner, id: string): Promise<z.infer<typeof physicalResultSchema>> {
  const result = await request(`/original-relays/${owner.relayId}/physical/checks/${id}?warehouseId=${owner.warehouseId}`, physicalResultSchema);
  const r = result.receipt; const original = z.object({ operatorId: z.uuid(), relayId: z.uuid(), warehouseId: z.uuid(), taskId: z.uuid() }).parse(result.originalContext.original);
  if (r.receiptId !== id || r.relayId !== owner.relayId || r.warehouseId !== owner.warehouseId || original.relayId !== r.relayId ||
      original.warehouseId !== r.warehouseId || original.taskId !== r.taskId || original.operatorId === owner.userId ||
      r.contextSha256 !== await jsonDigest(result.originalContext) || r.contextSha256 !== result.evidence.contextSha256 ||
      r.requestSha256 !== await jsonDigest(result.evidence)) throw new Error('各方实物引用或主管分离核对失败。');
  return result;
}
