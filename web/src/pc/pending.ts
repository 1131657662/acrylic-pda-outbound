import { z } from 'zod';
export const actionSchema = z.enum(['start', 'writer', 'entries', 'void', 'submit', 'review', 'resume']);
export type Action = z.infer<typeof actionSchema>;
const pendingSchema = z.object({ user: z.string().uuid(), warehouse: z.string().uuid(), order: z.string().uuid(), key: z.string().uuid(),
  epoch: z.string().regex(/^\d+$/), action: actionSchema, body: z.string() });
export type Pending = z.infer<typeof pendingSchema>;
export interface Context { user: string; warehouse: string; order: string }
const prefix = 'outbound.pc.request.v1:';
function matches(record: Pending, context: Context): boolean { return record.user === context.user && record.warehouse === context.warehouse && record.order === context.order; }
export function readPending(context: Context): Pending | null {
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i); if (!key?.startsWith(prefix)) continue;
    const text = localStorage.getItem(key); const parsed = pendingSchema.safeParse(JSON.parse(text ?? 'null'));
    if (!parsed.success) throw new Error('原PC操作恢复资料无法读取，请保留浏览器数据并联系主管。');
    if (matches(parsed.data, context)) return parsed.data;
  }
  return null;
}
export function savePending(record: Pending): void {
  const key = prefix + record.key; const text = JSON.stringify(pendingSchema.parse(record));
  localStorage.setItem(key, text);
  if (localStorage.getItem(key) !== text) throw new Error('原操作未能完整保存，本次请求未发送。');
}
export function clearPending(record: Pending): void { localStorage.removeItem(prefix + record.key); if (localStorage.getItem(prefix + record.key)) throw new Error('原操作恢复资料清理失败，请核对原回执。'); }
export const actionNames: Record<Action, string> = { start: '开始PC作业', writer: '切换录入窗口', entries: '计入本包', void: '撤销本次录入', submit: '提交结单', review: '审批当前提交', resume: '核验恢复作业' };
export const actionCodes: Record<Action, string> = { start: 'START', writer: 'WRITER', entries: 'ADD', void: 'VOID', submit: 'SUBMIT', review: 'REVIEW', resume: 'RESUME' };
