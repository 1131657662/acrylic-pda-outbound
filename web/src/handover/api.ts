import { z } from 'zod';
import { ApiError } from '../api';
import { connection, connectionResponse } from '../connection';
import { casePageSchema, caseContextSchema } from './contracts';
import type { CasePage, CaseContext } from './contracts';

const failure = z.object({ code: z.string(), message: z.string(), serviceMode: z.string().optional() });
async function get<T>(path: string, schema: z.ZodType<T>, signal: AbortSignal): Promise<T> {
  let response: Response;
  try { response = await fetch(`/api/v1/handovers/${path}`, { credentials: 'same-origin', cache: 'no-store', signal }); }
  catch (cause) { if (!signal.aborted) connection.state = 'unreachable'; throw cause; }
  if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
  connectionResponse(response.status, '');
  if (!response.ok) {
    if (response.status === 404) throw new ApiError(404, 'HANDOVER_UNAVAILABLE', '交接查询未开放，或当前仓库中没有可访问的记录。');
    const body = failure.safeParse(await response.json());
    if (!body.success) throw new ApiError(response.status, 'HANDOVER_READ_FAILED', '暂时无法读取交接记录，请稍后重试。');
    connectionResponse(response.status, body.data.serviceMode ?? '');
    throw new ApiError(response.status, body.data.code, body.data.message);
  }
  const body: unknown = await response.json();
  return schema.parse(body);
}

export async function casePage(warehouse: string, order: string, cursor: string, signal: AbortSignal): Promise<CasePage> {
  const query = new URLSearchParams({ warehouseId: warehouse, orderId: order });
  if (cursor) query.set('cursor', cursor);
  const result = await get(`cases?${query}`, casePageSchema, signal);
  if (result.warehouseId !== warehouse || result.orderId !== order) throw new Error('交接查询对象不一致');
  return result;
}

export async function caseContext(warehouse: string, order: string, id: string, signal: AbortSignal): Promise<CaseContext> {
  const query = new URLSearchParams({ warehouseId: warehouse, commandType: 'APPROVE' });
  const result = await get(`${encodeURIComponent(id)}/context?${query}`, caseContextSchema, signal);
  const c = result.context; const original = result.originalCommand;
  if (c.warehouseId !== warehouse || c.canonicalOrderId !== order || c.caseId !== id
      || original.warehouseId !== warehouse || original.scopeId !== c.scopeId) throw new Error('交接详情对象不一致');
  return result;
}
