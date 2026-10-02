import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {createHandler,ConflictError,BackupStore,StoredBackup,ApiEvent} from '../src/api';
const body = JSON.stringify({formatVersion:1,timeZone:'Asia/Shanghai',backupId:'one',datasetId:'local',
  sourceRevision:0,createdAt:'2026-10-02T00:00:00Z',calendars:[],scheduleChanges:[],checkIns:[]});
const event = (sub:string,routeKey:string,extra:Partial<ApiEvent>={}):ApiEvent=>({routeKey,
  requestContext:{authorizer:{jwt:{claims:{sub,token_use:'access'}}}},...extra});
class MemoryStore implements BackupStore {
  readonly rows = new Map<string,StoredBackup>();
  async read(key:string) {return this.rows.get(key) ?? null;}
  async write(key:string,body:string,expected:string|null) {
    const old = this.rows.get(key);
    if (expected ? old?.etag !== expected : old) throw new ConflictError();
    const saved = {body,etag:`"${createHash('md5').update(body).digest('hex')}"`,savedAt:'2026-10-02T00:00:00Z'};
    this.rows.set(key,saved); return saved;
  }
}
test('rejects anonymous requests and isolates account backups',async()=>{
  const handler=createHandler(new MemoryStore());
  assert.equal((await handler({routeKey:'GET /v1/backup'})).statusCode,401);
  assert.equal((await handler(event('account-a','PUT /v1/backup/snapshot',{body,headers:{'if-none-match':'*'}}))).statusCode,200);
  assert.equal((await handler(event('account-b','GET /v1/backup'))).statusCode,404);
  assert.equal((await handler(event('account-a','GET /v1/backup'))).statusCode,200);
});
test('repeated upload is idempotent and stale writes cannot overwrite latest backup',async()=>{
  const handler=createHandler(new MemoryStore());
  const put=event('account-a','PUT /v1/backup/snapshot',{body,headers:{'if-none-match':'*'}});
  const first=await handler(put);
  assert.equal(first.statusCode,200);
  assert.equal((await handler(put)).statusCode,200);
  const changed=body.replace('"one"','"two"');
  assert.equal((await handler({...put,body:changed})).statusCode,409);
  assert.equal((await handler({...put,body:changed,headers:{'if-match':first.headers.etag}})).statusCode,200);
  assert.equal((await handler({...put,headers:{'if-match':first.headers.etag}})).statusCode,409);
});
