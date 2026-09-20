import { downloadConfigSchema, manifestSchema, releaseSchema, DownloadError } from './contracts';
import type { DownloadConfig, Release, VerifiedRelease } from './contracts';
import { verifySignature } from './signature';
import { readJson } from './transport';

function validateEnvironment(config: DownloadConfig): void {
  if (location.protocol !== 'https:' || config.origin !== location.origin) throw new DownloadError('unconfigured');
}
function validateCompatibility(config: DownloadConfig, release: Release): void {
  const packageName = config.channel === 'staging' ? 'com.acrylic.outbound.pda.staging' : 'com.acrylic.outbound.pda';
  if (release.channel !== config.channel || release.packageName !== packageName || release.certificateSha256 !== config.certificateSha256)
    throw new DownloadError('invalid');
  if (release.apkPath !== `updates/${config.channel}/${release.releaseId}.apk`) throw new DownloadError('invalid');
  if (!release.firstInstallAllowed || !config.serverCompatibility.includes(release.serverCompatibility)) throw new DownloadError('incompatible');
  const androidVersion = /Android (\d+)/i.exec(navigator.userAgent)?.[1];
  const major = Number(androidVersion);
  const minimumApi = major >= 13 ? major + 20 : major + 19;
  if (androidVersion && minimumApi < release.minAndroidApi) throw new DownloadError('incompatible');
}
export async function readRelease(signal: AbortSignal): Promise<VerifiedRelease> {
  const rawConfig = await readJson('/pda-download-config.json', signal).catch((cause: unknown) => {
    if (cause instanceof DownloadError && cause.kind === 'unpublished') throw new DownloadError('unconfigured');
    throw cause;
  });
  const result = downloadConfigSchema.safeParse(rawConfig);
  if (!result.success) throw new DownloadError('unconfigured');
  const config = result.data; validateEnvironment(config);
  const raw = await readJson(`/updates/${config.channel}/manifest.json`, signal);
  try {
    const manifest = manifestSchema.parse(raw);
    const payload = await verifySignature(config.publicKeyBase64, manifest);
    const release = releaseSchema.parse(JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(payload)));
    validateCompatibility(config, release);
    return { config, release, payload: manifest.payload };
  } catch (cause) { throw cause instanceof DownloadError ? cause : new DownloadError('invalid'); }
}
export function sameRelease(left: VerifiedRelease, right: VerifiedRelease): boolean {
  return left.payload === right.payload && JSON.stringify(left.config) === JSON.stringify(right.config);
}
