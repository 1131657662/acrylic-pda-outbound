import { z } from 'zod';
import { commandFactsSchema, commandReceiptSchema } from './command-contracts';

const positive = z.string().regex(/^[1-9][0-9]*$/);
const hash = z.string().regex(/^[a-f0-9]{64}$/);
export const dispositionCommandFactsSchema = commandFactsSchema.extend({
  workRound: z.literal('1'),
  expected: commandFactsSchema.shape.expected.extend({
    currentSubmissionId: z.uuid(), scopeRound: z.literal('1'), evidenceSetId: z.uuid(), evidenceSetSha256: hash,
    verificationId: z.uuid(), verificationVersion: positive, verificationAlgorithmVersion: positive,
  }),
});
export const dispositionFactsSchema = z.object({
  status: z.enum(['ABORTED', 'REJECTED']), checkedAt: z.iso.datetime(), facts: dispositionCommandFactsSchema,
  items: z.array(z.object({ baleId: z.uuid(), barcode: z.string().regex(/^[A-Z0-9]{11}$/),
    dispatchCycle: z.string().regex(/^[0-9]+$/) })).min(1),
});
export const dispositionManifestSchema = z.object({ version: z.literal(1), items: z.array(z.object({
  baleId: z.uuid(), disposition: z.enum(['RELEASE', 'QUARANTINE']),
}).strict()).min(1) }).strict();
export type DispositionFacts = z.infer<typeof dispositionFactsSchema>;
export type DispositionManifest = z.infer<typeof dispositionManifestSchema>;
export interface DispositionSelection { observed: DispositionFacts; manifest: DispositionManifest }
export const closeReceiptSchema = commandReceiptSchema.extend({
  status: z.literal('RECORDED'), commandType: z.literal('CLOSE_DISPOSITION'),
  actor: z.object({ principalId: z.uuid() }), signedReceipt: z.string().min(1),
  originalContext: z.object({ submissionId: z.uuid() }), expected: z.record(z.string(), z.unknown()),
  businessDecision: z.object({ kind: z.literal('CLOSE_DISPOSITION'), status: z.literal('CANCELLED'),
    scopeClosed: z.literal(true), slotClosed: z.boolean(), manifestSha256: hash, manifestVersion: z.literal('1'),
    products: dispositionManifestSchema.shape.items }),
});

/** Only protocol fields; explicit key order matches the server's canonical JSON. Keep server UUID order. */
export async function dispositionHash(manifest: DispositionManifest): Promise<string> {
  const value = dispositionManifestSchema.parse(manifest);
  const canonical = JSON.stringify({ items: value.items.map(item => ({ baleId: item.baleId, disposition: item.disposition })), version: 1 });
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonical));
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
}
