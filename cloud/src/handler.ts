import { S3Client, GetObjectCommand, PutObjectCommand, S3ServiceException } from '@aws-sdk/client-s3';
import { BackupStore, ConflictError, createHandler } from './api';

const client = new S3Client({maxAttempts: 3});
const bucket = process.env.BACKUP_BUCKET;
if (!bucket) throw new Error('缺少备份存储配置');
const store: BackupStore = {
  async read(key) {
    try {
      const output = await client.send(new GetObjectCommand({Bucket:bucket, Key:key}));
      if (!output.Body || !output.ETag || !output.LastModified) throw new Error('备份对象不完整');
      return {body:await output.Body.transformToString('utf-8'), etag:output.ETag, savedAt:output.LastModified.toISOString()};
    } catch (error) {
      if (error instanceof S3ServiceException && error.$metadata.httpStatusCode === 404) return null;
      throw error;
    }
  },
  async write(key, body, expected) {
    try {
      const saved = await client.send(new PutObjectCommand({Bucket:bucket, Key:key, Body:body,
        ContentType:'application/json', ServerSideEncryption:'AES256',
        ...(expected ? {IfMatch:expected} : {IfNoneMatch:'*'})}));
      if (!saved.ETag) throw new Error('备份结果不完整');
      return {body, etag:saved.ETag, savedAt:new Date().toISOString()};
    } catch (error) {
      if (error instanceof S3ServiceException && [409,412].includes(error.$metadata.httpStatusCode ?? 0)) throw new ConflictError();
      throw error;
    }
  },
};
export const handler = createHandler(store);
