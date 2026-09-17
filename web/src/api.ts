import { z } from 'zod';

const envelope = z.object({
  code: z.string(), message: z.string(), data: z.unknown(), requestId: z.string(),
  serverTime: z.string(), serverRecoveryEpoch: z.string(), serviceMode: z.string(),
});
const csrfSchema = z.object({ headerName: z.string(), token: z.string() });
let recoveryEpoch = '';

export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) { super(message); }
}

async function read(path: string, options: RequestInit): Promise<z.infer<typeof envelope>> {
  const response = await fetch(`/api/v1${path}`, { ...options, credentials: 'same-origin' });
  const body: unknown = await response.json();
  const parsed = envelope.parse(body);
  recoveryEpoch = parsed.serverRecoveryEpoch;
  if (!response.ok) throw new ApiError(response.status, parsed.code, `${parsed.message}（请求 ${parsed.requestId}）`);
  return parsed;
}

export async function request<T>(path: string, schema: z.ZodType<T>, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.method && options.method !== 'GET') {
    const expectedEpoch = recoveryEpoch;
    const csrf = csrfSchema.parse((await read('/auth/csrf', {})).data);
    headers.set(csrf.headerName, csrf.token);
    headers.set('X-Server-Recovery-Epoch', expectedEpoch || recoveryEpoch);
  }
  return schema.parse((await read(path, { ...options, headers })).data);
}

export function errorText(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof TypeError) return '连接失败，请检查网络后重试。原提交不会自动重复发送。';
  return '服务响应异常，请稍后重试。';
}
