import { z } from 'zod';

const positiveInt = z.number().int().positive().max(Number.MAX_SAFE_INTEGER);
const digest = z.string().regex(/^[a-f0-9]{64}$/);
export const downloadConfigSchema = z.object({
  channel: z.enum(['staging', 'production']), environmentName: z.string().trim().min(1).max(80),
  origin: z.string().url(), publicKeyBase64: z.string().min(1).max(2048),
  certificateSha256: digest, serverCompatibility: z.array(z.string().min(1).max(80)).min(1).max(20),
});
export const manifestSchema = z.object({ payload: z.string().min(1).max(48000), signature: z.string().min(1).max(256) });
export const releaseSchema = z.object({
  releaseId: z.string().regex(/^[A-Za-z0-9._-]{1,80}$/), channel: z.enum(['staging', 'production']),
  packageName: z.string(), versionCode: positiveInt, versionName: z.string().min(1).max(160),
  publishedAt: z.iso.datetime(), minAndroidApi: positiveInt.min(28).max(100),
  minSupportedVersionCode: positiveInt, maxSupportedVersionCode: positiveInt.nullable(),
  apkPath: z.string(), sizeBytes: positiveInt.max(64 * 1024 * 1024), sha256: digest,
  certificateSha256: digest, serverCompatibility: z.string().min(1).max(80),
  releaseNotes: z.string().max(8000), firstInstallAllowed: z.boolean().default(false),
});
export type DownloadConfig = z.infer<typeof downloadConfigSchema>;
export type Release = z.infer<typeof releaseSchema>;
export interface VerifiedRelease { config: DownloadConfig; release: Release; payload: string }
export type DownloadProblem = 'unconfigured' | 'unpublished' | 'paused' | 'incompatible' | 'invalid' | 'network';
export class DownloadError extends Error {
  constructor(public readonly kind: DownloadProblem) { super(kind); }
}
