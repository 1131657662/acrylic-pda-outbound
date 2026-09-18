import { z } from 'zod';
import { entrySchema, workSchema } from './contracts';
export const dispositionAction = z.enum(['RETAIN', 'RELEASE', 'QUARANTINE']);
export const dispositionLine = z.object({ entryId: z.string().uuid(), expectedCycle: z.number(), action: dispositionAction, physicalEvidence: z.string() });
const historySchema = z.object({ id: z.string().uuid(), round: z.number(), status: z.enum(['STOPPED', 'APPLIED']), reason: z.string(), stoppedAt: z.string() });
const stoppedSchema = historySchema.extend({ snapshotSha256: z.string(), snapshot: z.object({ entries: z.array(entrySchema), operatorId: z.string().uuid() }) });
export const exceptionPlanSchema = z.object({ id: z.string().uuid(), status: z.enum(['PENDING', 'APPROVED', 'REJECTED', 'APPLIED']),
  payloadSha256: z.string(), approvedBy: z.string().uuid().nullable(), handoverEvidence: z.string().nullable(), requestedAt: z.string(), appliedAt: z.string().nullable(),
  body: z.object({ caseId: z.string().uuid(), snapshotSha256: z.string(), targetUserId: z.string().uuid(), targetUsername: z.string(), targetName: z.string(),
    outcome: z.enum(['CONTINUE', 'CANCEL']), reason: z.string(), lines: z.array(dispositionLine) }) });
export const exceptionViewSchema = z.object({ work: workSchema, stopped: stoppedSchema.nullable(), plans: z.array(exceptionPlanSchema), history: z.array(historySchema),
  reviews: z.array(z.object({ planId: z.string(), decision: z.enum(['APPROVE', 'REJECT']), reason: z.string(), actorName: z.string(), createdAt: z.string() })) });
export type ExceptionView = z.infer<typeof exceptionViewSchema>;
export type ExceptionPlan = z.infer<typeof exceptionPlanSchema>;
export type Stopped = z.infer<typeof stoppedSchema>;
export type DispositionLine = z.infer<typeof dispositionLine>;
export const dispositionLabels: Record<DispositionLine['action'], string> = { RETAIN: '本车保留', RELEASE: '卸货并释放', QUARANTINE: '卸货并隔离' };
export const planLabels: Record<ExceptionPlan['status'], string> = { PENDING: '待主管审核', APPROVED: '已批准，待本人交接', REJECTED: '已退回，占用保留', APPLIED: '交接已生效' };
