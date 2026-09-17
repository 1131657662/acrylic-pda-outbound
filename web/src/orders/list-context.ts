import type { LocationQuery, LocationQueryRaw } from 'vue-router';
import { orderStatusLabels } from './contracts';

export interface OrderQuery { search: string; status: string; page: number; pageSize: 20 | 50 | 100 }
export function parseOrderQuery(query: LocationQuery): OrderQuery {
  const page = Number(query.page); const size = Number(query.pageSize);
  return { search: typeof query.search === 'string' ? query.search.slice(0, 40) : '',
    status: typeof query.status === 'string' && Object.hasOwn(orderStatusLabels, query.status) ? query.status : '',
    page: Number.isInteger(page) && page > 0 && page <= 1000000 ? page : 1, pageSize: size === 50 || size === 100 ? size : 20 };
}
export function orderRouteQuery(query: OrderQuery, warehouse: string): LocationQueryRaw {
  return { warehouse, search: query.search || undefined, status: query.status || undefined, page: String(query.page), pageSize: String(query.pageSize) };
}
export interface ListPosition { table: number; page: number; focus: string }
const positions = new Map<string, ListPosition>();
export function contextKey(user: string, warehouse: string, query: OrderQuery): string { return JSON.stringify([user, warehouse, query]); }
export function saveListPosition(key: string, position: ListPosition): void {
  if (positions.size >= 30) positions.clear();
  positions.set(key, position);
}
export function listPosition(key: string): ListPosition | undefined { return positions.get(key); }
export function clearListPositions(): void { positions.clear(); }
