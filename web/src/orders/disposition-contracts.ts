import { z } from 'zod';

const cycle = z.string().regex(/^[1-9][0-9]*$/);
export const dispositionSchema = z.object({ captureId: z.string().uuid(), baleId: z.string().uuid(), barcode: z.string(),
  expectedDispatchCycle: cycle, action: z.enum(['RELEASE', 'QUARANTINE']), approvedEvidenceId: z.string().uuid() });
export const physicalEvidenceSchema = dispositionSchema.omit({ barcode: true }).extend({
  physicalEvidence: z.string(), recoveryPackageId: z.string().uuid() });
export const physicalEvidenceResponse = z.object({ stockReleased: z.literal(false), evidence: z.array(physicalEvidenceSchema) });
export type Disposition = z.infer<typeof dispositionSchema>;
export type PhysicalEvidence = z.infer<typeof physicalEvidenceSchema>;
export interface DispositionScan { captureId: string; baleId: string; barcode: string; expectedDispatchCycle: string; status: 'ACTIVE' | 'VOID' }
export const dispositionLabels = { RELEASE: '已核验卸货', QUARANTINE: '已核验隔离' };
export const pendingDispositionSchema = z.object({ path: z.string(), body: z.string(), key: z.string().uuid(), epoch: cycle });
