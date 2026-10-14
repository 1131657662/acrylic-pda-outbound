import { z } from 'zod';
import type { IndependentReview } from './contracts';
import { associationMemberSchema, type AssociationMember } from './association-members-contracts';
import type { Candidate } from './association-api';

export interface AssociationSelection { data: IndependentReview; candidates: Candidate[]; physical: string; conflict: string; exclusions: Record<string, string> }
export function selectedMembers(input: { main: IndependentReview; members: AssociationSelection[]; target: string; userId: string }): AssociationMember[] {
  const tasks = new Set([`${input.main.original.deviceId}/${input.main.original.taskId}`]);
  const relays = new Set([input.main.original.relayId]);
  return input.members.map(member => {
    const o = member.data.original; const task = `${o.deviceId}/${o.taskId}`;
    if (relays.has(o.relayId) || tasks.has(task) || o.operatorId === input.userId || o.warehouseId !== input.main.original.warehouseId ||
        o.task.vehicleNo !== input.main.original.task.vehicleNo || !member.candidates.some(row => row.id === input.target))
      throw new Error('额外任务须同仓同车、由其他人员采集、互不重复且包含同一目标。');
    if (member.data.summary.crossTaskConflicts !== '0' && !member.conflict.trim()) throw new Error('额外任务须引用覆盖全部冲突的原决定。');
    relays.add(o.relayId); tasks.add(task);
    return associationMemberSchema.parse({ relayId: o.relayId, contextSha256: member.data.contextSha256, physicalCheckId: member.physical,
      ...(member.conflict.trim() ? { conflictDecisionId: member.conflict.trim() } : {}),
      candidateExclusions: member.candidates.filter(row => row.id !== input.target).map(row => ({ orderId: row.id, reason: member.exclusions[row.id] ?? '' })) });
  });
}
const inventorySchema = z.object({ inventory: z.array(z.object({ original: z.object({ barcode: z.string().min(1) }),
  server: z.object({ baleId: z.uuid() }) })) });
export function checkSelectedPackageUniqueness(contexts: unknown[]): void {
  const barcodes = new Set<string>(); const bales = new Set<string>();
  for (const context of contexts) for (const row of inventorySchema.parse(context).inventory) {
    if (barcodes.has(row.original.barcode) || bales.has(row.server.baleId)) throw new Error('全组有效包重复，须先核对实物冲突。');
    barcodes.add(row.original.barcode); bales.add(row.server.baleId);
  }
}
