import { reactive } from 'vue';

export const connection = reactive({ state: 'unknown' as 'unknown' | 'ok' | 'unreachable' | 'expired', lastSuccess: '', serviceMode: '' });
export function connectionResponse(status: number, mode: string): void {
  connection.serviceMode = mode;
  if (status === 401) connection.state = 'expired';
  else if (status >= 500) connection.state = 'unreachable';
  else if (connection.state !== 'expired') { connection.state = 'ok'; connection.lastSuccess = new Date().toISOString(); }
}
