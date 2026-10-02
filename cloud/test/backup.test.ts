import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateSnapshot } from '../src/snapshot';

const valid = () => ({
  formatVersion: 1, backupId: 'backup-1', datasetId: 'dataset-1', sourceRevision: 2,
  createdAt: '2026-10-02T00:00:00Z', timeZone: 'Asia/Shanghai',
  calendars: [{id: 'calendar-1', name: '阅读', startDate: '2026-10-01', weekdayMask: 127,
    isPaused: false, reminderMinute: 480, reminderEnabled: false, iconKey: 'book', colorKey: 'sage'}],
  scheduleChanges: [{calendarId: 'calendar-1', effectiveDate: '2026-10-01', weekdayMask: 127}],
  checkIns: [{calendarId: 'calendar-1', localDate: '2026-10-01'}],
});

test('valid backup preserves Chinese names and history', () => {
  assert.equal(validateSnapshot(JSON.stringify(valid())).calendars[0].name, '阅读');
});
test('rejects orphan and duplicate check-ins', () => {
  const orphan = valid(); orphan.checkIns[0].calendarId = 'missing';
  assert.throws(() => validateSnapshot(JSON.stringify(orphan)));
  const duplicate = valid(); duplicate.checkIns.push(duplicate.checkIns[0]);
  assert.throws(() => validateSnapshot(JSON.stringify(duplicate)));
});
test('rejects invalid dates, missing initial history and unknown versions', () => {
  const date = valid(); date.calendars[0].startDate = '2026-02-30';
  assert.throws(() => validateSnapshot(JSON.stringify(date)));
  const missing = valid(); missing.scheduleChanges = [];
  assert.throws(() => validateSnapshot(JSON.stringify(missing)));
  const future = valid(); future.formatVersion = 2;
  assert.throws(() => validateSnapshot(JSON.stringify(future)));
});
test('rejects wrong field types and oversized payloads', () => {
  const wrong = {...valid(), sourceRevision: '2'};
  assert.throws(() => validateSnapshot(JSON.stringify(wrong)));
  assert.throws(() => validateSnapshot(' '.repeat(1048577)));
});
