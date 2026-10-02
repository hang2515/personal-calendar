export const MAX_BYTES = 1024 * 1024;
type Calendar = { id: string; name: string; startDate: string; weekdayMask: number;
  isPaused: boolean; reminderMinute: number; reminderEnabled: boolean; iconKey: string; colorKey: string };
export type Snapshot = { formatVersion: number; backupId: string; datasetId: string; sourceRevision: number;
  createdAt: string; timeZone: string; calendars: Calendar[];
  scheduleChanges: {calendarId: string; effectiveDate: string; weekdayMask: number}[];
  checkIns: {calendarId: string; localDate: string}[] };

function requireValid(valid: unknown): asserts valid { if (!valid) throw new Error('备份格式或数据无效'); }
function record(value: unknown): Record<string, unknown> {
  requireValid(value !== null && typeof value === 'object' && !Array.isArray(value));
  return value as Record<string, unknown>;
}
function id(value: unknown): asserts value is string {
  requireValid(typeof value === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(value));
}
function date(value: unknown): asserts value is string {
  requireValid(typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value));
  const parsed = new Date(value + 'T00:00:00Z');
  requireValid(Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value);
}
function integer(value: unknown, min: number, max: number): asserts value is number {
  requireValid(typeof value === 'number' && Number.isSafeInteger(value) && value >= min && value <= max);
}
export function validateSnapshot(body: string): Snapshot {
  requireValid(Buffer.byteLength(body, 'utf8') <= MAX_BYTES);
  const root = record(JSON.parse(body));
  requireValid(root.formatVersion === 1 && root.timeZone === 'Asia/Shanghai');
  id(root.backupId); id(root.datasetId); integer(root.sourceRevision, 0, Number.MAX_SAFE_INTEGER);
  requireValid(typeof root.createdAt === 'string' && /^\d{4}-\d{2}-\d{2}T.*Z$/.test(root.createdAt) && Number.isFinite(Date.parse(root.createdAt)));
  const calendars = root.calendars, changes = root.scheduleChanges, checks = root.checkIns;
  requireValid(Array.isArray(calendars) && Array.isArray(changes) && Array.isArray(checks));
  requireValid(calendars.length <= 1000 && changes.length <= 10000 && checks.length <= 10000);
  const starts = new Map<string, string>();
  for (const item of calendars) {
    const c = record(item); id(c.id); date(c.startDate);
    requireValid(!starts.has(c.id)); starts.set(c.id, c.startDate);
    requireValid(typeof c.name === 'string' && c.name.trim() === c.name && c.name.length >= 1 && c.name.length <= 40);
    integer(c.weekdayMask, 1, 127); integer(c.reminderMinute, 0, 1439);
    requireValid(typeof c.isPaused === 'boolean' && typeof c.reminderEnabled === 'boolean');
    requireValid(['leaf','fitness','book','heart'].includes(String(c.iconKey)));
    requireValid(['sage','mint','blue','lavender','coral','sand'].includes(String(c.colorKey)));
  }
  const ruleKeys = new Set<string>();
  for (const item of changes) {
    const c = record(item); id(c.calendarId); date(c.effectiveDate); integer(c.weekdayMask, 0, 127);
    const start = starts.get(c.calendarId), key = `${c.calendarId}/${c.effectiveDate}`;
    requireValid(start && c.effectiveDate >= start && !ruleKeys.has(key)); ruleKeys.add(key);
  }
  for (const [calendarId, start] of starts) requireValid(ruleKeys.has(`${calendarId}/${start}`));
  const checkKeys = new Set<string>();
  for (const item of checks) {
    const c = record(item); id(c.calendarId); date(c.localDate);
    const start = starts.get(c.calendarId), key = `${c.calendarId}/${c.localDate}`;
    requireValid(start && c.localDate >= start && !checkKeys.has(key)); checkKeys.add(key);
  }
  return root as unknown as Snapshot;
}
