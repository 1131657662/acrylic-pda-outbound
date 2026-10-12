import { z } from 'zod';
import { connection, connectionResponse } from './connection';

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
  let response: Response;
  try { response = await fetch(`/api/v1${path}`, { ...options, credentials: 'same-origin' }); }
  catch (cause) { connection.state = 'unreachable'; throw cause; }
  if (!response.ok) connectionResponse(response.status, '');
  const body: unknown = await response.json();
  const parsed = envelope.parse(body);
  if (response.ok && path === '/me') connection.state = 'ok';
  connectionResponse(response.status, parsed.serviceMode);
  recoveryEpoch = parsed.serverRecoveryEpoch;
  if (!response.ok) throw new ApiError(response.status, parsed.code, `${parsed.message}（请求 ${parsed.requestId}）`);
  return parsed;
}

export async function request<T>(path: string, schema: z.ZodType<T>, options: RequestInit = {}, assertCurrent?: () => void): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.method && options.method !== 'GET') {
    if (connection.state === 'expired' && !path.startsWith('/auth/')) throw new ApiError(401, 'UNAUTHENTICATED', '请重新登录后核对原操作结果。');
    const expectedEpoch = headers.get('X-Server-Recovery-Epoch') ?? recoveryEpoch;
    const csrf = csrfSchema.parse((await read('/auth/csrf', {})).data);
    headers.set(csrf.headerName, csrf.token);
    headers.set('X-Server-Recovery-Epoch', expectedEpoch || recoveryEpoch);
  }
  assertCurrent?.();
  return schema.parse((await read(path, { ...options, headers })).data);
}

export function currentRecoveryEpoch(): string {
  if (!recoveryEpoch) throw new Error('服务器恢复代次尚未加载');
  return recoveryEpoch;
}

/** Correction readers return original JSON directly; error envelopes still carry connection state. */
export async function getRaw<T>(path: string, schema: z.ZodType<T>): Promise<T> {
  let response: Response;
  try { response = await fetch(`/api/v1${path}`, { credentials: 'same-origin', cache: 'no-store' }); }
  catch (cause) { connection.state = 'unreachable'; throw cause; }
  const payload: unknown = await response.json();
  if (!response.ok) {
    const parsed = envelope.safeParse(payload);
    if (!parsed.success) throw new ApiError(response.status, 'COMMAND_SURFACE_CLOSED', '该功能尚未开放。');
    connectionResponse(response.status, parsed.data.serviceMode);
    throw new ApiError(response.status, parsed.data.code, `${parsed.data.message}（请求 ${parsed.data.requestId}）`);
  }
  connectionResponse(response.status, '');
  return schema.parse(payload);
}

export interface RawJsonCommand { json: string; recoveryEpoch: string; assertCurrent: () => void }

/** Send saved JSON verbatim, preserving the epoch and rechecking ownership after the asynchronous CSRF read. */
export async function postRaw<T>(path: string, body: RawJsonCommand, schema: z.ZodType<T>): Promise<T> {
  if (connection.state === 'expired' && !path.startsWith('/auth/')) throw new ApiError(401, 'UNAUTHENTICATED', '请重新登录后核对原操作结果。');
  const headers = new Headers({ 'Content-Type': 'application/json' });
  const csrf = csrfSchema.parse((await read('/auth/csrf', {})).data);
  headers.set(csrf.headerName, csrf.token);
  headers.set('X-Server-Recovery-Epoch', body.recoveryEpoch);
  body.assertCurrent();
  let response: Response;
  try { response = await fetch(`/api/v1${path}`, { method: 'POST', body: body.json, headers, credentials: 'same-origin' }); }
  catch (cause) { connection.state = 'unreachable'; throw cause; }
  const payload: unknown = await response.json();
  if (!response.ok) {
    if (response.status === 404) throw new ApiError(404, 'COMMAND_SURFACE_CLOSED', '该功能尚未开放。');
    const parsed = envelope.parse(payload);
    connectionResponse(response.status, parsed.serviceMode);
    throw new ApiError(response.status, parsed.code, `${parsed.message}（请求 ${parsed.requestId}）`);
  }
  connectionResponse(response.status, '');
  return schema.parse(payload);
}

export function errorText(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof TypeError) return '连接失败，请检查网络后重试。原提交不会自动重复发送。';
  return '服务响应异常，请稍后重试。';
}
