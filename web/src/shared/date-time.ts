const missingTime = '暂无记录';
// Require an explicit offset; never interpret an API timestamp in the browser's zone.
const timestamp = /^(\d{4}-\d{2}-\d{2})T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?(?:Z|[+-](?:(?:0\d|1[0-7]):[0-5]\d|18:00))$/;
const formatter = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Shanghai', calendar: 'gregory', numberingSystem: 'latn',
  year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
});

/** Display only. Keep the original timestamp for storage, signatures and comparisons. */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return missingTime;
  const match = timestamp.exec(value);
  if (!match) return missingTime;
  const date = new Date(value);
  const calendarDate = new Date(`${match[1]}T00:00:00Z`);
  if (!Number.isFinite(date.getTime()) || !Number.isFinite(calendarDate.getTime()) ||
      calendarDate.toISOString().slice(0, 10) !== match[1]) return missingTime;
  const parts = Object.fromEntries(formatter.formatToParts(date).map(part => [part.type, part.value]));
  return `${parts.year?.padStart(4, '0')}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`;
}
