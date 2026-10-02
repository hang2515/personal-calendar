import { validateSnapshot } from './snapshot';

export type StoredBackup = { body: string; etag: string; savedAt: string };
export interface BackupStore {
  read(key: string): Promise<StoredBackup | null>;
  write(key: string, body: string, expected: string | null): Promise<StoredBackup>;
}
export class ConflictError extends Error {}
export type ApiEvent = { routeKey: string; body?: string | null; isBase64Encoded?: boolean;
  headers?: Record<string, string | undefined>;
  requestContext?: { requestId?: string; authorizer?: { jwt?: {claims?: Record<string, unknown>} } } };
export function createHandler(store: BackupStore) {
  return async (event: ApiEvent) => {
    const respond = (statusCode: number, value: unknown, etag?: string) => ({statusCode,
      headers: {'content-type':'application/json; charset=utf-8', 'cache-control':'no-store', ...(etag ? {etag} : {})},
      body: JSON.stringify(value)});
    const sub = event.requestContext?.authorizer?.jwt?.claims?.sub;
    const tokenUse = event.requestContext?.authorizer?.jwt?.claims?.token_use;
    if (typeof sub !== 'string' || !/^[A-Za-z0-9_-]{1,128}$/.test(sub) || tokenUse !== 'access') {
      return respond(401, {code:'UNAUTHORIZED', message:'请重新登录'});
    }
    const key = `backups/${sub}/snapshot.json`;
    try {
      if (event.routeKey === 'PUT /v1/backup/snapshot') {
        const body = event.isBase64Encoded ? Buffer.from(event.body ?? '', 'base64').toString('utf8') : event.body ?? '';
        let snapshot;
        try { snapshot = validateSnapshot(body); }
        catch { return respond(400, {code:'INVALID_BACKUP', message:'备份格式、大小或数据无效'}); }
        const match = event.headers?.['if-match'];
        const create = event.headers?.['if-none-match'] === '*';
        if ((!match && !create) || (match && create) || (match && !/^"[a-fA-F0-9-]{1,128}"$/.test(match))) {
          return respond(428, {code:'PRECONDITION_REQUIRED', message:'请先确认云备份版本'});
        }
        let saved;
        try { saved = await store.write(key, body, match ?? null); }
        catch (error) {
          if (!(error instanceof ConflictError)) throw error;
          const current = await store.read(key);
          // 响应丢失后的完全相同重试视为成功，不能将新的数据当作旧请求重试。
          if (current?.body === body) saved = current;
          else return respond(409, {code:'BACKUP_CONFLICT', message:'云备份已有更新，请选择恢复或确认覆盖'});
        }
        return respond(200, {backupId:snapshot.backupId, savedAt:saved.savedAt, accountSub:sub}, saved.etag);
      }
      if (event.routeKey !== 'GET /v1/backup' && event.routeKey !== 'GET /v1/backup/snapshot') {
        return respond(404, {code:'NOT_FOUND', message:'接口不存在'});
      }
      const saved = await store.read(key);
      if (!saved) return respond(404, {code:'NO_BACKUP', message:'此账号尚无云备份'});
      const snapshot = validateSnapshot(saved.body);
      if (event.routeKey === 'GET /v1/backup/snapshot') return respond(200, snapshot, saved.etag);
      return respond(200, {backupId:snapshot.backupId, savedAt:saved.savedAt,
        calendarCount:snapshot.calendars.length, checkInCount:snapshot.checkIns.length, accountSub:sub}, saved.etag);
    } catch (error) {
      console.error(JSON.stringify({requestId:event.requestContext?.requestId,
        errorType:error instanceof Error ? error.name : 'UnknownError'}));
      return respond(500, {code:'BACKUP_UNAVAILABLE', message:'云备份暂时不可用，请稍后重试'});
    }
  };
}
