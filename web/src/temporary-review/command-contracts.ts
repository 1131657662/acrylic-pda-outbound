import { z } from 'zod';

const count = z.string().regex(/^[0-9]+$/);
const positive = z.string().regex(/^[1-9][0-9]*$/);
const hash = z.string().regex(/^[a-f0-9]{64}$/);

/** Same five flags as the command contract: every one stays false until the owners open the chain. */
export const commandCapabilitiesSchema = z.object({
  writeConfirmationAvailable: z.literal(false), releaseAllowed: z.literal(false), mayResumeScanning: z.literal(false),
  stockReleased: z.literal(false), localDeletionAllowed: z.literal(false),
});

export const commandFactsSchema = z.object({
  expected: z.object({
    canonicalOrderId: z.string().uuid().nullable(), mappingVersion: count.nullable(),
    currentSubmissionId: z.string().uuid().nullable(), scopeRound: positive.nullable(), slotVersion: positive.nullable(),
    decisionVersion: count.nullable(), receivedPayloadSha256: hash.nullable(), declaredUploadSha256: hash.nullable(),
    planSha256: hash.nullable(), targetRevision: positive.nullable(), userAuthVersion: positive,
    candidateContextSha256: z.null(), evidenceSetId: z.null(), evidenceSetSha256: z.null(), verificationId: z.null(),
    verificationVersion: z.null(), verificationAlgorithmVersion: z.null(), dispositionManifestSha256: z.null(),
    dispositionManifestVersion: z.null(), retainedManifestSha256: z.null(), retainedManifestVersion: z.null(),
  }),
  warehouseId: z.string().uuid(), submissionId: z.string().uuid(), workRound: positive, authorizationId: z.string().uuid(),
  freezeDeviceId: z.string().uuid(), sourceRecoveryEpoch: count,
  source: z.object({ kind: z.literal('TEMP'), creatorDeviceId: z.string().uuid(), clientOrderId: z.string().uuid() }),
});

export const commandReceiptSchema = z.object({
  status: z.string(), commandType: z.string(), commandId: z.string().uuid(), capabilities: commandCapabilitiesSchema,
}).catchall(z.unknown());

export type CommandFacts = z.infer<typeof commandFactsSchema>;
export type CommandReceipt = z.infer<typeof commandReceiptSchema>;
export type CommandKind = 'LINK' | 'NORMAL_CONFIRM' | 'DUPLICATE_EXCLUDE';

export function commandStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    RECORDED: '已记录原回执', REJECTED: '已拒绝', UNSUPPORTED: '尚未开放', CONFLICT: '原键冲突',
    STALE: '上下文已变化', INVALID: '命令无效',
  };
  return labels[status] ?? status;
}

export function commandKindLabel(kind: string): string {
  const labels: Record<string, string> = { LINK: '人工关联正式单', NORMAL_CONFIRM: '正常确认出库', SHORT_APPROVE: '短装批准', DUPLICATE_EXCLUDE: '逐项排除候选' };
  return labels[kind] ?? kind;
}
