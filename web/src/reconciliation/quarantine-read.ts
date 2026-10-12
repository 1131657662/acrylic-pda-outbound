import { getRaw, request } from '../api';
import { attachmentSchema } from './contracts';
import type { ReconciliationEvidence } from './contracts';
import { listSchema, preparationSchema } from './quarantine-contracts';
import type { QuarantineKind } from './quarantine-contracts';
import { loadPending, verifyResponse } from './pending';
import { downloadEvidence } from './download';
import { downloadQuarantineSource } from './quarantine-source';
import type { QuarantineState } from './quarantine-state';
export class QuarantineRead {
  constructor(private readonly state: QuarantineState) { }
  load = async (older: false | 'candidates' | 'cases' = false): Promise<void> => {
    const s = this.state;
    try {
      s.assertCurrent(); const saved = loadPending(s.owner); if (saved?.receipt) await verifyResponse(saved, saved.receipt); s.pending.value = saved;
      const review = s.session.identity?.permissions.includes('correction:approve');
      const cursor = older === 'candidates' && s.list.value?.nextCursor ? `&before=${s.list.value.nextCursor}`
        : older === 'cases' && s.list.value?.nextCaseCursor ? `&beforeCase=${s.list.value.nextCaseCursor}` : '';
      const data = await getRaw(`/corrections/quarantine?warehouseId=${s.owner.warehouse}&review=${review}${cursor}`, listSchema);
      s.assertCurrent(); s.list.value = older && s.list.value ? { ...s.list.value,
        ...(older === 'candidates' ? { candidates: [...s.list.value.candidates, ...data.candidates], nextCursor: data.nextCursor }
          : { cases: [...s.list.value.cases, ...data.cases], nextCaseCursor: data.nextCaseCursor }) } : data;
      s.error.value = ''; s.blocked.value = false;
    } catch (cause) { s.failed(cause); s.blocked.value = true; }
  };
  select = async (kind: QuarantineKind, id: string): Promise<void> => {
    const s = this.state; if (s.busy.value) return; s.busy.value = true; s.selected.value = null;
    try {
      const target = kind.endsWith('_DISCOVER') ? `baleId=${id}` : `caseId=${id}`;
      const data = await getRaw(`/corrections/quarantine/context?warehouseId=${s.owner.warehouse}&commandType=${kind}&${target}`, preparationSchema);
      s.assertCurrent();
      if (data.context.warehouseId !== s.owner.warehouse || data.expected.baleId !== data.context.baleId
          || data.expected.dispositionId !== data.context.dispositionId || data.expected.factsSha256 !== data.context.factsSha256
          || kind.endsWith('_DISCOVER') && data.context.baleId !== id
          || !kind.endsWith('_DISCOVER') && (data.context.case?.caseId !== id || data.expected.caseId !== id)) throw new Error('隔离上下文不匹配');
      s.selected.value = data; s.error.value = '';
    } catch (cause) { s.failed(cause); } finally { if (s.alive) s.busy.value = false; }
  };
  upload = async (file: File) => {
    const s = this.state; if (!file.size || file.size > 10 * 1024 * 1024) throw new Error('单文件必须为1字节至10MB');
    const bytes = await file.arrayBuffer(); const digest = await crypto.subtle.digest('SHA-256', bytes);
    const sha = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join(''); s.assertCurrent();
    return request(`/corrections/attachments/${crypto.randomUUID()}?warehouseId=${s.owner.warehouse}`, attachmentSchema,
      { method: 'PUT', body: bytes, headers: { 'Content-Type': 'application/octet-stream', 'X-Content-SHA256': sha } }, s.assertCurrent);
  };
  download = async (attachment: ReconciliationEvidence['attachments'][number]): Promise<void> => {
    const s = this.state; if (s.busy.value) return; s.busy.value = true;
    try { await downloadEvidence({ owner: s.owner, attachment, review: true, assertCurrent: s.assertCurrent }); }
    catch (cause) { s.failed(cause); } finally { if (s.alive) s.busy.value = false; }
  };
  source = async (kind: QuarantineKind): Promise<void> => {
    const s = this.state; if (s.busy.value || !s.selected.value) return; s.busy.value = true;
    try { await downloadQuarantineSource({ context: s.selected.value, kind, assertCurrent: s.assertCurrent }); }
    catch (cause) { s.failed(cause); } finally { if (s.alive) s.busy.value = false; }
  };
}
